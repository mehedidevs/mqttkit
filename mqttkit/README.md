# MqttKit

Coroutines-first MQTT 5 client for Kotlin — Android apps, Ktor/Spring backends,
desktop, and CLI tools. Backed by the HiveMQ MQTT client, hidden behind a small
`MqttClient` interface.

**Features:** Flow-based subscribe with shared broker subscriptions ·
automatic re-subscribe after reconnect · endpoint failover · exponential
backoff · Last Will & Testament · typed JSON pub/sub (kotlinx.serialization) ·
retry helpers · mutual TLS · WebSocket transport · pluggable logging ·
configurable backpressure.

Deep reference: [USER_MANUAL.md](USER_MANUAL.md). Working Android app:
[`../app`](../app).

---

## 1. Install

```kotlin
// After `./gradlew :mqttkit:publishToMavenLocal` (or a JitPack/Maven Central release):
dependencies {
    implementation("io.github.mehedidevs:mqttkit:0.1.0")
}

// The typed JSON helpers need the serialization plugin in YOUR module:
plugins {
    kotlin("plugin.serialization") version "<your-kotlin-version>"
}
```

Android apps also need:

```xml
<uses-permission android:name="android.permission.INTERNET" />
```

## 2. Five-minute quick start (works on plain JVM)

```kotlin
import io.github.mehedidevs.mqttkit.*
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class Telemetry(val deviceId: String, val temperature: Double, val ts: Long)

fun main() = runBlocking {
    val client = HiveMqttClient(
        MqttConfig(
            host = "test.mosquitto.org",          // public test broker
            clientId = "quickstart-${UUID.randomUUID()}"
        ),
        logger = MqttLogger.Stdout
    )

    client.connect()

    val collector = launch {
        client.subscribeJson<Telemetry>("mqttkit/demo/telemetry").collect { result ->
            result
                .onSuccess { println("got ${it.value} on ${it.topic}") }
                .onFailure { println("bad payload: ${it.message}") }
        }
    }

    client.publishJson(
        topic = "mqttkit/demo/telemetry",
        value = Telemetry("dev-1", 21.5, System.currentTimeMillis())
    )

    delay(2_000)
    client.disconnect()   // completes the subscribe flow; collector ends cleanly
    collector.join()
}
```

## 3. Full implementation (Android)

A realistic setup: session lifecycle bound to login/logout, retained presence
via LWT, typed telemetry in, typed commands out, UI observing everything
through a ViewModel.

### 3.1 Domain models

```kotlin
import kotlinx.serialization.Serializable

@Serializable
data class Telemetry(val deviceId: String, val temperature: Double, val humidity: Double, val ts: Long)

@Serializable
data class Command(val action: String, val value: Double? = null)

@Serializable
data class Presence(val online: Boolean)
```

### 3.2 Logger bridge (the kit logs nothing by default)

```kotlin
import io.github.mehedidevs.mqttkit.MqttLogger
import timber.log.Timber

object TimberMqttLogger : MqttLogger {
    override fun debug(message: String) = Timber.d(message)
    override fun info(message: String) = Timber.i(message)
    override fun warn(message: String, error: Throwable?) = Timber.w(error, message)
    override fun error(message: String, error: Throwable?) = Timber.e(error, message)
}
```

### 3.3 Repository — the only class that touches MQTT

```kotlin
import io.github.mehedidevs.mqttkit.*
import kotlinx.coroutines.flow.*

class DeviceRepository(
    private val sessionManager: MqttSessionManager
) {
    /** Null until connect() succeeds. */
    val connectionState: StateFlow<MqttConnectionState>?
        get() = sessionManager.current()?.connectionState

    suspend fun connect(userId: String, auth: MqttAuth): Result<MqttClient> {
        val clientId = "app-$userId"
        val statusTopic = "clients/$clientId/status"

        return sessionManager.startWithRetry(
            config = MqttConfig(
                host = "broker.example.com",
                port = 8883,
                clientId = clientId,
                auth = auth,                       // MqttAuth.Basic(...) or .Token(...)
                useTls = true,
                // tlsConfig = MqttTlsConfig(trustManagerFactory = tmf), // private CA / mTLS

                // If 8883 is blocked (hotel wifi, corporate proxy), fall back to WSS on 443:
                fallbackEndpoints = listOf(
                    MqttBrokerEndpoint(
                        host = "broker.example.com",
                        port = 443,
                        useTls = true,
                        webSocket = MqttWebSocketConfig()
                    )
                ),

                // Broker announces us offline if the app dies without a clean DISCONNECT:
                willMessage = MqttWillMessage(
                    topic = statusTopic,
                    payload = """{"online":false}""",
                    retained = true
                )
            ),
            retryPolicy = MqttRetryPolicy(maxAttempts = 3, initialDelayMs = 500)
        ).onSuccess { client ->
            // Counterpart of the LWT: retained "online" marker.
            client.publishJson(statusTopic, Presence(online = true), retained = true)
        }
    }

    /** Live telemetry for one device. Bad payloads are skipped, not fatal. */
    fun telemetry(deviceId: String): Flow<Telemetry> {
        val client = sessionManager.current() ?: return emptyFlow()
        return client.subscribeJson<Telemetry>("devices/$deviceId/telemetry")
            .mapNotNull { it.getOrNull()?.value }
    }

    /** Telemetry for ALL devices — one wildcard subscription. */
    fun allTelemetry(): Flow<Telemetry> {
        val client = sessionManager.current() ?: return emptyFlow()
        return client.subscribeJson<Telemetry>("devices/+/telemetry")
            .mapNotNull { it.getOrNull()?.value }
    }

    suspend fun sendCommand(deviceId: String, command: Command): Result<Unit> {
        val client = sessionManager.current()
            ?: return Result.failure(IllegalStateException("MQTT session not started"))
        return client.publishJsonWithRetry("devices/$deviceId/commands", command, qos = 1)
    }

    /** Call on logout so the next user doesn't inherit this session's subscriptions. */
    suspend fun disconnect() = sessionManager.stop()
}
```

### 3.4 Dependency injection (Koin shown; Hilt is analogous)

```kotlin
import io.github.mehedidevs.mqttkit.HiveMqttClient
import io.github.mehedidevs.mqttkit.MqttSessionManager
import org.koin.dsl.module

val mqttModule = module {
    // App-scoped: the connection survives screen rotations and ViewModel recreation.
    single {
        MqttSessionManager(logger = TimberMqttLogger) { cfg ->
            HiveMqttClient(cfg, logger = TimberMqttLogger)
        }
    }
    single { DeviceRepository(get()) }
    viewModel { DashboardViewModel(get()) }
}
```

### 3.5 ViewModel

```kotlin
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.mehedidevs.mqttkit.MqttAuth
import io.github.mehedidevs.mqttkit.MqttConnectionState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class DashboardUiState(
    val connection: MqttConnectionState = MqttConnectionState.Idle,
    val latest: Map<String, Telemetry> = emptyMap(),   // deviceId → newest reading
    val error: String? = null
)

class DashboardViewModel(private val repo: DeviceRepository) : ViewModel() {

    private val _ui = MutableStateFlow(DashboardUiState())
    val ui: StateFlow<DashboardUiState> = _ui.asStateFlow()

    private var telemetryJob: Job? = null

    fun connect(userId: String, username: String, password: String) {
        viewModelScope.launch {
            repo.connect(userId, MqttAuth.Basic(username, password))
                .onSuccess {
                    observeConnection()
                    observeTelemetry()
                }
                .onFailure { e -> _ui.update { it.copy(error = e.message) } }
        }
    }

    private fun observeConnection() {
        viewModelScope.launch {
            repo.connectionState?.collect { state ->
                _ui.update { it.copy(connection = state) }
            }
        }
    }

    private fun observeTelemetry() {
        telemetryJob?.cancel()   // cancelling the collector unsubscribes at the broker
        telemetryJob = viewModelScope.launch {
            repo.allTelemetry().collect { t ->
                _ui.update { it.copy(latest = it.latest + (t.deviceId to t)) }
            }
        }
    }

    fun setTargetTemperature(deviceId: String, celsius: Double) {
        viewModelScope.launch {
            repo.sendCommand(deviceId, Command("set_temperature", celsius))
                .onFailure { e -> _ui.update { it.copy(error = "Command failed: ${e.message}") } }
        }
    }

    fun logout() {
        viewModelScope.launch { repo.disconnect() }
        // disconnect() completes all open subscribe flows, so collectors end cleanly.
    }
}
```

### 3.6 Compose UI (sketch)

```kotlin
@Composable
fun DashboardScreen(viewModel: DashboardViewModel = koinViewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()

    when (val c = ui.connection) {
        is MqttConnectionState.Connected    -> StatusChip("Online")
        is MqttConnectionState.Reconnecting -> StatusChip("Reconnecting (#${c.attempt})…")
        is MqttConnectionState.Failed       -> StatusChip("Failed: ${c.cause.message}")
        else                                -> StatusChip("Offline")
    }

    LazyColumn {
        items(ui.latest.values.toList(), key = { it.deviceId }) { t ->
            DeviceCard(t, onSetTemp = { viewModel.setTargetTemperature(t.deviceId, it) })
        }
    }
}
```

### 3.7 Keeping the connection alive in background

If MQTT must outlive the UI (chat, tracking, alerts), hold the session in a
foreground service. See
[`../app/src/main/kotlin/io/github/mqttdemo/service/MqttForegroundService.kt`](../app/src/main/kotlin/io/github/mqttdemo/service/MqttForegroundService.kt)
for a complete example. If MQTT is only needed while the app is visible,
skip the service and call `repo.disconnect()` when the last screen goes away.

## 4. Backend / CLI (Ktor, Spring, plain JVM)

Same library, no Android anywhere:

```kotlin
// Ktor: start the session with the application, stop it on shutdown.
fun Application.configureMqtt() {
    val sessionManager = MqttSessionManager(logger = MqttLogger.Stdout) { cfg ->
        HiveMqttClient(cfg, MqttLogger.Stdout)
    }

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    scope.launch {
        sessionManager.startWithRetry(
            MqttConfig(
                host = System.getenv("MQTT_HOST") ?: "localhost",
                clientId = "backend-${UUID.randomUUID()}",
                auth = MqttAuth.Token(System.getenv("MQTT_TOKEN") ?: "")
            )
        ).onSuccess { client ->
            client.subscribeJson<Telemetry>("devices/+/telemetry").collect { result ->
                result.onSuccess { msg -> /* persist msg.value, alert, etc. */ }
            }
        }
    }

    monitor.subscribe(ApplicationStopping) {
        runBlocking { sessionManager.stop() }
        scope.cancel()
    }
}
```

## 5. Testing your code without a broker

`MqttClient` is an interface — hand your repository a fake:

```kotlin
class FakeMqttClient : MqttClient {
    private val _state = MutableStateFlow<MqttConnectionState>(MqttConnectionState.Idle)
    override val connectionState: StateFlow<MqttConnectionState> = _state.asStateFlow()

    private val incoming = MutableSharedFlow<MqttMessage>(extraBufferCapacity = 64)
    val published = mutableListOf<MqttMessage>()

    override suspend fun connect() { _state.value = MqttConnectionState.Connected }
    override suspend fun disconnect() { _state.value = MqttConnectionState.Disconnected("manual") }

    override suspend fun publish(message: MqttMessage): Result<Unit> {
        published += message
        return Result.success(Unit)
    }

    // Exact-match only; extend if your tests need wildcard topics.
    override fun subscribe(topic: String, qos: Int): Flow<MqttMessage> =
        incoming.filter { it.topic == topic }

    suspend fun emit(topic: String, payload: String) =
        incoming.emit(MqttMessage(topic, payload.toByteArray()))
}

@Test
fun repositoryParsesTelemetry() = runBlocking {
    val fake = FakeMqttClient()
    val sessionManager = MqttSessionManager { fake }
    sessionManager.start(MqttConfig(host = "unused", clientId = "test"))

    val repo = DeviceRepository(sessionManager)
    val first = async { repo.telemetry("dev-1").first() }

    fake.emit("devices/dev-1/telemetry", """{"deviceId":"dev-1","temperature":21.5,"humidity":40.0,"ts":1}""")

    assertEquals(21.5, first.await().temperature, 0.001)
}
```

## 6. API cheat sheet

| You want to… | Call |
| --- | --- |
| Connect with retry + failover | `sessionManager.startWithRetry(config, retryPolicy)` |
| Watch connection state | `client.connectionState` (StateFlow) |
| Receive typed JSON | `client.subscribeJson<T>(topic)` → `Flow<Result<MqttTypedMessage<T>>>` |
| Send typed JSON | `client.publishJson(topic, value, qos, retained)` |
| Send with retry | `client.publishJsonWithRetry(...)` |
| Raw bytes | `client.subscribe(topic)` / `client.publish(MqttMessage(...))` |
| Non-JSON payloads (protobuf, …) | implement `MqttPayloadCodec<T>`, use `publish/subscribe(topic, codec)` |
| Announce offline on crash | `MqttConfig(willMessage = MqttWillMessage(...))` |
| Mutual TLS / private CA | `MqttConfig(tlsConfig = MqttTlsConfig(...))` |
| Broker behind port 443 | `MqttBrokerEndpoint(..., webSocket = MqttWebSocketConfig())` |
| Slow-collector policy | `MqttConfig(subscribeBufferSize, subscribeOverflow)` |
| Log through your framework | implement `MqttLogger`, pass to `HiveMqttClient` / `MqttSessionManager` |
| End the session (logout) | `sessionManager.stop()` — completes all open subscribe flows |
