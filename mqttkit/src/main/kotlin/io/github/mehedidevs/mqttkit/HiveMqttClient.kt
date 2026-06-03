package io.github.mehedidevs.mqttkit

import com.hivemq.client.mqtt.MqttClientSslConfig
import com.hivemq.client.mqtt.datatypes.MqttQos
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient
import com.hivemq.client.mqtt.mqtt5.Mqtt5Client
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicBoolean

/**
 * MQTT 5 implementation backed by the HiveMQ client library.
 *
 * ═══════════════════════════════════════════════════════════════════
 *  ARCHITECTURE DECISIONS (production lessons learned)
 * ═══════════════════════════════════════════════════════════════════
 *
 * 1. RE-SUBSCRIBE ON RECONNECT
 *    HiveMQ's built-in `resubscribeIfSessionPresent` only works when the
 *    broker still has your session (cleanStart=false AND session hasn't
 *    expired). It's unreliable across network changes and broker restarts.
 *    Solution: We keep our own [activeSubscriptions] map and re-apply every
 *    subscription in the `connectedListener` — giving us deterministic
 *    behaviour regardless of broker session state.
 *
 * 2. SINGLE FLOW PER TOPIC (fan-out via callbacks)
 *    A naive implementation creates one broker subscription per Flow collector.
 *    If three screens subscribe to the same topic, you pay three times.
 *    Solution: [topicSubscribers] maps topic → Set<callback>. Only the *first*
 *    subscriber actually talks to the broker; subsequent ones just register
 *    their callback. The broker subscription is removed only when the last
 *    callback unregisters.
 *
 * 3. MUTEX-GUARDED CONNECT/DISCONNECT
 *    Without a lock, calling connect() from two coroutines simultaneously
 *    creates two client instances. [mutex] prevents this.
 *
 * 4. LAST WILL TESTAMENT (LWT)
 *    Configured at connect time via [MqttConfig.willMessage]. The broker
 *    publishes the will automatically if the client disconnects ungracefully.
 *    This lets subscribers know the device went offline without polling.
 */
class HiveMqttClient(
    private val config: MqttConfig
) : MqttClient {

    // ── Internal coroutine scope (IO dispatcher, outlives any single ViewModel) ──
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    // ── Connection state exposed as a StateFlow ──────────────────────────────────
    private val _state = MutableStateFlow<MqttConnectionState>(MqttConnectionState.Idle)
    override val connectionState: StateFlow<MqttConnectionState> = _state.asStateFlow()

    /**
     * topic → QoS: every topic that *should* be subscribed.
     * Persists across reconnects so we can re-apply them.
     */
    private val activeSubscriptions = ConcurrentHashMap<String, Int>()

    /**
     * topic → Set of message callbacks.
     * Multiple Flow collectors of the same topic share one broker subscription.
     */
    private val topicSubscribers =
        ConcurrentHashMap<String, CopyOnWriteArraySet<(MqttMessage) -> Unit>>()

    private var client: Mqtt5AsyncClient? = null
    private var activeEndpoint: MqttBrokerEndpoint = config.primaryEndpoint
    private val manualDisconnect = AtomicBoolean(false)

    // ════════════════════════════════════════════════════════════════════════════
    //  connect()
    // ════════════════════════════════════════════════════════════════════════════

    override suspend fun connect() = mutex.withLock {
        if (_state.value is MqttConnectionState.Connected ||
            _state.value is MqttConnectionState.Connecting
        ) {
            Timber.d("MQTT connect() skipped — already ${_state.value}")
            return
        }
        _state.value = MqttConnectionState.Connecting
        manualDisconnect.set(false)

        val failures = mutableListOf<String>()

        config.endpoints.forEachIndexed { index, endpoint ->
            try {
                val c = buildClient(endpoint)
                client = c
                activeEndpoint = endpoint
                sendConnectPacket(c)
                Timber.i("MQTT handshake complete (${endpoint.host}:${endpoint.port}, tls=${endpoint.useTls})")
                return
            } catch (t: Throwable) {
                failures += "${endpoint.host}:${endpoint.port} tls=${endpoint.useTls}: ${t.message}"
                Timber.w(t, "MQTT connect attempt failed for ${endpoint.host}:${endpoint.port}")
                runCatching { client?.disconnect()?.await() }
                client = null

                if (index == config.endpoints.lastIndex) {
                    val error = IllegalStateException(
                        "MQTT connect failed for all configured endpoints: ${failures.joinToString(" | ")}",
                        t
                    )
                    Timber.e(error, "MQTT connect() failed")
                    _state.value = MqttConnectionState.Failed(error)
                    throw error
                }
            }
        }
    }

    private fun buildClient(endpoint: MqttBrokerEndpoint): Mqtt5AsyncClient {
        val builder = Mqtt5Client.builder()
                .identifier(config.clientId)
                .serverHost(endpoint.host)
                .serverPort(endpoint.port)

                // ── CONNECTED listener ───────────────────────────────────────
                // Called both on initial connect AND after every automatic reconnect.
                // KEY FIX: re-subscribe all tracked topics here so subscriptions
                // survive network drops, server restarts, and session expiry.
                .addConnectedListener {
                    Timber.i("MQTT connected to ${activeEndpoint.host}:${activeEndpoint.port}")
                    _state.value = MqttConnectionState.Connected
                    if (activeSubscriptions.isNotEmpty()) {
                        scope.launch {
                            activeSubscriptions.forEach { (topic, qos) ->
                                runCatching { applySubscribe(topic, qos) }
                                    .onFailure { Timber.w(it, "Re-subscribe failed: $topic") }
                            }
                            Timber.d("Re-subscribed ${activeSubscriptions.size} topic(s) after (re)connect")
                        }
                    }
                }

                // ── DISCONNECTED listener ────────────────────────────────────
                // Fires on unexpected disconnect. We use HiveMQ's reconnector
                // with exponential back-off.
                .addDisconnectedListener { ctx ->
                    if (manualDisconnect.get()) {
                        _state.value = MqttConnectionState.Disconnected("manual")
                        return@addDisconnectedListener
                    }

                    val attempt = ctx.reconnector.attempts
                    Timber.w("MQTT disconnected (cause=${ctx.cause?.message}, attempt=$attempt)")

                    _state.value = if (config.automaticReconnect) {
                        val delayMs = minOf(
                            config.initialReconnectDelayMs * (1L shl attempt.coerceAtMost(5)),
                            config.maxReconnectDelayMs
                        )
                        ctx.reconnector
                            .reconnect(true)
                            .delay(delayMs, java.util.concurrent.TimeUnit.MILLISECONDS)
                        MqttConnectionState.Reconnecting(attempt + 1)
                    } else {
                        MqttConnectionState.Disconnected(ctx.cause?.message)
                    }
                }

        if (endpoint.useTls) {
            builder.sslConfig(MqttClientSslConfig.builder().build())
        }

        return builder.buildAsync()
    }

    private suspend fun sendConnectPacket(c: Mqtt5AsyncClient) {
        // ── Build CONNECT packet ─────────────────────────────────────────
        val connectBuilder = c.connectWith()
            .cleanStart(config.cleanStart)
            .sessionExpiryInterval(config.sessionExpiryIntervalSec)
            .keepAlive(config.keepAliveSec)

        when (val auth = config.auth) {
            MqttAuth.None -> Unit
            is MqttAuth.Basic -> {
                connectBuilder.simpleAuth()
                    .username(auth.username)
                    .password(auth.password.toByteArray(Charsets.UTF_8))
                    .applySimpleAuth()
            }
            is MqttAuth.Token -> {
                connectBuilder.simpleAuth()
                    .username(auth.username)
                    .password(auth.token.toByteArray(Charsets.UTF_8))
                    .applySimpleAuth()
            }
        }

        // ── Last Will & Testament (LWT) ──────────────────────────────────
        // If the client disconnects without sending DISCONNECT (e.g. crash,
        // network drop, battery pull), the broker publishes this message.
        config.willMessage?.let { will ->
            connectBuilder.willPublish()
                .topic(will.topic)
                .payload(will.payload.toByteArray())
                .qos(qosOf(will.qos))
                .retain(will.retained)
                .applyWillPublish()
            Timber.d("LWT configured: topic=${will.topic}")
        }

        connectBuilder.send().await()
    }

    // ════════════════════════════════════════════════════════════════════════════
    //  disconnect()
    // ════════════════════════════════════════════════════════════════════════════

    override suspend fun disconnect() {
        mutex.withLock {
            try {
                manualDisconnect.set(true)
                client?.disconnect()?.await()
            } catch (t: Throwable) {
                Timber.w(t, "MQTT disconnect error (ignored)")
            } finally {
                client = null
                activeSubscriptions.clear()
                topicSubscribers.clear()
                _state.value = MqttConnectionState.Disconnected("manual")
                Timber.i("MQTT disconnected (manual)")
                scope.cancel()
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════════════
    //  publish()
    // ════════════════════════════════════════════════════════════════════════════

    override suspend fun publish(message: MqttMessage): Result<Unit> = runCatching {
        require(message.qos in 0..2) { "MQTT qos must be 0, 1, or 2." }
        val c = client ?: error("MQTT not connected — call connect() first")
        c.publishWith()
            .topic(message.topic)
            .qos(qosOf(message.qos))
            .payload(message.payload)
            .retain(message.retained)
            .send()
            .await()
        Timber.d("MQTT → published topic=${message.topic} qos=${message.qos} retained=${message.retained}")
        Unit
    }.onFailure { Timber.w(it, "MQTT publish failed: ${message.topic}") }

    // ════════════════════════════════════════════════════════════════════════════
    //  subscribe()
    // ════════════════════════════════════════════════════════════════════════════

    /**
     * Returns a cold [Flow] that:
     *  1. Registers a callback in [topicSubscribers].
     *  2. If this is the *first* subscriber, tells the broker to subscribe.
     *  3. On cancellation, removes the callback; if last subscriber, unsubscribes.
     *
     * Wildcards are fully supported: `sensors/+/temp`, `home/#`, etc.
     */
    override fun subscribe(topic: String, qos: Int): Flow<MqttMessage> = callbackFlow {
        require(topic.isNotBlank()) { "MQTT topic cannot be blank." }
        require(qos in 0..2) { "MQTT qos must be 0, 1, or 2." }
        val listener: (MqttMessage) -> Unit = { trySend(it) }

        val subscribers = topicSubscribers.getOrPut(topic) { CopyOnWriteArraySet() }
        val isFirstSubscriber = subscribers.isEmpty()
        subscribers.add(listener)

        if (isFirstSubscriber) {
            activeSubscriptions[topic] = qos
            if (_state.value is MqttConnectionState.Connected) {
                runCatching { applySubscribe(topic, qos) }
                    .onFailure { Timber.w(it, "Subscribe failed; will retry on reconnect") }
            }
        }

        Timber.d("MQTT ← subscribed topic=$topic qos=$qos (totalListeners=${subscribers.size})")

        awaitClose {
            subscribers.remove(listener)
            if (subscribers.isEmpty()) {
                topicSubscribers.remove(topic)
                activeSubscriptions.remove(topic)
                scope.launch {
                    runCatching {
                        client?.unsubscribeWith()?.topicFilter(topic)?.send()?.await()
                        Timber.d("MQTT ← unsubscribed topic=$topic (no more listeners)")
                    }
                }
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════════════
    //  Internal helpers
    // ════════════════════════════════════════════════════════════════════════════

    /** Actually send the MQTT SUBSCRIBE packet to the broker. */
    private suspend fun applySubscribe(topic: String, qos: Int) {
        val c = client ?: return
        c.subscribeWith()
            .topicFilter(topic)
            .qos(qosOf(qos))
            .callback { publish: Mqtt5Publish ->
                val msg = MqttMessage(
                    topic    = publish.topic.toString(),
                    payload  = publish.payloadAsBytes,
                    qos      = publish.qos.code,
                    retained = publish.isRetain
                )
                // Fan-out: deliver to all registered callbacks for this topic
                topicSubscribers[topic]?.forEach { cb ->
                    runCatching { cb(msg) }
                        .onFailure { Timber.e(it, "MQTT listener threw for topic=$topic") }
                }
            }
            .send()
            .await()
    }

    /** Map integer QoS value → HiveMQ enum. Defaults to AT_LEAST_ONCE for unknown values. */
    private fun qosOf(value: Int): MqttQos = when (value) {
        0    -> MqttQos.AT_MOST_ONCE   // fire and forget
        2    -> MqttQos.EXACTLY_ONCE   // two-phase commit
        else -> MqttQos.AT_LEAST_ONCE  // acknowledged delivery (default)
    }
}
