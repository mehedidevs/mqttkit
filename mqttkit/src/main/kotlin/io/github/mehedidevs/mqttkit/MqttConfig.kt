package io.github.mehedidevs.mqttkit

import kotlinx.coroutines.channels.BufferOverflow

/**
 * MQTT Last Will and Testament.
 *
 * The broker publishes this message if the client disappears without sending a
 * clean DISCONNECT packet, for example because the network dropped or the app
 * process died. A common production pattern is retained `online`/`offline`
 * status under `devices/{id}/status`.
 */
data class MqttWillMessage(
    /** Topic where the broker should publish the will message. */
    val topic: String,
    /** Text payload to publish. Use JSON if subscribers expect structured data. */
    val payload: String,
    /** MQTT QoS level for the will publish. Must be 0, 1, or 2. */
    val qos: Int = 1,
    /** Whether the broker should retain this as the latest value for [topic]. */
    val retained: Boolean = true
)

/**
 * A broker endpoint used for initial connection fallback.
 *
 * Use fallback endpoints for different broker hosts in the same cluster. Do not
 * repeat the primary endpoint here; use [MqttRetryPolicy] for retrying the same
 * endpoint.
 */
data class MqttBrokerEndpoint(
    val host: String,
    val port: Int = 1883,
    val useTls: Boolean = false,
    /** Non-null switches this endpoint to MQTT-over-WebSocket transport. */
    val webSocket: MqttWebSocketConfig? = null
) {
    init {
        require(host.isNotBlank()) { "MQTT endpoint host cannot be blank." }
        require(port in 1..65_535) { "MQTT endpoint port must be between 1 and 65535." }
    }
}

/**
 * Immutable configuration for one MQTT client session.
 *
 * [host], [port], and [useTls] form the primary broker endpoint. Optional
 * [fallbackEndpoints] are tried only during initial connect if the primary
 * endpoint fails.
 */
data class MqttConfig(
    /** Primary broker host name or IP address. */
    val host: String,
    /** Primary broker TCP port. Usually 1883 for plain MQTT or 8883 for TLS. */
    val port: Int = 1883,
    /** Unique client identifier. Duplicate live client IDs can disconnect each other. */
    val clientId: String,
    /** MQTT simple auth configuration. */
    val auth: MqttAuth = MqttAuth.None,
    /** Whether the primary endpoint should use TLS. */
    val useTls: Boolean = false,
    /** Non-null switches the primary endpoint to MQTT-over-WebSocket transport. */
    val webSocket: MqttWebSocketConfig? = null,
    /** Custom TLS settings (mutual TLS, private CAs) for every TLS endpoint. */
    val tlsConfig: MqttTlsConfig? = null,
    /** Backup broker endpoints tried in order during initial connect. */
    val fallbackEndpoints: List<MqttBrokerEndpoint> = emptyList(),

    /** `false` keeps broker session state when supported; `true` starts fresh. */
    val cleanStart: Boolean = false,
    /** Seconds the broker should retain session state after disconnect. */
    val sessionExpiryIntervalSec: Long = 3600,

    /** MQTT keep-alive interval in seconds. */
    val keepAliveSec: Int = 30,

    /** Enable HiveMQ automatic reconnect after unexpected disconnects. */
    val automaticReconnect: Boolean = true,
    /** First reconnect delay after unexpected disconnect. */
    val initialReconnectDelayMs: Long = 1_000,
    /** Maximum reconnect delay after exponential backoff. */
    val maxReconnectDelayMs: Long = 30_000,

    /** Optional Last Will and Testament message. */
    val willMessage: MqttWillMessage? = null,

    /**
     * Per-collector buffer for [MqttClient.subscribe] flows. When a collector
     * falls behind, up to this many messages are held before
     * [subscribeOverflow] kicks in.
     */
    val subscribeBufferSize: Int = 64,
    /**
     * What happens when a subscribe buffer is full. The default
     * [BufferOverflow.DROP_OLDEST] keeps the flow alive and favours the most
     * recent messages — usually right for telemetry. Use
     * [BufferOverflow.SUSPEND] if every message must be processed (drops are
     * then logged via [MqttLogger]).
     */
    val subscribeOverflow: BufferOverflow = BufferOverflow.DROP_OLDEST
) {
    /** Primary endpoint derived from [host], [port], [useTls], and [webSocket]. */
    val primaryEndpoint: MqttBrokerEndpoint
        get() = MqttBrokerEndpoint(host = host, port = port, useTls = useTls, webSocket = webSocket)

    /** Primary endpoint followed by fallback endpoints. */
    val endpoints: List<MqttBrokerEndpoint>
        get() = listOf(primaryEndpoint) + fallbackEndpoints

    @Deprecated(
        message = "Use auth = MqttAuth.Basic(username, password) instead.",
        replaceWith = ReplaceWith("MqttConfig(host, port, clientId, MqttAuth.Basic(username, password))")
    )
    constructor(
        host: String,
        port: Int = 1883,
        clientId: String,
        username: String?,
        password: String?,
        useTls: Boolean = false,
        cleanStart: Boolean = false,
        sessionExpiryIntervalSec: Long = 3600,
        keepAliveSec: Int = 30,
        automaticReconnect: Boolean = true,
        initialReconnectDelayMs: Long = 1_000,
        maxReconnectDelayMs: Long = 30_000,
        willMessage: MqttWillMessage? = null
    ) : this(
        host = host,
        port = port,
        clientId = clientId,
        auth = if (username.isNullOrBlank() && password.isNullOrBlank()) {
            MqttAuth.None
        } else {
            MqttAuth.Basic(
                username = requireNotNull(username).trim(),
                password = requireNotNull(password)
            )
        },
        useTls = useTls,
        cleanStart = cleanStart,
        sessionExpiryIntervalSec = sessionExpiryIntervalSec,
        keepAliveSec = keepAliveSec,
        automaticReconnect = automaticReconnect,
        initialReconnectDelayMs = initialReconnectDelayMs,
        maxReconnectDelayMs = maxReconnectDelayMs,
        willMessage = willMessage
    )

    init {
        require(host.isNotBlank()) { "MQTT host cannot be blank." }
        require(port in 1..65_535) { "MQTT port must be between 1 and 65535." }
        require(endpoints.distinct().size == endpoints.size) {
            "MQTT endpoints must not contain duplicates. Use retry policy for retrying the same endpoint."
        }
        require(clientId.isNotBlank()) { "MQTT clientId cannot be blank." }
        require(keepAliveSec > 0) { "MQTT keepAliveSec must be greater than 0." }
        require(sessionExpiryIntervalSec >= 0) { "MQTT sessionExpiryIntervalSec cannot be negative." }
        require(initialReconnectDelayMs > 0) { "MQTT initialReconnectDelayMs must be greater than 0." }
        require(maxReconnectDelayMs >= initialReconnectDelayMs) {
            "MQTT maxReconnectDelayMs must be greater than or equal to initialReconnectDelayMs."
        }
        require(willMessage?.topic?.isNotBlank() != false) { "MQTT will topic cannot be blank." }
        require(subscribeBufferSize >= 1) { "MQTT subscribeBufferSize must be at least 1." }
    }
}
