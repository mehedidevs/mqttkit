package io.github.mehedidevs.mqttkit

/**
 * Logging hook for the MQTT kit.
 *
 * The library never picks a logging framework for you. By default nothing is
 * logged ([MqttLogger.None]). Provide an adapter for whatever your app uses:
 *
 * ```kotlin
 * // Timber (Android)
 * object TimberMqttLogger : MqttLogger {
 *     override fun debug(message: String) = Timber.d(message)
 *     override fun info(message: String) = Timber.i(message)
 *     override fun warn(message: String, error: Throwable?) = Timber.w(error, message)
 *     override fun error(message: String, error: Throwable?) = Timber.e(error, message)
 * }
 *
 * HiveMqttClient(config, logger = TimberMqttLogger)
 * ```
 */
interface MqttLogger {
    fun debug(message: String)
    fun info(message: String)
    fun warn(message: String, error: Throwable? = null)
    fun error(message: String, error: Throwable? = null)

    /** Default logger: discards everything. */
    object None : MqttLogger {
        override fun debug(message: String) = Unit
        override fun info(message: String) = Unit
        override fun warn(message: String, error: Throwable?) = Unit
        override fun error(message: String, error: Throwable?) = Unit
    }

    /** Simple stdout logger, handy for JVM tools and integration tests. */
    object Stdout : MqttLogger {
        override fun debug(message: String) = println("[MQTT] D $message")
        override fun info(message: String) = println("[MQTT] I $message")
        override fun warn(message: String, error: Throwable?) =
            println("[MQTT] W $message${error?.let { " (${it.message})" } ?: ""}")
        override fun error(message: String, error: Throwable?) =
            println("[MQTT] E $message${error?.let { " (${it.message})" } ?: ""}")
    }
}
