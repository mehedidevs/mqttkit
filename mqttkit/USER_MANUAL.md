# Core MQTT User Manual

This manual explains how to use the `core-mqtt` module as a production-ready base MQTT module in Android projects.

The module is intentionally small:

- It connects to an MQTT broker.
- It publishes raw or typed messages.
- It subscribes to topics and returns Kotlin `Flow`s.
- It handles reconnect and re-subscribe behavior.
- It supports no-auth, username/password auth, token auth, TLS, Last Will and Testament, and persistent sessions.

The module does not force a specific app architecture. You can use it with Koin, Hilt, manual dependency injection, MVVM, MVI, services, repositories, or workers.

## Module Files

| File | Purpose |
| --- | --- |
| `MqttClient.kt` | Main client interface used by your app. |
| `HiveMqttClient.kt` | MQTT 5 implementation using HiveMQ client. |
| `MqttConfig.kt` | Broker URL, port, TLS, auth, reconnect, session, and LWT settings. |
| `MqttAuth.kt` | Auth models: no auth, username/password, token. |
| `MqttMessage.kt` | Raw MQTT message model. |
| `MqttPayloadCodec.kt` | Typed payload encode/decode helpers for your own data classes. |
| `MqttConnectionState.kt` | Connection state model. |
| `MqttSessionManager.kt` | App-level lifecycle owner for one active MQTT session. |

## Installation

Add the module to your project settings:

```kotlin
include(":core-mqtt")
```

Add the dependency to your app module:

```kotlin
dependencies {
    implementation(project(":core-mqtt"))
}
```

The app that uses this module must declare internet permission:

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

If you keep MQTT alive in the background with a foreground service, your app module also needs foreground service permissions and service declarations. The demo app shows one working example.

## Quick Start

Create a session manager:

```kotlin
val sessionManager = MqttSessionManager { config ->
    HiveMqttClient(config)
}
```

Connect:

```kotlin
val client = sessionManager.start(
    MqttConfig(
        host = "broker.emqx.io",
        port = 1883,
        clientId = "android-${System.currentTimeMillis()}",
        auth = MqttAuth.None,
        useTls = false
    )
)
```

Publish:

```kotlin
client.publish(
    MqttMessage(
        topic = "demo/device/status",
        payload = "online".toByteArray(),
        qos = 1,
        retained = true
    )
)
```

Subscribe:

```kotlin
client.subscribe("demo/device/#").collect { message ->
    println("Topic: ${message.topic}")
    println("Payload: ${message.payloadAsString}")
}
```

Disconnect:

```kotlin
sessionManager.stop()
```

## Public Brokers For Testing

These are public brokers for development and experiments only. Do not send passwords, tokens, private data, or production traffic through them.

| Broker | Host | Port | TLS | Auth |
| --- | --- | --- | --- | --- |
| HiveMQ Public Broker | `broker.hivemq.com` | `1883` | No | None |
| EMQX Public Broker | `broker.emqx.io` | `1883` | No | None |
| Eclipse Mosquitto Test Server | `test.mosquitto.org` | `1883` | No | None |
| EMQX Public Broker TLS | `broker.emqx.io` | `8883` | Yes | None |

Example:

```kotlin
MqttConfig(
    host = "broker.hivemq.com",
    port = 1883,
    clientId = "test-client-123",
    auth = MqttAuth.None,
    useTls = false
)
```

## Configuration

`MqttConfig` controls how the client connects.

```kotlin
MqttConfig(
    host = "broker.example.com",
    port = 8883,
    clientId = "device-123",
    auth = MqttAuth.Basic("user", "password"),
    useTls = true,
    fallbackEndpoints = emptyList(),
    cleanStart = false,
    sessionExpiryIntervalSec = 3600,
    keepAliveSec = 30,
    automaticReconnect = true,
    initialReconnectDelayMs = 1_000,
    maxReconnectDelayMs = 30_000,
    willMessage = null
)
```

### Required Fields

`host`: Broker host name or IP address.

Examples:

```kotlin
host = "broker.example.com"
host = "192.168.1.50"
```

`port`: Broker TCP port.

Common values:

| Port | Meaning |
| --- | --- |
| `1883` | MQTT over plain TCP. |
| `8883` | MQTT over TLS. |

`clientId`: Unique ID for this client connection.

Important: two live clients using the same client ID can disconnect each other. For mobile apps, include a user ID, device ID, or generated suffix.

Good examples:

```kotlin
clientId = "driver-42-phone"
clientId = "device-$deviceId"
clientId = "mqttdemo-${UUID.randomUUID()}"
```

### TLS

Use TLS when connecting to a real production broker:

```kotlin
useTls = true
port = 8883
```

The current module uses the platform/default TLS trust configuration. For private certificate authorities, certificate pinning, custom trust stores, or mutual TLS, extend `HiveMqttClient` where it builds `MqttClientSslConfig`.

### Fallback Endpoints

Use fallback endpoints when you have more than one broker endpoint for the same MQTT backend.

Example:

```kotlin
val config = MqttConfig(
    host = "mqtt-primary.example.com",
    port = 8883,
    useTls = true,
    clientId = "device-123",
    auth = MqttAuth.Token(token = accessToken),
    fallbackEndpoints = listOf(
        MqttBrokerEndpoint(
            host = "mqtt-backup-1.example.com",
            port = 8883,
            useTls = true
        ),
        MqttBrokerEndpoint(
            host = "mqtt-backup-2.example.com",
            port = 8883,
            useTls = true
        )
    )
)
```

Initial connect behavior:

1. Try the primary `host`, `port`, and `useTls`.
2. If that fails, try each `fallbackEndpoints` entry in order.
3. If all endpoints fail, throw one error that includes every attempted endpoint.

Important: fallback endpoints should represent the same logical broker system. Do not fall back from your private production broker to a public test broker because that could leak data.

Do not put the same URL in fallback. The module rejects duplicate endpoints, including a fallback endpoint that matches the primary `host`, `port`, and `useTls`. If you want to try the same URL again, use `startWithRetry`.

## Authentication

Authentication is configured with `MqttAuth`.

### No Auth

Use this for public test brokers or local brokers without credentials:

```kotlin
auth = MqttAuth.None
```

### Username And Password

Use this for normal broker credentials:

```kotlin
auth = MqttAuth.Basic(
    username = "mqtt-user",
    password = "mqtt-password"
)
```

### Token Auth

Some brokers expect an access token as the password:

```kotlin
auth = MqttAuth.Token(
    username = "token",
    token = accessToken
)
```

If your broker expects a different token username, pass it:

```kotlin
auth = MqttAuth.Token(
    username = "jwt",
    token = jwtToken
)
```

## Session Behavior

MQTT sessions decide whether the broker remembers your subscriptions and queued messages.

### Clean Start

```kotlin
cleanStart = false
```

Recommended for many production mobile/IoT flows. The broker can keep session state after disconnect.

```kotlin
cleanStart = true
```

Useful for simple demos or stateless clients. The broker discards old session state on connect.

### Session Expiry

```kotlin
sessionExpiryIntervalSec = 3600
```

This means the broker keeps session state for one hour after disconnect.

Use a longer value when the client may go offline and still needs queued QoS 1 or QoS 2 messages.

## Keep Alive

```kotlin
keepAliveSec = 30
```

The client sends a ping when idle so the broker knows the connection is still alive. `30` seconds is a practical default for many mobile apps.

Use lower values if you need faster offline detection. Use higher values if battery/network usage matters more than quick detection.

## Last Will And Testament

Last Will and Testament, or LWT, is a message the broker publishes if the client disappears without a clean disconnect.

Example:

```kotlin
MqttConfig(
    host = "broker.example.com",
    clientId = "device-123",
    willMessage = MqttWillMessage(
        topic = "devices/device-123/status",
        payload = """{"status":"offline"}""",
        qos = 1,
        retained = true
    )
)
```

Common pattern:

1. Configure LWT as `offline`.
2. After successful connect, publish retained `online`.
3. Before manual disconnect, publish retained `offline`.

This gives subscribers a reliable online/offline status.

## Connection State

Observe `connectionState`:

```kotlin
viewModelScope.launch {
    client.connectionState.collect { state ->
        when (state) {
            MqttConnectionState.Idle -> Unit
            MqttConnectionState.Connecting -> Unit
            MqttConnectionState.Connected -> Unit
            is MqttConnectionState.Reconnecting -> Unit
            is MqttConnectionState.Disconnected -> Unit
            is MqttConnectionState.Failed -> Unit
        }
    }
}
```

States:

| State | Meaning |
| --- | --- |
| `Idle` | Client has not connected yet. |
| `Connecting` | Initial connection is in progress. |
| `Connected` | MQTT handshake completed. |
| `Reconnecting` | Network/broker disconnected and auto-reconnect is active. |
| `Disconnected` | Client is disconnected. |
| `Failed` | Initial connect failed. |

## Reconnect Behavior

When `automaticReconnect = true`, the client asks HiveMQ to reconnect after unexpected disconnects.

Backoff is controlled by:

```kotlin
initialReconnectDelayMs = 1_000
maxReconnectDelayMs = 30_000
```

The module also tracks active subscriptions and re-applies them after reconnect. This matters because broker-side persistent sessions are not always enough, especially after broker restarts, network changes, or session expiry.

Manual disconnect is guarded. When you call:

```kotlin
sessionManager.stop()
```

or:

```kotlin
client.disconnect()
```

the client does not auto-reconnect.

## Retry Helpers

Fallback endpoints help when the initial broker endpoint is unavailable. Retry helpers help when an operation fails temporarily.

Use both together for a professional setup:

- `fallbackEndpoints` for different broker hosts in the same cluster.
- `MqttRetryPolicy` for repeated attempts when the same endpoint may recover.
- `automaticReconnect = true` for unexpected disconnects after a successful connection.
- `sessionManager.stop()` or `client.disconnect()` only for intentional user/service disconnect.

Default retry policy:

```kotlin
MqttRetryPolicy(
    maxAttempts = 3,
    initialDelayMs = 500,
    maxDelayMs = 5_000,
    backoffMultiplier = 2.0
)
```

Connect with retry:

```kotlin
val result: Result<MqttClient> = sessionManager.startWithRetry(
    config = config,
    retryPolicy = MqttRetryPolicy(
        maxAttempts = 3,
        initialDelayMs = 1_000,
        maxDelayMs = 10_000
    )
)

result
    .onSuccess { client -> println("Connected") }
    .onFailure { error -> println(error.message) }
```

The demo app uses this pattern: the user can disconnect manually from the UI/service, but network failures and temporary startup failures are retried automatically.

Publish with retry:

```kotlin
client.publishWithRetry(
    message = MqttMessage(
        topic = "devices/device-123/status",
        payload = "online".toByteArray(),
        qos = 1,
        retained = true
    )
)
```

Publish typed JSON with retry:

```kotlin
client.publishJsonWithRetry(
    topic = "devices/device-123/status",
    value = DeviceStatus(deviceId = "device-123", online = true),
    qos = 1,
    retained = true
)
```

Use retries carefully. Retrying QoS 1 publishes can still create duplicate business events if the broker received the first publish but the client did not receive the acknowledgement. For commands, payments, and state changes, include an event ID and make server-side processing idempotent.

## Publishing Raw Messages

Use `MqttMessage` for raw payloads:

```kotlin
val result = client.publish(
    MqttMessage(
        topic = "orders/123/status",
        payload = "accepted".toByteArray(),
        qos = 1,
        retained = false
    )
)

result
    .onSuccess { println("Published") }
    .onFailure { error -> println("Publish failed: ${error.message}") }
```

`publish()` returns `Result<Unit>` so app code can handle failures without a required `try/catch`.

### QoS

| QoS | Name | Meaning | Typical Use |
| --- | --- | --- | --- |
| `0` | At most once | Fire and forget. Can be lost. | High-frequency telemetry. |
| `1` | At least once | Delivered one or more times. Duplicates possible. | Commands, alerts, normal events. |
| `2` | Exactly once | Highest guarantee, more overhead. | Critical workflows. |

Use QoS 1 as the default for most business events.

### Retained Messages

```kotlin
retained = true
```

A retained message is stored by the broker as the latest value for a topic. New subscribers receive it immediately.

Good uses:

- Device online/offline status.
- Last known sensor value.
- Current configuration.

Avoid retained messages for event streams where old events should not replay to new subscribers.

## Subscribing To Raw Messages

```kotlin
val job = viewModelScope.launch {
    client.subscribe("orders/+/status", qos = 1).collect { message ->
        println(message.topic)
        println(message.payloadAsString)
    }
}
```

Cancel the collection to unsubscribe:

```kotlin
job.cancel()
```

The module shares one broker subscription for the same topic filter. If multiple screens collect `orders/+/status`, the broker receives one subscription and the module fans messages out to all collectors.

## Topic Design

MQTT topics are simple strings split by `/`.

Good topic examples:

```text
devices/{deviceId}/status
devices/{deviceId}/telemetry/temperature
users/{userId}/orders/{orderId}/status
fleet/{fleetId}/vehicles/{vehicleId}/location
```

Avoid:

```text
status
data
test
device
```

Short generic topics collide easily on shared brokers.

### Wildcards

`+` matches exactly one topic level:

```text
devices/+/status
```

Matches:

```text
devices/device-1/status
devices/device-2/status
```

`#` matches zero or more levels:

```text
devices/device-1/#
```

Matches:

```text
devices/device-1/status
devices/device-1/telemetry/temperature
devices/device-1/telemetry/humidity
```

## Typed Data Classes

The module includes two typed payload styles:

- Generic JSON extensions for normal JSON data classes.
- Manual codecs for custom formats such as protobuf, encrypted payloads, or pipe-separated strings.

### Generic JSON Conversion

Use this when the MQTT payload is JSON and you want to convert it directly to a data class by only giving the target type.

Your data class must be marked with `@Serializable`:

```kotlin
import kotlinx.serialization.Serializable

@Serializable
data class OrderUpdate(
    val id: String,
    val status: String
)
```

Decode one raw message:

```kotlin
val result: Result<OrderUpdate> =
    message.decodePayloadAsJson<OrderUpdate>()

result
    .onSuccess { update ->
        println(update.status)
    }
    .onFailure { error ->
        println(error.message)
    }
```

Subscribe and receive typed results:

```kotlin
client.subscribeJson<OrderUpdate>("orders/+/status").collect { result ->
    result
        .onSuccess { typed ->
            val update: OrderUpdate = typed.value
            println("Order ${update.id}: ${update.status}")
        }
        .onFailure { error ->
            println(error.message)
        }
}
```

If decoding fails, the error is `MqttPayloadDecodeException`. It includes:

- MQTT topic
- target Kotlin type
- safe payload preview
- original serialization error

Example error message:

```text
Failed to decode MQTT payload on topic 'orders/123/status' as com.example.OrderUpdate. Payload preview: '{"id":123,"status":false}'. Reason: ...
```

If you prefer exceptions instead of `Result`, use:

```kotlin
val update: OrderUpdate = message.requirePayloadAsJson<OrderUpdate>()
```

or:

```kotlin
client.subscribeJsonOrThrow<OrderUpdate>("orders/+/status").collect { typed ->
    println(typed.value)
}
```

Publish a JSON data class:

```kotlin
client.publishJson(
    topic = "orders/123/status",
    value = OrderUpdate(id = "123", status = "accepted"),
    qos = 1,
    retained = false
)
```

The default JSON parser is `DefaultMqttJson`:

```kotlin
val DefaultMqttJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    explicitNulls = false
    encodeDefaults = true
}
```

You can pass your own `Json` instance:

```kotlin
val strictJson = Json {
    ignoreUnknownKeys = false
}

message.decodePayloadAsJson<OrderUpdate>(strictJson)
```

### Manual Codec Conversion

Use `MqttPayloadCodec` when your payload is not JSON or you need full control.

```kotlin
val orderCodec = object : MqttPayloadCodec<OrderUpdate> {
    override fun encode(value: OrderUpdate): ByteArray {
        return "${value.id}|${value.status}".toByteArray()
    }

    override fun decode(message: MqttMessage): OrderUpdate {
        val parts = message.payloadAsString.split("|")
        return OrderUpdate(parts[0], parts[1])
    }
}
```

Subscribe with the codec:

```kotlin
client.subscribe("orders/+/status", orderCodec).collect { typedMessage ->
    val update: OrderUpdate = typedMessage.value
    println(update.status)
}
```

Publish with the codec:

```kotlin
client.publish(
    topic = "orders/123/status",
    value = OrderUpdate("123", "accepted"),
    codec = orderCodec,
    qos = 1,
    retained = false
)
```

## Session Manager

`MqttSessionManager` owns one active client.

```kotlin
class MqttRepository(
    private val sessionManager: MqttSessionManager
) {
    suspend fun connect(config: MqttConfig): MqttClient {
        return sessionManager.start(config)
    }

    suspend fun disconnect() {
        sessionManager.stop()
    }

    fun currentClient(): MqttClient? {
        return sessionManager.current()
    }
}
```

When `start()` is called while a client already exists, the old client is disconnected first. This prevents old subscriptions from leaking into a new user/session.

## Dependency Injection

### Manual DI

```kotlin
val sessionManager = MqttSessionManager { config ->
    HiveMqttClient(config)
}
```

### Koin

```kotlin
val mqttModule = module {
    factory<(MqttConfig) -> MqttClient> {
        { config -> HiveMqttClient(config) }
    }
    single { MqttSessionManager(get()) }
}
```

### Hilt

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object MqttModule {

    @Provides
    fun provideMqttClientFactory(): (MqttConfig) -> MqttClient {
        return { config -> HiveMqttClient(config) }
    }

    @Provides
    @Singleton
    fun provideMqttSessionManager(
        factory: (MqttConfig) -> MqttClient
    ): MqttSessionManager {
        return MqttSessionManager(factory)
    }
}
```

## ViewModel Example

```kotlin
class OrdersViewModel(
    private val sessionManager: MqttSessionManager
) : ViewModel() {

    private var subscriptionJob: Job? = null

    fun connect() {
        viewModelScope.launch {
            val client = sessionManager.start(
                MqttConfig(
                    host = "broker.example.com",
                    port = 8883,
                    clientId = "orders-app-user-42",
                    useTls = true,
                    auth = MqttAuth.Token(token = "access-token")
                )
            )

            subscriptionJob = launch {
                client.subscribe("orders/+/status").collect { message ->
                    println(message.payloadAsString)
                }
            }
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            subscriptionJob?.cancel()
            sessionManager.stop()
        }
    }
}
```

## Foreground Service Pattern

For mobile apps, a foreground service may be needed if you want to keep MQTT alive while the app is backgrounded.

Recommended pattern:

1. Keep `MqttSessionManager` in application scope.
2. Start the foreground service after connect.
3. The service observes `sessionManager.current()?.connectionState`.
4. Stop the service after manual disconnect.
5. Do not disconnect in `ViewModel.onCleared()` if the service should keep MQTT alive.

The demo app includes an example service implementation.

### How To Use From A Foreground Service

Keep the actual MQTT connection in `MqttSessionManager`, then let the service start, observe, and stop that session.

Minimal service shape:

```kotlin
class AppMqttForegroundService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Inject this with Hilt/Koin or pass it from your app singleton.
    private lateinit var sessionManager: MqttSessionManager

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                startForeground(NOTIFICATION_ID, buildNotification("Connecting"))
                connectAndObserve()
            }

            ACTION_STOP -> {
                serviceScope.launch {
                    sessionManager.stop()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }

        return START_STICKY
    }

    private fun connectAndObserve() {
        serviceScope.launch {
            val client = sessionManager.start(
                MqttConfig(
                    host = "broker.example.com",
                    port = 8883,
                    clientId = "device-123",
                    useTls = true,
                    auth = MqttAuth.Token(token = "access-token")
                )
            )

            launch {
                client.connectionState.collect { state ->
                    updateNotification(state.toNotificationText())
                }
            }

            launch {
                client.subscribeJson<DeviceStatus>("devices/device-123/status")
                    .collect { result ->
                        result
                            .onSuccess { typed -> handleStatus(typed.value) }
                            .onFailure { error -> logDecodeError(error) }
                    }
            }
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "app.mqtt.START"
        const val ACTION_STOP = "app.mqtt.STOP"
        const val NOTIFICATION_ID = 1001
    }
}
```

Start the service from your ViewModel or Activity:

```kotlin
val intent = Intent(context, AppMqttForegroundService::class.java)
    .setAction(AppMqttForegroundService.ACTION_START)

ContextCompat.startForegroundService(context, intent)
```

Stop it:

```kotlin
val intent = Intent(context, AppMqttForegroundService::class.java)
    .setAction(AppMqttForegroundService.ACTION_STOP)

context.startService(intent)
```

Important foreground-service rules:

- Call `startForeground(...)` quickly after the service starts.
- Keep `MqttSessionManager` app-scoped so Activity recreation does not kill MQTT.
- Stop MQTT on logout or manual disconnect.
- Do not hardcode tokens inside the service; fetch them from secure app state.
- On Android 13+, request notification permission before showing the foreground notification.
- On Android 14+, declare the correct foreground service type permission and manifest type.

Manifest example:

```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

<service
    android:name=".AppMqttForegroundService"
    android:exported="false"
    android:foregroundServiceType="dataSync" />
```

## Error Handling

Connection errors:

```kotlin
try {
    sessionManager.start(config)
} catch (error: Throwable) {
    println("Connection failed: ${error.message}")
}
```

Publish errors:

```kotlin
client.publish(message)
    .onFailure { error ->
        println("Publish failed: ${error.message}")
    }
```

Subscribe decode errors:

```kotlin
client.subscribe("orders/+", decoder)
    .catch { error ->
        println("Subscription failed: ${error.message}")
    }
    .collect { typed ->
        println(typed.value)
    }
```

## Testing

Run module tests:

```bash
./gradlew :core-mqtt:testDebugUnitTest
```

Run app build:

```bash
./gradlew :app:assembleDebug
```

If you do not have `local.properties`, set `ANDROID_HOME`:

```bash
ANDROID_HOME=/path/to/Android/sdk ./gradlew :core-mqtt:testDebugUnitTest :app:assembleDebug
```

## Production Checklist

Before using this in a real app:

- Use a private broker, not a public test broker.
- Use TLS, normally port `8883`.
- Configure fallback endpoints for your own broker cluster if available.
- Use unique client IDs.
- Add broker-side ACLs so clients can only access allowed topics.
- Do not hardcode real passwords or tokens in source code.
- Store tokens securely.
- Decide `cleanStart` and `sessionExpiryIntervalSec` based on your delivery needs.
- Use QoS 1 for important messages.
- Use retry helpers for transient connection/publish failures.
- Use retained messages only for latest-state topics.
- Add integration tests against your broker.
- Monitor connection state and reconnect behavior.
- Handle Android foreground service and notification permissions if keeping MQTT alive in background.

## Common Problems

### Client Keeps Disconnecting

Check:

- Another client may be using the same `clientId`.
- Broker ACL may reject the connection.
- Host/port/TLS combination may be wrong.
- Mobile network may be unstable.

### Cannot Connect To TLS Broker

Check:

- `useTls = true`.
- Port is usually `8883`.
- Broker certificate is trusted by Android.
- Some brokers require username/password or token auth.

### Messages Arrive More Than Once

QoS 1 can deliver duplicates. Your app should make command/event processing idempotent when duplicates matter.

### Subscriber Does Not Receive Old Message

Only retained messages are delivered immediately to new subscribers. Normal messages are only delivered to active subscriptions.

### Public Broker Works But Private Broker Does Not

Check private broker firewall, DNS, TLS certificate, username/password, ACL, and whether MQTT 5 is enabled.

## Recommended Base Integration

For most apps:

1. Put `MqttSessionManager` in application scope.
2. Create one repository for MQTT operations.
3. Keep data class parsing in app-specific codecs.
4. Keep topic names in one app-specific topic builder.
5. Observe `connectionState` from UI.
6. Connect after login or app startup.
7. Disconnect on logout.

Example topic builder:

```kotlin
class MqttTopics(
    private val userId: String
) {
    fun orderStatus(orderId: String) = "users/$userId/orders/$orderId/status"
    fun allOrderStatuses() = "users/$userId/orders/+/status"
}
```

This keeps the reusable MQTT module generic while your app controls business-specific URLs, topics, auth, and response data classes.
