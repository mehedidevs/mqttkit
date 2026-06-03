package io.github.mehedidevs.mqttkit

/**
 * Represents a single MQTT PUBLISH message — either inbound (received) or
 * outbound (to be sent).
 *
 * MQTT concepts:
 *  - [topic]    : UTF-8 string hierarchy, e.g. "home/living-room/temperature"
 *  - [payload]  : raw bytes — could be JSON, plain text, Protobuf, or binary
 *  - [qos]      : Quality of Service level (see [MqttClient.subscribe] for details)
 *  - [retained] : if true, broker stores this as the "last known value" for the topic
 *                 and delivers it immediately to new subscribers
 */
data class MqttMessage(
    val topic: String,
    val payload: ByteArray,
    val qos: Int = 1,
    val retained: Boolean = false
) {
    init {
        require(topic.isNotBlank()) { "MQTT topic cannot be blank." }
        require(qos in 0..2) { "MQTT qos must be 0, 1, or 2." }
    }

    /** Convenience: decode payload as UTF-8 text. */
    val payloadAsString: String get() = String(payload, Charsets.UTF_8)

    // ByteArray is a reference type — we must compare content, not identity.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MqttMessage) return false
        return topic == other.topic
                && payload.contentEquals(other.payload)
                && qos == other.qos
                && retained == other.retained
    }

    override fun hashCode(): Int {
        var r = topic.hashCode()
        r = 31 * r + payload.contentHashCode()
        r = 31 * r + qos
        r = 31 * r + retained.hashCode()
        return r
    }
}
