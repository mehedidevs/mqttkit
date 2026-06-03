package io.github.mehedidevs.mqttkit

/**
 * All possible states of an MQTT client connection.
 *
 * State machine:
 *   Idle ──connect()──► Connecting ──success──► Connected
 *                                  ──fail──────► Failed
 *   Connected ──network drop──► Reconnecting (attempt 1, 2, …)
 *             ──disconnect()──► Disconnected("manual")
 *   Reconnecting ──success──► Connected
 *                ──give up──► Disconnected(reason)
 */
sealed interface MqttConnectionState {
    data object Idle        : MqttConnectionState
    data object Connecting  : MqttConnectionState
    data object Connected   : MqttConnectionState

    /** Auto-reconnect is in progress. [attempt] is 1-based. */
    data class Reconnecting(val attempt: Int) : MqttConnectionState

    /** Cleanly disconnected or broker rejected. [reason] may be null. */
    data class Disconnected(val reason: String?) : MqttConnectionState

    /** Initial connect() threw — no auto-retry will happen. */
    data class Failed(val cause: Throwable) : MqttConnectionState
}
