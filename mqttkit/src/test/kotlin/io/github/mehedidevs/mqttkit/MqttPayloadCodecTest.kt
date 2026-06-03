package io.github.mehedidevs.mqttkit

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class MqttPayloadCodecTest {

    data class OrderUpdate(val id: String, val status: String)

    private val orderCodec = object : MqttPayloadCodec<OrderUpdate> {
        override fun encode(value: OrderUpdate): ByteArray =
            "${value.id}|${value.status}".toByteArray()

        override fun decode(message: MqttMessage): OrderUpdate {
            val parts = message.payloadAsString.split("|")
            return OrderUpdate(id = parts[0], status = parts[1])
        }
    }

    @Test
    fun typedPublishUsesCodec() = runBlocking {
        val client = RecordingClient()

        client.publish(
            topic = "orders/1",
            value = OrderUpdate("1", "accepted"),
            codec = orderCodec,
            qos = 1,
            retained = true
        )

        val message = client.lastPublished
        requireNotNull(message)
        assertEquals("orders/1", message.topic)
        assertEquals("1|accepted", message.payloadAsString)
        assertEquals(1, message.qos)
        assertEquals(true, message.retained)
    }

    @Test
    fun typedSubscribeUsesDecoder() = runBlocking {
        val client = RecordingClient(
            inbound = MqttMessage(
                topic = "orders/1",
                payload = "1|ready".toByteArray()
            )
        )

        val typed = client.subscribe("orders/+", orderCodec).single()

        assertEquals("orders/1", typed.topic)
        assertEquals(OrderUpdate("1", "ready"), typed.value)
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
