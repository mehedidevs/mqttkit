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
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.future.await
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
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
 *    Solution: We keep our own [topics] map and re-apply every subscription
 *    in the `connectedListener` — giving us deterministic behaviour
 *    regardless of broker session state.
 *
 * 2. SINGLE FLOW PER TOPIC (fan-out via callbacks)
 *    A naive implementation creates one broker subscription per Flow collector.
 *    If three screens subscribe to the same topic, you pay three times.
 *    Solution: [topics] maps topic → subscribers. Only the *first* subscriber
 *    actually talks to the broker; subsequent ones just register. The broker
 *    subscription is removed only when the last subscriber unregisters.
 *    All map mutations go through ConcurrentHashMap.compute so register/
 *    unregister can't race, and broker SUBSCRIBE/UNSUBSCRIBE packets are
 *    serialized through [brokerOps] so they can't be reordered.
 *
 * 3. MUTEX-GUARDED CONNECT/DISCONNECT
 *    Without a lock, calling connect() from two coroutines simultaneously
 *    creates two client instances. [mutex] prevents this.
 *
 * 4. LAST WILL TESTAMENT (LWT)
 *    Configured at connect time via [MqttConfig.willMessage]. The broker
 *    publishes the will automatically if the client disconnects ungracefully.
 *    This lets subscribers know the device went offline without polling.
 *
 * 5. AUTO-RECONNECT ONLY AFTER A SUCCESSFUL CONNECT
 *    HiveMQ fires the disconnected listener for *failed initial connect
 *    attempts* too. If we enabled the reconnector there, a dead primary
 *    endpoint would keep retrying in the background while [connect] moves on
 *    to a fallback endpoint — leaving two live clients. Each built client
 *    carries an `everConnected` flag and only auto-reconnects once it has
 *    been connected at least once; initial connect failures are handled
 *    solely by the endpoint loop in [connect].
 *
 * 6. SHARED SUBSCRIPTIONS USE THE HIGHEST REQUESTED QoS
 *    When collectors of the same topic ask for different QoS levels, the
 *    broker subscription is (re-)established at the maximum. MQTT treats a
 *    repeated SUBSCRIBE for the same filter as a replacement, so upgrading
 *    is safe and message delivery is uninterrupted.
 *
 * 7. FLOWS COMPLETE ON DISCONNECT
 *    [disconnect] closes every open subscribe flow so collectors terminate
 *    instead of waiting forever on a client that will never emit again.
 */
class HiveMqttClient(
    private val config: MqttConfig,
    private val logger: MqttLogger = MqttLogger.None
) : MqttClient {

    /** One registered collector of a topic flow. */
    private class TopicSubscriber(
        /** Pushes a message into the collector's flow. */
        val deliver: (MqttMessage) -> Unit,
        /** Completes the collector's flow (used by [disconnect]). */
        val close: () -> Unit
    )

    /** Immutable per-topic state; replaced atomically via [ConcurrentHashMap.compute]. */
    private class TopicState(
        /** QoS the broker subscription is (or should be) established at — max of all requests. */
        val qos: Int,
        val subscribers: List<TopicSubscriber>
    )

    // ── Internal coroutine scope (IO dispatcher, outlives any single ViewModel).
    //    Recreated on connect() so the client is reusable after disconnect(). ──
    private var scope = newScope()
    private val mutex = Mutex()

    /** Serializes broker SUBSCRIBE/UNSUBSCRIBE packets so they can't be reordered. */
    private val brokerOps = Mutex()

    // ── Connection state exposed as a StateFlow ──────────────────────────────────
    private val _state = MutableStateFlow<MqttConnectionState>(MqttConnectionState.Idle)
    override val connectionState: StateFlow<MqttConnectionState> = _state.asStateFlow()

    /**
     * topic → subscribers + effective QoS: every topic that *should* be
     * subscribed. Persists across reconnects so we can re-apply them.
     */
    private val topics = ConcurrentHashMap<String, TopicState>()

    private var client: Mqtt5AsyncClient? = null
    private var activeEndpoint: MqttBrokerEndpoint = config.primaryEndpoint
    private val manualDisconnect = AtomicBoolean(false)

    private fun newScope() = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ════════════════════════════════════════════════════════════════════════════
    //  connect()
    // ════════════════════════════════════════════════════════════════════════════

    override suspend fun connect() = mutex.withLock {
        if (_state.value is MqttConnectionState.Connected ||
            _state.value is MqttConnectionState.Connecting
        ) {
            logger.debug("MQTT connect() skipped — already ${_state.value}")
            return
        }
        _state.value = MqttConnectionState.Connecting
        manualDisconnect.set(false)

        // disconnect() cancels the scope; a fresh connect() needs a live one so
        // re-subscribe and unsubscribe launches don't silently no-op.
        if (!scope.isActive) scope = newScope()

        val failures = mutableListOf<String>()

        config.endpoints.forEachIndexed { index, endpoint ->
            try {
                val c = buildClient(endpoint)
                client = c
                activeEndpoint = endpoint
                sendConnectPacket(c)
                logger.info("MQTT handshake complete (${endpoint.host}:${endpoint.port}, tls=${endpoint.useTls})")
                return
            } catch (t: Throwable) {
                failures += "${endpoint.host}:${endpoint.port} tls=${endpoint.useTls}: ${t.message}"
                logger.warn("MQTT connect attempt failed for ${endpoint.host}:${endpoint.port}", t)
                runCatching { client?.disconnect()?.await() }
                client = null

                if (index == config.endpoints.lastIndex) {
                    val error = IllegalStateException(
                        "MQTT connect failed for all configured endpoints: ${failures.joinToString(" | ")}",
                        t
                    )
                    logger.error("MQTT connect() failed", error)
                    _state.value = MqttConnectionState.Failed(error)
                    throw error
                }
            }
        }
    }

    private fun buildClient(endpoint: MqttBrokerEndpoint): Mqtt5AsyncClient {
        // Set once this specific client instance completes a handshake. Gates the
        // reconnector so failed *initial* attempts never spawn background retries
        // (see architecture decision 5).
        val everConnected = AtomicBoolean(false)

        val builder = Mqtt5Client.builder()
                .identifier(config.clientId)
                .serverHost(endpoint.host)
                .serverPort(endpoint.port)

                // ── CONNECTED listener ───────────────────────────────────────
                // Called both on initial connect AND after every automatic reconnect.
                // KEY FIX: re-subscribe all tracked topics here so subscriptions
                // survive network drops, server restarts, and session expiry.
                .addConnectedListener {
                    everConnected.set(true)
                    logger.info("MQTT connected to ${endpoint.host}:${endpoint.port}")
                    _state.value = MqttConnectionState.Connected
                    if (topics.isNotEmpty()) {
                        scope.launch {
                            brokerOps.withLock {
                                topics.forEach { (topic, state) ->
                                    runCatching { applySubscribe(topic, state.qos) }
                                        .onFailure { logger.warn("Re-subscribe failed: $topic", it) }
                                }
                            }
                            logger.debug("Re-subscribed ${topics.size} topic(s) after (re)connect")
                        }
                    }
                }

                // ── DISCONNECTED listener ────────────────────────────────────
                // Fires on unexpected disconnect AND on failed initial connect
                // attempts. We use HiveMQ's reconnector with exponential back-off,
                // but only after this client has connected successfully once.
                .addDisconnectedListener { ctx ->
                    if (manualDisconnect.get()) {
                        _state.value = MqttConnectionState.Disconnected("manual")
                        return@addDisconnectedListener
                    }

                    if (!everConnected.get()) {
                        // Initial connect attempt failed: let connect()'s endpoint
                        // loop decide what happens next; no background reconnect.
                        logger.debug(
                            "MQTT initial connect attempt failed for " +
                                "${endpoint.host}:${endpoint.port} (cause=${ctx.cause.message})"
                        )
                        return@addDisconnectedListener
                    }

                    val attempt = ctx.reconnector.attempts
                    logger.warn("MQTT disconnected (cause=${ctx.cause.message}, attempt=$attempt)")

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
                        MqttConnectionState.Disconnected(ctx.cause.message)
                    }
                }

        if (endpoint.useTls) {
            builder.sslConfig(buildSslConfig())
        }

        endpoint.webSocket?.let { ws ->
            builder.webSocketConfig()
                .serverPath(ws.serverPath)
                .subprotocol(ws.subprotocol)
                .applyWebSocketConfig()
            logger.debug("MQTT WebSocket transport enabled (path=${ws.serverPath})")
        }

        return builder.buildAsync()
    }

    /** Translate [MqttConfig.tlsConfig] (plain javax.net.ssl types) into HiveMQ's ssl config. */
    private fun buildSslConfig(): MqttClientSslConfig {
        val tls = config.tlsConfig ?: return MqttClientSslConfig.builder().build()
        val builder = MqttClientSslConfig.builder()
        tls.keyManagerFactory?.let { builder.keyManagerFactory(it) }
        tls.trustManagerFactory?.let { builder.trustManagerFactory(it) }
        tls.protocols?.let { builder.protocols(it) }
        tls.cipherSuites?.let { builder.cipherSuites(it) }
        return builder.build()
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
            logger.debug("LWT configured: topic=${will.topic}")
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
                logger.warn("MQTT disconnect error (ignored)", t)
            } finally {
                client = null
                // Complete all open subscribe flows so collectors terminate
                // instead of hanging on a dead client (decision 7). Clear the
                // map first so awaitClose cleanup sees no active topics.
                val openSubscribers = topics.values.flatMap { it.subscribers }
                topics.clear()
                openSubscribers.forEach { runCatching { it.close() } }
                _state.value = MqttConnectionState.Disconnected("manual")
                logger.info("MQTT disconnected (manual)")
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
        logger.debug("MQTT → published topic=${message.topic} qos=${message.qos} retained=${message.retained}")
        Unit
    }.onFailure { logger.warn("MQTT publish failed: ${message.topic}", it) }

    // ════════════════════════════════════════════════════════════════════════════
    //  subscribe()
    // ════════════════════════════════════════════════════════════════════════════

    /**
     * Returns a cold [Flow] that:
     *  1. Atomically registers a subscriber in [topics].
     *  2. If it's the first subscriber — or requests a higher QoS than the
     *     current broker subscription — (re-)subscribes at the broker.
     *  3. On cancellation, removes the subscriber; the last one out sends
     *     UNSUBSCRIBE.
     *
     * Wildcards are fully supported: `sensors/+/temp`, `home/#`, etc.
     * Backpressure is governed by [MqttConfig.subscribeBufferSize] and
     * [MqttConfig.subscribeOverflow].
     */
    override fun subscribe(topic: String, qos: Int): Flow<MqttMessage> = callbackFlow {
        require(topic.isNotBlank()) { "MQTT topic cannot be blank." }
        require(qos in 0..2) { "MQTT qos must be 0, 1, or 2." }

        val subscriber = TopicSubscriber(
            deliver = { message ->
                val result = trySend(message)
                if (result.isFailure && !result.isClosed) {
                    logger.warn("MQTT message dropped (collector too slow) topic=${message.topic}")
                }
            },
            close = { channel.close() }
        )

        // Atomic register: compute() prevents racing a concurrent last-out
        // removal of the same topic (decision 2).
        var brokerQos = qos
        var needsBrokerSubscribe = false
        topics.compute(topic) { _, current ->
            if (current == null) {
                needsBrokerSubscribe = true
                brokerQos = qos
                TopicState(qos, listOf(subscriber))
            } else {
                brokerQos = maxOf(current.qos, qos)
                needsBrokerSubscribe = brokerQos > current.qos
                TopicState(brokerQos, current.subscribers + subscriber)
            }
        }

        if (needsBrokerSubscribe && _state.value is MqttConnectionState.Connected) {
            brokerOps.withLock {
                runCatching { applySubscribe(topic, brokerQos) }
                    .onFailure { logger.warn("Subscribe failed; will retry on reconnect", it) }
            }
        }

        logger.debug(
            "MQTT ← subscribed topic=$topic qos=$qos " +
                "(brokerQos=$brokerQos, totalListeners=${topics[topic]?.subscribers?.size ?: 0})"
        )

        awaitClose {
            val remaining = topics.compute(topic) { _, current ->
                val left = current?.subscribers?.filterNot { it === subscriber }
                if (left.isNullOrEmpty()) null else TopicState(current.qos, left)
            }
            if (remaining == null) {
                scope.launch {
                    brokerOps.withLock {
                        // Re-check under the lock: a new collector may have
                        // re-registered the topic while this launch was queued.
                        if (!topics.containsKey(topic)) {
                            runCatching {
                                client?.unsubscribeWith()?.topicFilter(topic)?.send()?.await()
                                logger.debug("MQTT ← unsubscribed topic=$topic (no more listeners)")
                            }
                        }
                    }
                }
            }
        }
    }.buffer(config.subscribeBufferSize, config.subscribeOverflow)

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
                // Fan-out: deliver to all registered subscribers for this topic
                topics[topic]?.subscribers?.forEach { sub ->
                    runCatching { sub.deliver(msg) }
                        .onFailure { logger.error("MQTT listener threw for topic=$topic", it) }
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
