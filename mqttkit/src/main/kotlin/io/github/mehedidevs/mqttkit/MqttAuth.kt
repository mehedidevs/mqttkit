package io.github.mehedidevs.mqttkit

/**
 * Authentication options for MQTT simple auth.
 *
 * Most MQTT brokers accept username/password. Token-based brokers usually pass
 * the token as the password with a broker-specific username.
 */
sealed interface MqttAuth {
    /** Connect without MQTT simple auth. Useful for local or public test brokers only. */
    data object None : MqttAuth

    /** Username/password MQTT simple auth. Do not hardcode production secrets in source. */
    data class Basic(
        val username: String,
        val password: String
    ) : MqttAuth

    /** Token-as-password auth for brokers that accept JWT/API-token style credentials. */
    data class Token(
        val token: String,
        val username: String = "token"
    ) : MqttAuth
}
