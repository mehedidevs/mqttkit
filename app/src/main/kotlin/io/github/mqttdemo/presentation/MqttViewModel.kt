package io.github.mqttdemo.presentation

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.mehedidevs.mqttkit.MqttClient
import io.github.mehedidevs.mqttkit.MqttAuth
import io.github.mehedidevs.mqttkit.MqttConfig
import io.github.mehedidevs.mqttkit.MqttConnectionState
import io.github.mehedidevs.mqttkit.MqttMessage
import io.github.mehedidevs.mqttkit.MqttRetryPolicy
import io.github.mehedidevs.mqttkit.MqttSessionManager
import io.github.mehedidevs.mqttkit.MqttWillMessage
import io.github.mehedidevs.mqttkit.startWithRetry
import io.github.mqttdemo.domain.LogEntry
import io.github.mqttdemo.domain.MessageDirection
import io.github.mqttdemo.domain.SensorReading
import io.github.mqttdemo.service.MqttForegroundService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.UUID
import kotlin.math.sin
import kotlin.math.cos
import kotlin.random.Random

/**
 * Single ViewModel for the entire MQTT Demo app.
 *
 * ── Why [AndroidViewModel]? ──────────────────────────────────────────────────
 * We need the [Application] context to start/stop the foreground service.
 * Normally prefer plain ViewModel, but here the service interaction justifies it.
 *
 * ── Survival across Activity destruction ─────────────────────────────────────
 * [MqttSessionManager] is a Koin `single` — it lives in the Application scope.
 * The ViewModel holds a reference to the active [MqttClient] from that manager.
 * When the Activity is recreated (rotation, theme change), the new ViewModel
 * instance detects the existing client via [MqttSessionManager.current] and
 * reattaches — no reconnect needed.
 *
 * For true background survival (swipe-away, low memory), [MqttForegroundService]
 * keeps the process alive with an ongoing notification.
 */
class MqttViewModel(
    application: Application,
    private val sessionManager: MqttSessionManager
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(
        MqttUiState(clientId = "mqttdemo-${UUID.randomUUID().toString().take(8)}")
    )
    val uiState: StateFlow<MqttUiState> = _uiState.asStateFlow()

    // Active jobs
    private var connectionStateJob: Job? = null
    private var subscriptionJobs   = mutableMapOf<String, Job>()  // topic → collect Job
    private var sensorSimJob: Job? = null

    // Simulated sensor tick for wave generation
    private var sensorTick = 0

    // ── Namespace for all topics this client owns ─────────────────────────────
    private val topicBase get() = "mqttdemo/${_uiState.value.clientId}"

    init {
        // Re-attach to an existing session if the ViewModel was recreated
        sessionManager.current()?.let { existingClient ->
            Timber.d("MqttViewModel: reattaching to existing MQTT session")
            attachToClient(existingClient)
        }
    }

    // ════════════════════════════════════════════════════════════════════════════
    //  Event handler — single entry point from the UI
    // ════════════════════════════════════════════════════════════════════════════

    fun onEvent(event: MqttUiEvent) {
        when (event) {
            // ── Connection ────────────────────────────────────────────────
            is MqttUiEvent.Connect    -> connect()
            is MqttUiEvent.Disconnect -> disconnect()

            // ── Broker config fields ──────────────────────────────────────
            is MqttUiEvent.BrokerHostChanged   -> _uiState.update { it.copy(brokerHost = event.value) }
            is MqttUiEvent.BrokerPortChanged   -> _uiState.update { it.copy(brokerPort = event.value) }
            is MqttUiEvent.PublicBrokerSelected -> applyPublicBroker(event.preset)
            is MqttUiEvent.ClientIdChanged     -> _uiState.update { it.copy(clientId = event.value) }
            is MqttUiEvent.UsernameChanged     -> _uiState.update { it.copy(username = event.value) }
            is MqttUiEvent.PasswordChanged     -> _uiState.update { it.copy(password = event.value) }
            is MqttUiEvent.UseTlsChanged       -> _uiState.update { it.copy(useTls = event.value) }
            is MqttUiEvent.CleanStartChanged   -> _uiState.update { it.copy(cleanStart = event.value) }
            is MqttUiEvent.KeepAliveChanged    -> _uiState.update { it.copy(keepAliveSec = event.value) }
            is MqttUiEvent.EnableLwtChanged    -> _uiState.update { it.copy(enableLwt = event.value) }

            // ── Publish ───────────────────────────────────────────────────
            is MqttUiEvent.PublishTopicChanged   -> _uiState.update { it.copy(publishTopic = event.value) }
            is MqttUiEvent.PublishPayloadChanged -> _uiState.update { it.copy(publishPayload = event.value) }
            is MqttUiEvent.PublishQosChanged     -> _uiState.update { it.copy(publishQos = event.value) }
            is MqttUiEvent.PublishRetainedChanged -> _uiState.update { it.copy(publishRetained = event.value) }
            is MqttUiEvent.Publish               -> publishUserMessage()

            // ── Subscribe ─────────────────────────────────────────────────
            is MqttUiEvent.SubscribeTopicChanged -> _uiState.update { it.copy(subscribeTopic = event.value) }
            is MqttUiEvent.AddSubscription       -> addSubscription()
            is MqttUiEvent.RemoveSubscription    -> removeSubscription(event.topic)

            // ── Dashboard ─────────────────────────────────────────────────
            is MqttUiEvent.ToggleAutoPublish -> toggleAutoPublish()

            // ── Log ───────────────────────────────────────────────────────
            is MqttUiEvent.ClearLog      -> _uiState.update { it.copy(logEntries = emptyList()) }
            is MqttUiEvent.SnackbarShown -> _uiState.update { it.copy(snackbarMessage = null) }
        }
    }

    // ════════════════════════════════════════════════════════════════════════════
    //  Connection
    // ════════════════════════════════════════════════════════════════════════════

    private fun applyPublicBroker(preset: PublicBrokerPreset) {
        _uiState.update {
            it.copy(
                brokerHost = preset.host,
                brokerPort = preset.port,
                useTls = preset.useTls,
                username = preset.username,
                password = preset.password,
                snackbarMessage = "${preset.name} broker selected"
            )
        }
    }

    private fun connect() {
        val state = _uiState.value
        val port = state.brokerPort.toIntOrNull() ?: 1883
        val keepAlive = state.keepAliveSec.toIntOrNull() ?: 30

        // Build the LWT: when this client drops off without DISCONNECT, the broker
        // publishes "offline" to the status topic with retained=true, so any new
        // subscriber immediately sees the device is gone.
        val willMessage = if (state.enableLwt) {
            MqttWillMessage(
                topic    = "$topicBase/status",
                payload  = """{"status":"offline","clientId":"${state.clientId}"}""",
                qos      = 1,
                retained = true
            )
        } else null

        val config = MqttConfig(
            host                   = state.brokerHost,
            port                   = port,
            clientId               = state.clientId,
            auth                   = if (state.username.isBlank() && state.password.isBlank()) {
                MqttAuth.None
            } else {
                MqttAuth.Basic(
                    username = state.username.trim(),
                    password = state.password
                )
            },
            useTls                 = state.useTls,
            cleanStart             = state.cleanStart,
            keepAliveSec           = keepAlive,
            automaticReconnect     = true,
            initialReconnectDelayMs = 1_000,
            maxReconnectDelayMs    = 30_000,
            willMessage            = willMessage
        )

        viewModelScope.launch {
            sessionManager.startWithRetry(
                config = config,
                retryPolicy = MqttRetryPolicy(
                    maxAttempts = 3,
                    initialDelayMs = 1_000,
                    maxDelayMs = 5_000
                )
            ).onSuccess { client ->
                attachToClient(client)
                subscribeToDefaultTopics()

                // Publish "online" status (retained) — counterpart to the LWT "offline"
                publishStatus("online")

                // Start foreground service to keep process alive in background
                startForegroundService()
            }.onFailure { error ->
                Timber.e(error, "connect() failed")
                _uiState.update {
                    it.copy(snackbarMessage = "Connection failed: ${error.message}")
                }
            }
        }
    }

    private fun disconnect() {
        viewModelScope.launch {
            _uiState.update { it.copy(isDisconnecting = true) }

            try {
                // Publish "offline" manually before clean disconnect so the broker
                // doesn't need to send the LWT (we're disconnecting gracefully).
                publishStatus("offline")
                delay(200) // allow the publish to flush

                sensorSimJob?.cancel()
                sensorSimJob = null
                subscriptionJobs.values.forEach { it.cancel() }
                subscriptionJobs.clear()

                sessionManager.stop()

                // connectionStateJob is cancelled below, so mirror the final state
                // explicitly; otherwise the UI can keep showing "Connected".
                _uiState.update {
                    it.copy(
                        connectionState = MqttConnectionState.Disconnected("manual"),
                        activeSubscriptions = emptyList(),
                        isAutoPublishing = false,
                        isDisconnecting = false,
                        snackbarMessage = "Disconnected"
                    )
                }
            } catch (error: Throwable) {
                Timber.e(error, "disconnect() failed")
                _uiState.update {
                    it.copy(
                        isDisconnecting = false,
                        snackbarMessage = "Disconnect failed: ${error.message}"
                    )
                }
            } finally {
                connectionStateJob?.cancel()
                connectionStateJob = null
                stopForegroundService()
            }
        }
    }

    /** Observe connection state changes from the client and mirror them to UiState. */
    private fun attachToClient(client: MqttClient) {
        connectionStateJob?.cancel()
        connectionStateJob = viewModelScope.launch {
            client.connectionState.collect { state ->
                _uiState.update { it.copy(connectionState = state) }
                if (state is MqttConnectionState.Connected) {
                    Timber.i("ViewModel: connected — re-subscribing UI topics")
                    // Re-subscribe all active UI subscriptions after reconnect
                    resubscribeAll()
                }
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════════════
    //  Subscriptions
    // ════════════════════════════════════════════════════════════════════════════

    /**
     * Default topics subscribed automatically on connect:
     *  - Device-specific wildcard: everything under this client's namespace
     *  - Global broadcast channel: shared by all demo users
     */
    private fun subscribeToDefaultTopics() {
        subscribeToTopic("$topicBase/#")
        subscribeToTopic("mqttdemo/broadcast/#")
    }

    private fun addSubscription() {
        val topic = _uiState.value.subscribeTopic.trim()
        if (topic.isBlank()) return
        if (_uiState.value.activeSubscriptions.contains(topic)) {
            _uiState.update { it.copy(snackbarMessage = "Already subscribed to $topic") }
            return
        }
        subscribeToTopic(topic)
        _uiState.update { it.copy(subscribeTopic = "") }
    }

    private fun removeSubscription(topic: String) {
        subscriptionJobs[topic]?.cancel()
        subscriptionJobs.remove(topic)
        _uiState.update { it.copy(activeSubscriptions = it.activeSubscriptions - topic) }
    }

    private fun subscribeToTopic(topic: String, qos: Int = 1) {
        val client = sessionManager.current() ?: return
        if (subscriptionJobs.containsKey(topic)) return   // already subscribed

        _uiState.update { it.copy(activeSubscriptions = it.activeSubscriptions + topic) }

        subscriptionJobs[topic] = viewModelScope.launch {
            client.subscribe(topic, qos).collect { message ->
                handleIncomingMessage(message)
            }
        }
        Timber.d("UI subscribed: $topic")
    }

    private fun resubscribeAll() {
        // Jobs are cancelled on reconnect by the library; re-launch collectors
        val topics = subscriptionJobs.keys.toList()
        subscriptionJobs.values.forEach { it.cancel() }
        subscriptionJobs.clear()
        topics.forEach { subscribeToTopic(it) }
    }

    private fun handleIncomingMessage(message: MqttMessage) {
        // Update live sensor panel if the message is from our own sensor topics
        when {
            message.topic.endsWith("/sensor/temperature") ->
                message.payload.toFloatOrNull()?.let { temp ->
                    _uiState.update { it.copy(sensorReading = it.sensorReading.copy(temperature = temp)) }
                }
            message.topic.endsWith("/sensor/humidity") ->
                message.payload.toFloatOrNull()?.let { hum ->
                    _uiState.update { it.copy(sensorReading = it.sensorReading.copy(humidity = hum)) }
                }
        }

        addToLog(
            topic     = message.topic,
            payload   = message.payloadAsString,
            qos       = message.qos,
            retained  = message.retained,
            direction = MessageDirection.INBOUND
        )
    }

    // ════════════════════════════════════════════════════════════════════════════
    //  Publish
    // ════════════════════════════════════════════════════════════════════════════

    private fun publishUserMessage() {
        val state = _uiState.value
        if (state.publishTopic.isBlank()) {
            _uiState.update { it.copy(snackbarMessage = "Topic cannot be empty") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isPublishing = true, lastPublishError = null) }

            val message = MqttMessage(
                topic    = state.publishTopic,
                payload  = state.publishPayload.toByteArray(),
                qos      = state.publishQos,
                retained = state.publishRetained
            )

            sessionManager.current()
                ?.publish(message)
                ?.onSuccess {
                    addToLog(
                        topic     = message.topic,
                        payload   = message.payloadAsString,
                        qos       = message.qos,
                        retained  = message.retained,
                        direction = MessageDirection.OUTBOUND
                    )
                    _uiState.update { it.copy(snackbarMessage = "Published ✓") }
                }
                ?.onFailure { err ->
                    _uiState.update { it.copy(lastPublishError = err.message) }
                }
                ?: _uiState.update { it.copy(snackbarMessage = "Not connected") }

            _uiState.update { it.copy(isPublishing = false) }
        }
    }

    private suspend fun publishStatus(status: String) {
        val payload = """{"status":"$status","clientId":"${_uiState.value.clientId}"}"""
        sessionManager.current()?.publish(
            MqttMessage(
                topic    = "$topicBase/status",
                payload  = payload.toByteArray(),
                qos      = 1,
                retained = true
            )
        )
    }

    // ════════════════════════════════════════════════════════════════════════════
    //  Sensor simulation (Dashboard)
    // ════════════════════════════════════════════════════════════════════════════

    private fun toggleAutoPublish() {
        val wasRunning = _uiState.value.isAutoPublishing
        if (wasRunning) {
            sensorSimJob?.cancel()
            sensorSimJob = null
            _uiState.update { it.copy(isAutoPublishing = false) }
        } else {
            _uiState.update { it.copy(isAutoPublishing = true) }
            sensorSimJob = viewModelScope.launch {
                while (true) {
                    simulateAndPublishSensor()
                    delay(3_000) // publish every 3 seconds
                }
            }
        }
    }

    /**
     * Generates realistic-looking sensor values using sine waves + noise,
     * then publishes each to its own topic — demonstrating per-measurement topics
     * which is the standard IoT pattern.
     *
     * Topic structure:
     *   mqttdemo/{clientId}/sensor/temperature   QoS 0 — telemetry (fire-and-forget)
     *   mqttdemo/{clientId}/sensor/humidity      QoS 0
     *   mqttdemo/{clientId}/sensor/pressure      QoS 0
     *   mqttdemo/{clientId}/sensor/light         QoS 0
     *   mqttdemo/{clientId}/sensor/all           QoS 1 — full JSON snapshot
     */
    private suspend fun simulateAndPublishSensor() {
        val t = sensorTick++.toDouble()
        val reading = SensorReading(
            temperature = (22.0 + 4.0 * sin(t * 0.3) + Random.nextFloat() * 0.5).toFloat()
                .coerceIn(10f, 40f),
            humidity    = (60.0 + 10.0 * cos(t * 0.2) + Random.nextFloat() * 1.0).toFloat()
                .coerceIn(20f, 95f),
            pressure    = (1013.0 + 5.0 * sin(t * 0.1) + Random.nextFloat() * 0.3).toFloat()
                .coerceIn(950f, 1050f),
            lightLevel  = (400 + (200 * sin(t * 0.5)).toInt() + Random.nextInt(20))
                .coerceIn(0, 1000)
        )

        _uiState.update { it.copy(sensorReading = reading) }

        val client = sessionManager.current() ?: return

        // Individual sensor topics — QoS 0 (at most once) is fine for streaming telemetry
        mapOf(
            "$topicBase/sensor/temperature" to "%.1f".format(reading.temperature),
            "$topicBase/sensor/humidity"    to "%.1f".format(reading.humidity),
            "$topicBase/sensor/pressure"    to "%.1f".format(reading.pressure),
            "$topicBase/sensor/light"       to reading.lightLevel.toString()
        ).forEach { (topic, value) ->
            client.publish(MqttMessage(topic = topic, payload = value.toByteArray(), qos = 0))
        }

        // Full JSON snapshot — QoS 1 (at least once) to ensure it arrives
        val json = """
            {
              "temperature": ${"%.1f".format(reading.temperature)},
              "humidity":    ${"%.1f".format(reading.humidity)},
              "pressure":    ${"%.1f".format(reading.pressure)},
              "light":       ${reading.lightLevel},
              "unit":        {"temp":"°C","humidity":"%RH","pressure":"hPa","light":"lux"}
            }
        """.trimIndent()
        client.publish(MqttMessage(
            topic   = "$topicBase/sensor/all",
            payload = json.toByteArray(),
            qos     = 1
        ))
    }

    // ════════════════════════════════════════════════════════════════════════════
    //  Message log
    // ════════════════════════════════════════════════════════════════════════════

    private fun addToLog(
        topic: String, payload: String, qos: Int,
        retained: Boolean, direction: MessageDirection
    ) {
        val entry = LogEntry(
            topic     = topic,
            payload   = payload,
            qos       = qos,
            retained  = retained,
            direction = direction
        )
        _uiState.update { state ->
            // Keep last 200 entries to avoid unbounded memory growth
            val updated = (listOf(entry) + state.logEntries).take(200)
            state.copy(logEntries = updated)
        }
    }

    // ════════════════════════════════════════════════════════════════════════════
    //  Foreground Service
    // ════════════════════════════════════════════════════════════════════════════

    private fun startForegroundService() {
        val intent = Intent(getApplication(), MqttForegroundService::class.java)
            .setAction(MqttForegroundService.ACTION_START)
        getApplication<Application>().startService(intent)
    }

    private fun stopForegroundService() {
        val intent = Intent(getApplication(), MqttForegroundService::class.java)
            .setAction(MqttForegroundService.ACTION_STOP)
        getApplication<Application>().startService(intent)
    }

    // ════════════════════════════════════════════════════════════════════════════
    //  Cleanup
    // ════════════════════════════════════════════════════════════════════════════

    override fun onCleared() {
        super.onCleared()
        // ViewModel is destroyed — but MqttSessionManager (Koin single) lives on.
        // We do NOT disconnect here. The foreground service keeps the connection alive.
        // On next launch, init{} detects the existing session and reattaches.
        sensorSimJob?.cancel()
        connectionStateJob?.cancel()
        subscriptionJobs.values.forEach { it.cancel() }
        Timber.d("MqttViewModel cleared — connection kept alive by foreground service")
    }
}

// Extension to parse a ByteArray as Float (for sensor topic payloads)
private fun ByteArray.toFloatOrNull(): Float? =
    runCatching { String(this, Charsets.UTF_8).trim().toFloat() }.getOrNull()
