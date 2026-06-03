package io.github.mehedidevs.mqttkit

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Converts between app/domain objects and MQTT payload bytes.
 *
 * Keep JSON, protobuf, encrypted payloads, or any app-specific DTO mapping
 * outside the MQTT transport. Your app can provide a codec per response type.
 */
fun interface MqttPayloadDecoder<T> {
    /** Convert a raw MQTT message into an app/domain value. */
    fun decode(message: MqttMessage): T
}

/** Converts app/domain values to and from MQTT payload bytes. */
interface MqttPayloadCodec<T> : MqttPayloadDecoder<T> {
    /** Encode [value] into MQTT payload bytes. */
    fun encode(value: T): ByteArray
}

/** A decoded MQTT message plus the original raw message metadata. */
data class MqttTypedMessage<T>(
    val topic: String,
    val value: T,
    val qos: Int,
    val retained: Boolean,
    val raw: MqttMessage
)

object StringPayloadCodec : MqttPayloadCodec<String> {
    override fun encode(value: String): ByteArray = value.toByteArray(Charsets.UTF_8)
    override fun decode(message: MqttMessage): String = message.payloadAsString
}

object ByteArrayPayloadCodec : MqttPayloadCodec<ByteArray> {
    override fun encode(value: ByteArray): ByteArray = value
    override fun decode(message: MqttMessage): ByteArray = message.payload
}

/** Encode [value] with [codec] and publish it to [topic]. */
suspend fun <T> MqttClient.publish(
    topic: String,
    value: T,
    codec: MqttPayloadCodec<T>,
    qos: Int = 1,
    retained: Boolean = false
): Result<Unit> = publish(
    MqttMessage(
        topic = topic,
        payload = codec.encode(value),
        qos = qos,
        retained = retained
    )
)

/** Subscribe to [topic] and decode each raw message with [decoder]. */
fun <T> MqttClient.subscribe(
    topic: String,
    decoder: MqttPayloadDecoder<T>,
    qos: Int = 1
): Flow<MqttTypedMessage<T>> = subscribe(topic, qos).map { message ->
    MqttTypedMessage(
        topic = message.topic,
        value = decoder.decode(message),
        qos = message.qos,
        retained = message.retained,
        raw = message
    )
}
