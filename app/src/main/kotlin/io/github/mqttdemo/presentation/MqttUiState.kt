package io.github.mqttdemo.presentation

import io.github.mqttdemo.domain.SensorReading

// ════════════════════════════════════════════════════════════════════
//  UI State  —  single source of truth for the entire screen
// ════════════════════════════════════════════════════════════════════

data class MqttUiState(
    // ── Connection ────────────────────────────────────────────────
    val connectionState: io.github.mehedidevs.mqttkit.MqttConnectionState = io.github.mehedidevs.mqttkit.MqttConnectionState.Idle,
    val isDisconnecting: Boolean  = false,
    val brokerHost: String       = "broker.hivemq.com",
    val brokerPort: String       = "1883",
    val clientId: String         = "",      // auto-generated on first launch
    val username: String         = "",
    val password: String         = "",
    val useTls: Boolean          = false,
    val cleanStart: Boolean      = false,
    val keepAliveSec: String     = "30",
    val enableLwt: Boolean       = true,

    // ── Publish form ──────────────────────────────────────────────
    val publishTopic: String     = "",
    val publishPayload: String   = "",
    val publishQos: Int          = 1,
    val publishRetained: Boolean = false,
    val isPublishing: Boolean    = false,
    val lastPublishError: String? = null,

    // ── Subscribe form ────────────────────────────────────────────
    val subscribeTopic: String         = "",
    val activeSubscriptions: List<String> = emptyList(),

    // ── Dashboard / sensor simulation ────────────────────────────
    val sensorReading: SensorReading = SensorReading(),
    val isAutoPublishing: Boolean    = false,

    // ── Message log ───────────────────────────────────────────────
    val logEntries: List<io.github.mqttdemo.domain.LogEntry> = emptyList(),

    // ── Snackbar / one-shot feedback ─────────────────────────────
    val snackbarMessage: String? = null,
)

data class PublicBrokerPreset(
    val name: String,
    val host: String,
    val port: String,
    val useTls: Boolean,
    val username: String = "",
    val password: String = "",
    val note: String
)

val publicBrokerPresets = listOf(
    PublicBrokerPreset(
        name = "HiveMQ",
        host = "broker.hivemq.com",
        port = "1883",
        useTls = false,
        note = "Public test broker"
    ),
 PublicBrokerPreset(
        name = "EMQX",
        host = "broker.emqx.io",
        port = "1883",
        useTls = false,
        note = "Open-source EMQX public broker"
    ),
PublicBrokerPreset(
        name = "Mosquitto",
        host = "test.mosquitto.org",
        port = "1883",
        useTls = false,
        note = "Eclipse Mosquitto test server"
    ),
PublicBrokerPreset(
        name = "EMQX TLS",
        host = "broker.emqx.io",
        port = "8883",
        useTls = true,
        note = "Encrypted public MQTT"
    )
)

// ════════════════════════════════════════════════════════════════════
//  UI Events  —  intents from the UI to the ViewModel
// ════════════════════════════════════════════════════════════════════

sealed interface MqttUiEvent {
    // connection
    data object Connect           : MqttUiEvent
    data object Disconnect        : MqttUiEvent

    // broker config fields
    data class BrokerHostChanged(val value: String)   : MqttUiEvent
    data class BrokerPortChanged(val value: String)   : MqttUiEvent
    data class PublicBrokerSelected(val preset: io.github.mqttdemo.presentation.PublicBrokerPreset) : MqttUiEvent
    data class ClientIdChanged(val value: String)     : MqttUiEvent
    data class UsernameChanged(val value: String)     : MqttUiEvent
    data class PasswordChanged(val value: String)     : MqttUiEvent
    data class UseTlsChanged(val value: Boolean)      : MqttUiEvent
    data class CleanStartChanged(val value: Boolean)  : MqttUiEvent
    data class KeepAliveChanged(val value: String)    : MqttUiEvent
    data class EnableLwtChanged(val value: Boolean)   : MqttUiEvent

    // publish form
    data class PublishTopicChanged(val value: String)   : MqttUiEvent
    data class PublishPayloadChanged(val value: String) : MqttUiEvent
    data class PublishQosChanged(val value: Int)        : MqttUiEvent
    data class PublishRetainedChanged(val value: Boolean) : MqttUiEvent
    data object Publish                                 : MqttUiEvent

    // subscribe form
    data class SubscribeTopicChanged(val value: String) : MqttUiEvent
    data object AddSubscription                         : MqttUiEvent
    data class RemoveSubscription(val topic: String)    : MqttUiEvent

    // dashboard
    data object ToggleAutoPublish : MqttUiEvent

    // log
    data object ClearLog          : MqttUiEvent
    data object SnackbarShown     : MqttUiEvent
}
