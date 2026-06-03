package io.github.mqttdemo.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.food.core.mqtt.MqttConnectionState
import com.food.core.mqtt.MqttSessionManager
import com.food.mqttdemo.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import timber.log.Timber

/**
 * Foreground Service — keeps the MQTT client alive even when:
 *  - The user swipes the app away from recents
 *  - The Activity is fully destroyed
 *  - The OS tries to reclaim memory
 *
 * The service posts an ongoing notification (required by Android to run in the
 * foreground). This prevents the process from being killed by the system while
 * the MQTT session is active.
 *
 * ── Why not a bound service? ──────────────────────────────────────────────────
 * A bound service is destroyed when the last binder disconnects. If the Activity
 * is swiped away, we'd lose the connection. A started foreground service lives
 * until explicitly stopped — which is what we want for persistent IoT data flow.
 *
 * ── Integration ───────────────────────────────────────────────────────────────
 * Start via:  startService(Intent(context, MqttForegroundService::class.java).setAction(ACTION_START))
 * Stop via:   startService(Intent(context, MqttForegroundService::class.java).setAction(ACTION_STOP))
 *
 * The [MqttSessionManager] is a Koin singleton shared with the ViewModel — no IPC needed.
 */
class MqttForegroundService : Service() {

    companion object {
        const val ACTION_START = "com.food.mqttdemo.START_MQTT"
        const val ACTION_STOP  = "com.food.mqttdemo.STOP_MQTT"

        private const val CHANNEL_ID   = "mqtt_connection_channel"
        private const val NOTIFICATION_ID = 1001
    }

    private val sessionManager: MqttSessionManager by inject()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                Timber.i("MqttForegroundService: starting")
                startForeground(NOTIFICATION_ID, buildNotification("Connecting…"))
                observeConnectionState()
            }
            ACTION_STOP -> {
                Timber.i("MqttForegroundService: stopping")
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        // START_STICKY: if the OS kills the service, it will restart it with a null intent
        return START_STICKY
    }

    /** Watch the MQTT state and update the notification text accordingly. */
    private fun observeConnectionState() {
        serviceScope.launch {
            sessionManager.current()?.connectionState?.collect { state ->
                val text = when (state) {
                    is MqttConnectionState.Connected       -> "Connected to broker"
                    is MqttConnectionState.Connecting      -> "Connecting…"
                    is MqttConnectionState.Reconnecting    -> "Reconnecting… (attempt ${state.attempt})"
                    is MqttConnectionState.Disconnected    -> "Disconnected"
                    is MqttConnectionState.Failed          -> "Connection failed"
                    is MqttConnectionState.Idle            -> "Idle"
                }
                updateNotification(text)
            }
        }
    }

    private fun buildNotification(contentText: String): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MQTT Demo")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_menu_share)   // replace with your own icon
            .setOngoing(true)
            .setContentIntent(openAppIntent)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "MQTT Connection",
                NotificationManager.IMPORTANCE_LOW   // no sound — it's a status notification
            ).apply {
                description = "Shows the live MQTT connection status"
                setShowBadge(false)
            }
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null  // not a bound service

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        Timber.i("MqttForegroundService destroyed")
    }
}
