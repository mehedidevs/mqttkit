package io.github.mehedidevs.mqttkit

import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class HiveMqttClientTest {

    /**
     * Regression test: subscribe flows must complete when disconnect() is
     * called. Previously they stayed open forever on a client that would
     * never emit again.
     */
    @Test
    fun disconnectCompletesOpenSubscribeFlows() = runBlocking {
        val client = HiveMqttClient(
            MqttConfig(host = "localhost", clientId = "test-${UUID.randomUUID()}")
        )

        val collector = async {
            client.subscribe("sensors/test").toList()
        }
        delay(200) // let the collector register its subscription

        client.disconnect()

        // Without the fix this times out because the flow never completes.
        val messages = withTimeout(2_000) { collector.await() }
        assertTrue(messages.isEmpty())
    }
}
