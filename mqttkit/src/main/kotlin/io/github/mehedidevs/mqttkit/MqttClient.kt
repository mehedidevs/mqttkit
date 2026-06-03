package io.github.mehedidevs.mqttkit

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * App-facing MQTT client contract.
 *
 * ## MQTT Quality of Service (QoS) quick reference
 *
 * | Level | Name            | Guarantee                              | When to use               |
 * |-------|-----------------|----------------------------------------|---------------------------|
 * |   0   | At most once    | Fire-and-forget. May be lost.          | Sensor readings, telemetry|
 * |   1   | At least once   | Delivered ≥1 time. May duplicate.      | Commands, alerts          |
 * |   2   | Exactly once    | Exactly one delivery. Highest overhead.| Billing, critical events  |
 *
 * ## Topic Wildcards
 *  - `+` matches exactly one level:  `home/+/temperature` matches `home/room1/temperature`
 *  - `#` matches zero or more levels: `home/#` matches everything under `home/`
 *
 * ## Reconnect behaviour
 * Subscriptions are automatically re-applied after a reconnect so callers
 * don't need to re-subscribe. The implementation tracks active subscriptions
 * internally and replays them as soon as the connection is re-established.
 */
interface MqttClient {
    /** Current connection state. Collect this from app/UI/service code. */
    val connectionState: StateFlow<MqttConnectionState>

    /**
     * Establish a connection to the broker.
     *
     * Suspends until the MQTT handshake completes or throws. Implementations may
     * try configured fallback endpoints during this initial connection.
     */
    suspend fun connect()

    /**
     * Gracefully close the connection and clear tracked subscriptions.
     *
     * This is treated as an intentional/manual disconnect and must not trigger
     * automatic reconnect.
     */
    suspend fun disconnect()

    /**
     * Publish [message] to the broker.
     *
     * Returns [Result.failure] instead of throwing so callers can handle errors
     * without a try/catch at the call site.
     */
    suspend fun publish(message: MqttMessage): Result<Unit>

    /**
     * Subscribe to [topic] (supports wildcards) and receive a [Flow] of
     * matching messages.
     *
     * The subscription is established lazily when the [Flow] is collected and
     * removed from the broker when collection is cancelled. Implementations
     * should re-subscribe automatically after reconnect.
     */
    fun subscribe(topic: String, qos: Int = 1): Flow<MqttMessage>
}
