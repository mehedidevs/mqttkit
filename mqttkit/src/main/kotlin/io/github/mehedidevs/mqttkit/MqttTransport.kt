package io.github.mehedidevs.mqttkit

import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.TrustManagerFactory

/**
 * Custom TLS settings applied to every endpoint with `useTls = true`.
 *
 * Leave fields null to use the JVM/platform defaults. Typical uses:
 *  - [trustManagerFactory]: trust a private/self-signed broker CA.
 *  - [keyManagerFactory]: present a client certificate (mutual TLS), the
 *    standard setup for AWS IoT Core and hardened Mosquitto deployments.
 *
 * Example — trust a custom CA:
 * ```kotlin
 * val trustStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
 *     load(null)
 *     setCertificateEntry("broker-ca", brokerCaCertificate)
 * }
 * val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
 *     .apply { init(trustStore) }
 *
 * MqttConfig(..., useTls = true, tlsConfig = MqttTlsConfig(trustManagerFactory = tmf))
 * ```
 */
data class MqttTlsConfig(
    /** Supplies the client certificate/key for mutual TLS. Null = no client cert. */
    val keyManagerFactory: KeyManagerFactory? = null,
    /** Decides which broker certificates to trust. Null = platform default CAs. */
    val trustManagerFactory: TrustManagerFactory? = null,
    /** TLS protocol versions to allow, e.g. `listOf("TLSv1.3", "TLSv1.2")`. Null = defaults. */
    val protocols: List<String>? = null,
    /** Cipher suites to allow. Null = platform defaults. */
    val cipherSuites: List<String>? = null
)

/**
 * Enables MQTT-over-WebSocket transport for an endpoint.
 *
 * Use this for brokers only reachable through HTTP infrastructure (port
 * 80/443, corporate proxies, load balancers). Combine with `useTls = true`
 * for `wss://`.
 *
 * ```kotlin
 * // wss://broker.example.com:443/mqtt
 * MqttBrokerEndpoint(
 *     host = "broker.example.com",
 *     port = 443,
 *     useTls = true,
 *     webSocket = MqttWebSocketConfig()
 * )
 * ```
 */
data class MqttWebSocketConfig(
    /** HTTP path of the broker's WebSocket listener. Most brokers use "mqtt". */
    val serverPath: String = "mqtt",
    /** WebSocket subprotocol; brokers expect "mqtt" per the MQTT spec. */
    val subprotocol: String = "mqtt"
)
