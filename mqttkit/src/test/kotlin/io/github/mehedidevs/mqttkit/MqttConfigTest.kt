package io.github.mehedidevs.mqttkit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MqttConfigTest {

    @Test
    fun validConfigStoresAuth() {
        val config = MqttConfig(
            host = "broker.example.com",
            clientId = "client-1",
            auth = MqttAuth.Basic("user", "secret"),
            useTls = true,
            fallbackEndpoints = listOf(
                MqttBrokerEndpoint("backup.example.com", port = 8883, useTls = true)
            )
        )

        assertEquals("broker.example.com", config.host)
        assertTrue(config.auth is MqttAuth.Basic)
        assertEquals(2, config.endpoints.size)
        assertEquals("backup.example.com", config.endpoints[1].host)
    }

    @Test(expected = IllegalArgumentException::class)
    fun blankHostIsRejected() {
        MqttConfig(host = "", clientId = "client-1")
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidPortIsRejected() {
        MqttConfig(host = "broker.example.com", port = 0, clientId = "client-1")
    }

    @Test(expected = IllegalArgumentException::class)
    fun duplicateFallbackEndpointsAreRejected() {
        MqttConfig(
            host = "broker.example.com",
            clientId = "client-1",
            fallbackEndpoints = listOf(
                MqttBrokerEndpoint("backup.example.com"),
                MqttBrokerEndpoint("backup.example.com")
            )
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun fallbackMatchingPrimaryEndpointIsRejected() {
        MqttConfig(
            host = "broker.example.com",
            port = 1883,
            clientId = "client-1",
            fallbackEndpoints = listOf(
                MqttBrokerEndpoint("broker.example.com", port = 1883, useTls = false)
            )
        )
    }

    @Suppress("DEPRECATION")
    @Test
    fun legacyUsernamePasswordConstructorMapsToBasicAuth() {
        val config = MqttConfig(
            host = "broker.example.com",
            clientId = "client-1",
            username = "user",
            password = "secret"
        )

        val auth = config.auth as MqttAuth.Basic
        assertEquals("user", auth.username)
        assertEquals("secret", auth.password)
    }
}
