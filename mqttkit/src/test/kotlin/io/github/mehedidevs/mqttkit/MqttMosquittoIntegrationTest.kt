package io.github.mehedidevs.mqttkit

import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.UUID

class MqttMosquittoIntegrationTest {

    @Test
    fun publishAndSubscribeThroughLocalMosquitto() {
        assumeTrue(
            "Set RUN_MQTT_INTEGRATION_TESTS=true to run Mosquitto integration tests.",
            System.getenv("RUN_MQTT_INTEGRATION_TESTS") == "true"
        )

        runBlocking {
            val host = System.getenv("MQTT_TEST_BROKER_HOST") ?: "127.0.0.1"
            val port = System.getenv("MQTT_TEST_BROKER_PORT")?.toIntOrNull() ?: 1883
            val clientId = "core-mqtt-test-${UUID.randomUUID()}"
            val topic = "core-mqtt/tests/${UUID.randomUUID()}"
            val payload = "integration-${UUID.randomUUID()}"

            val sessionManager = MqttSessionManager { config -> HiveMqttClient(config) }
            val client = sessionManager.start(
                MqttConfig(
                    host = host,
                    port = port,
                    clientId = clientId,
                    cleanStart = true,
                    automaticReconnect = false
                )
            )

            try {
                withTimeout(10_000) {
                    val received = async {
                        client.subscribe(topic, qos = 1)
                            .first { it.payloadAsString == payload }
                    }

                    delay(300)

                    client.publish(
                        MqttMessage(
                            topic = topic,
                            payload = payload.toByteArray(),
                            qos = 1
                        )
                    ).getOrThrow()

                    received.await()
                }
            } finally {
                sessionManager.stop()
            }
        }
    }
}
