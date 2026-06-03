package io.github.mehedidevs.mqttkit

import kotlinx.coroutines.delay

/**
 * Retry policy for explicit connect/publish retry helpers.
 *
 * This is separate from [MqttConfig.automaticReconnect], which handles
 * unexpected disconnects after a successful connection.
 */
data class MqttRetryPolicy(
    val maxAttempts: Int = 3,
    val initialDelayMs: Long = 500,
    val maxDelayMs: Long = 5_000,
    val backoffMultiplier: Double = 2.0
) {
    init {
        require(maxAttempts >= 1) { "MQTT maxAttempts must be at least 1." }
        require(initialDelayMs >= 0) { "MQTT initialDelayMs cannot be negative." }
        require(maxDelayMs >= initialDelayMs) {
            "MQTT maxDelayMs must be greater than or equal to initialDelayMs."
        }
        require(backoffMultiplier >= 1.0) { "MQTT backoffMultiplier must be at least 1.0." }
    }
}

/** Start a session, retrying transient initial connection failures. */
suspend fun MqttSessionManager.startWithRetry(
    config: MqttConfig,
    retryPolicy: MqttRetryPolicy = MqttRetryPolicy()
): Result<MqttClient> = retryResult(retryPolicy) {
    start(config)
}

/** Publish a raw message, retrying transient publish failures. */
suspend fun MqttClient.publishWithRetry(
    message: MqttMessage,
    retryPolicy: MqttRetryPolicy = MqttRetryPolicy()
): Result<Unit> = retryResult(retryPolicy) {
    publish(message).getOrThrow()
}

/** Encode [value] as JSON and publish it, retrying transient publish failures. */
suspend inline fun <reified T> MqttClient.publishJsonWithRetry(
    topic: String,
    value: T,
    qos: Int = 1,
    retained: Boolean = false,
    retryPolicy: MqttRetryPolicy = MqttRetryPolicy()
): Result<Unit> = retryResult(retryPolicy) {
    publishJson(
        topic = topic,
        value = value,
        qos = qos,
        retained = retained
    ).getOrThrow()
}

@PublishedApi
internal suspend fun <T> retryResult(
    retryPolicy: MqttRetryPolicy,
    block: suspend () -> T
): Result<T> {
    var lastError: Throwable? = null
    var delayMs = retryPolicy.initialDelayMs

    repeat(retryPolicy.maxAttempts) { attempt ->
        runCatching { block() }
            .onSuccess { return Result.success(it) }
            .onFailure { error ->
                lastError = error
                if (attempt < retryPolicy.maxAttempts - 1 && delayMs > 0) {
                    delay(delayMs)
                    delayMs = (delayMs * retryPolicy.backoffMultiplier)
                        .toLong()
                        .coerceAtMost(retryPolicy.maxDelayMs)
                }
            }
    }

    return Result.failure(
        IllegalStateException(
            "MQTT operation failed after ${retryPolicy.maxAttempts} attempt(s): ${lastError?.message}",
            lastError
        )
    )
}
