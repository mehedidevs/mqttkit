package io.github.mqttdemo.di

import io.github.mehedidevs.mqttkit.MqttLogger
import timber.log.Timber

/**
 * Bridges the mqttkit logging hook to Timber. The kit itself has no logging
 * dependency; each app decides where MQTT logs go.
 */
object TimberMqttLogger : MqttLogger {
    override fun debug(message: String) = Timber.d(message)
    override fun info(message: String) = Timber.i(message)
    override fun warn(message: String, error: Throwable?) = Timber.w(error, message)
    override fun error(message: String, error: Throwable?) = Timber.e(error, message)
}
