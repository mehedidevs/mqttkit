package io.github.mehedidevs.mqttkit

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

/**
 * Manages the lifecycle of an [MqttClient] session.
 *
 * Intended use:
 *  - Call [start] once the user logs in / the app connects.
 *  - Call [stop] on logout, so subscriptions from the previous user
 *    don't leak into the next session.
 *  - Call [current] anywhere to get the live client without re-connecting.
 *
 * The [Mutex] prevents double-connect races when [start] is called
 * from multiple coroutines simultaneously.
 */
class MqttSessionManager(
    private val clientFactory: (MqttConfig) -> MqttClient
) {
    private val mutex = Mutex()
    private var client: MqttClient? = null

    /**
     * Creates a fresh [MqttClient] with [config] and connects to the broker.
     * If a session is already active it is stopped cleanly first.
     *
     * @return the connected [MqttClient] — keep it or use [current].
     */
    suspend fun start(config: MqttConfig): MqttClient = mutex.withLock {
        if (client != null) {
            Timber.w("MqttSessionManager.start: stopping existing session first")
            client?.disconnect()
        }
        val c = clientFactory(config)
        c.connect()
        client = c
        Timber.i("MqttSessionManager: session started (host=${config.host}:${config.port})")
        c
    }

    /** Disconnect and clear the active client. Safe to call if already stopped. */
    suspend fun stop() = mutex.withLock {
        client?.disconnect()
        client = null
        Timber.i("MqttSessionManager: session stopped")
    }

    /**
     * Returns the currently connected [MqttClient], or null if no session
     * is active. Use this to reconnect to an existing session after an
     * Activity/ViewModel is recreated.
     */
    fun current(): MqttClient? = client
}
