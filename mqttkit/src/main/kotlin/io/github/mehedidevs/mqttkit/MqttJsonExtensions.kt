package io.github.mehedidevs.mqttkit

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.reflect.typeOf

/**
 * Default JSON configuration used by the generic MQTT JSON helpers.
 *
 * Unknown keys are ignored so newer server payloads do not break older apps.
 */
val DefaultMqttJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    explicitNulls = false
    encodeDefaults = true
}

/**
 * Error thrown when an MQTT payload cannot be decoded into the requested type.
 */
class MqttPayloadDecodeException(
    val topic: String,
    val targetType: String,
    val payloadPreview: String,
    cause: Throwable
) : IllegalArgumentException(
    "Failed to decode MQTT payload on topic '$topic' as $targetType. " +
            "Payload preview: '$payloadPreview'. Reason: ${cause.message}",
    cause
)

/**
 * Decode this message payload as JSON into [T].
 *
 * Returns a [Result] so callers can surface [MqttPayloadDecodeException]
 * messages without crashing collectors.
 */
inline fun <reified T> MqttMessage.decodePayloadAsJson(
    json: Json = DefaultMqttJson
): Result<T> = runCatching {
    try {
        json.decodeFromString<T>(payloadAsString)
    } catch (error: SerializationException) {
        throw decodeError<T>(error)
    } catch (error: IllegalArgumentException) {
        throw decodeError<T>(error)
    }
}

/** Decode this message payload as JSON into [T], throwing on failure. */
inline fun <reified T> MqttMessage.requirePayloadAsJson(
    json: Json = DefaultMqttJson
): T = decodePayloadAsJson<T>(json).getOrThrow()

@PublishedApi
internal inline fun <reified T> MqttMessage.decodeError(error: Throwable): MqttPayloadDecodeException =
    MqttPayloadDecodeException(
        topic = topic,
        targetType = typeOf<T>().toString(),
        payloadPreview = payloadAsString.previewForError(),
        cause = error
    )

/**
 * Subscribe to [topic] and decode each JSON payload into [T].
 *
 * Each emission is a [Result], allowing bad payloads to be reported without
 * cancelling the whole subscription.
 */
inline fun <reified T> MqttClient.subscribeJson(
    topic: String,
    qos: Int = 1,
    json: Json = DefaultMqttJson
): Flow<Result<MqttTypedMessage<T>>> = subscribe(topic, qos).map { message ->
    message.decodePayloadAsJson<T>(json).map { value ->
        MqttTypedMessage(
            topic = message.topic,
            value = value,
            qos = message.qos,
            retained = message.retained,
            raw = message
        )
    }
}

/** Subscribe to [topic] and throw if any payload cannot be decoded as [T]. */
inline fun <reified T> MqttClient.subscribeJsonOrThrow(
    topic: String,
    qos: Int = 1,
    json: Json = DefaultMqttJson
): Flow<MqttTypedMessage<T>> = subscribe(topic, qos).map { message ->
    MqttTypedMessage(
        topic = message.topic,
        value = message.requirePayloadAsJson<T>(json),
        qos = message.qos,
        retained = message.retained,
        raw = message
    )
}

/** Encode [value] as JSON and publish it to [topic]. */
suspend inline fun <reified T> MqttClient.publishJson(
    topic: String,
    value: T,
    qos: Int = 1,
    retained: Boolean = false,
    json: Json = DefaultMqttJson
): Result<Unit> = publish(
    MqttMessage(
        topic = topic,
        payload = json.encodeToString(value).toByteArray(Charsets.UTF_8),
        qos = qos,
        retained = retained
    )
)

@PublishedApi
internal fun String.previewForError(maxLength: Int = 240): String {
    val compact = replace(Regex("\\s+"), " ").trim()
    return if (compact.length <= maxLength) compact else compact.take(maxLength) + "..."
}
