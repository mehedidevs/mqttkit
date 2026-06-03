package io.github.mqttdemo.domain

import java.time.Instant

/** Direction of an MQTT message from the app's perspective. */
enum class MessageDirection { INBOUND, OUTBOUND }

/**
 * A single entry in the message log — either a message we received or one
 * we published, plus metadata for display.
 */
data class LogEntry(
    val id: Long = System.nanoTime(),
    val topic: String,
    val payload: String,
    val qos: Int,
    val retained: Boolean,
    val direction: MessageDirection,
    val timestamp: Instant = Instant.now()
)

/** Live reading from a simulated IoT sensor, displayed on the Dashboard tab. */
data class SensorReading(
    val temperature: Float = 24.0f,   // °C
    val humidity: Float = 62.0f,       // %RH
    val pressure: Float = 1013.0f,     // hPa
    val lightLevel: Int = 420          // lux
)
