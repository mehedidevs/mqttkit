package io.github.mehedidevs.mqttkit

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MqttJsonExtensionsTest {

    @Serializable
    data class DeviceStatus(
        val deviceId: String,
        val online: Boolean
    )

    @Test
    fun decodesPayloadAsDesiredType() {
        val message = MqttMessage(
            topic = "devices/1/status",
            payload = """{"deviceId":"1","online":true}""".toByteArray()
        )

        val result = message.decodePayloadAsJson<DeviceStatus>()

        assertEquals(DeviceStatus("1", true), result.getOrThrow())
    }

    @Test
    fun decodeFailureIncludesUsefulMessage() {
        val message = MqttMessage(
            topic = "devices/1/status",
            payload = """{"deviceId": 1, "online": "wrong"}""".toByteArray()
        )

        val error = message.decodePayloadAsJson<DeviceStatus>().exceptionOrNull()

        require(error is MqttPayloadDecodeException)
        assertEquals("devices/1/status", error.topic)
        assertTrue(error.message.orEmpty().contains("DeviceStatus"))
        assertTrue(error.message.orEmpty().contains("devices/1/status"))
        assertTrue(error.message.orEmpty().contains("Payload preview"))
    }

    @Test
    fun subscribeJsonReturnsTypedResult() = runBlocking {
        val client = RecordingClient(
            inbound = MqttMessage(
                topic = "devices/1/status",
                payload = """{"deviceId":"1","online":false}""".toByteArray()
            )
        )

        val typed = client.subscribeJson<DeviceStatus>("devices/+/status").single().getOrThrow()

        assertEquals("devices/1/status", typed.topic)
        assertEquals(DeviceStatus("1", false), typed.value)
    }

    @Test
    fun publishJsonEncodesValue() = runBlocking {
        val client = RecordingClient()

        client.publishJson(
            topic = "devices/1/status",
            value = DeviceStatus("1", true),
            retained = true
        )

        val message = requireNotNull(client.lastPublished)
        assertEquals("devices/1/status", message.topic)
        assertTrue(message.payloadAsString.contains(""""deviceId":"1""""))
        assertTrue(message.payloadAsString.contains(""""online":true"""))
        assertEquals(true, message.retained)
    }

    private class RecordingClient(
        private val inbound: MqttMessage? = null
    ) : MqttClient {
        override val connectionState = MutableStateFlow<MqttConnectionState>(MqttConnectionState.Idle)
        var lastPublished: MqttMessage? = null

        override suspend fun connect() = Unit
        override suspend fun disconnect() = Unit

        override suspend fun publish(message: MqttMessage): Result<Unit> {
            lastPublished = message
            return Result.success(Unit)
        }

        override fun subscribe(topic: String, qos: Int) = flowOf(requireNotNull(inbound))
    }
}
