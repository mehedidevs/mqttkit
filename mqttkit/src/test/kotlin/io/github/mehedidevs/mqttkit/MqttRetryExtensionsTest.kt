package io.github.mehedidevs.mqttkit

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MqttRetryExtensionsTest {

    @Test
    fun publishWithRetrySucceedsAfterTransientFailure() = runBlocking {
        val client = FlakyPublishClient(failuresBeforeSuccess = 1)

        val result = client.publishWithRetry(
            message = MqttMessage("test/topic", "hello".toByteArray()),
            retryPolicy = MqttRetryPolicy(maxAttempts = 2, initialDelayMs = 0)
        )

        assertTrue(result.isSuccess)
        assertEquals(2, client.publishAttempts)
    }

    @Test
    fun publishWithRetryReturnsHelpfulFailureAfterAllAttempts() = runBlocking {
        val client = FlakyPublishClient(failuresBeforeSuccess = 10)

        val result = client.publishWithRetry(
            message = MqttMessage("test/topic", "hello".toByteArray()),
            retryPolicy = MqttRetryPolicy(maxAttempts = 3, initialDelayMs = 0)
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("3 attempt"))
        assertEquals(3, client.publishAttempts)
    }

    private class FlakyPublishClient(
        private val failuresBeforeSuccess: Int
    ) : MqttClient {
        override val connectionState = MutableStateFlow<MqttConnectionState>(MqttConnectionState.Idle)
        var publishAttempts = 0

        override suspend fun connect() = Unit
        override suspend fun disconnect() = Unit

        override suspend fun publish(message: MqttMessage): Result<Unit> {
            publishAttempts++
            return if (publishAttempts <= failuresBeforeSuccess) {
                Result.failure(IllegalStateException("temporary publish failure"))
            } else {
                Result.success(Unit)
            }
        }

        override fun subscribe(topic: String, qos: Int) = emptyFlow<MqttMessage>()
    }
}
