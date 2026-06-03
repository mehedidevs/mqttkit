# MQTT Production Base Module

This project contains a reusable Android MQTT module plus a demo Compose app.

- `core-mqtt`: production-ready MQTT transport module built on HiveMQ MQTT 5.
- `app`: demo UI showing connection, publish, subscribe, foreground service, LWT, and reconnect behavior.

For full module documentation, see [core-mqtt/USER_MANUAL.md](core-mqtt/USER_MANUAL.md).

License: MIT. See [LICENSE](LICENSE).

Public API review notes are in [API_REVIEW.md](API_REVIEW.md).

## What Was Cleaned

Generated and machine-local files were removed from the project:

- `.gradle/`, `.idea/`, `.kotlin/`
- all `build/` folders
- `local.properties`
- `java_pid3433.hprof`
- accidental brace-named directories from failed shell expansion

The project now includes a root `.gitignore` so these do not come back.

## Reusable Module

Add the module to another Android project:

```kotlin
include(":core-mqtt")
```

Then depend on it:

```kotlin
implementation(project(":core-mqtt"))
```

## Basic Usage

```kotlin
val sessionManager = MqttSessionManager { config ->
    HiveMqttClient(config)
}

val client = sessionManager.start(
    MqttConfig(
        host = "broker.example.com",
        port = 8883,
        clientId = "device-123",
        useTls = true,
        auth = MqttAuth.Basic(
            username = "mqtt-user",
            password = "mqtt-password"
        )
    )
)
```

Change the URL by changing `host`, `port`, and `useTls`.

## Public Test Brokers

The demo app has one-tap presets for these no-auth public MQTT brokers:

| Broker | Host | Port | TLS |
| --- | --- | --- | --- |
| HiveMQ Public Broker | `broker.hivemq.com` | `1883` | No |
| EMQX Public Broker | `broker.emqx.io` | `1883` | No |
| Eclipse Mosquitto Test Server | `test.mosquitto.org` | `1883` | No |
| EMQX Public Broker TLS | `broker.emqx.io` | `8883` | Yes |

Public brokers are for testing only. Do not send secrets or production data through them.

## Authentication Options

No auth:

```kotlin
auth = MqttAuth.None
```

Username/password:

```kotlin
auth = MqttAuth.Basic(username = "user", password = "secret")
```

Token-style brokers:

```kotlin
auth = MqttAuth.Token(token = accessToken)
```

## Typed Response Data Classes

For JSON payloads, mark your response model with `@Serializable` and give the extension only the type you want:

```kotlin
@Serializable
data class OrderUpdate(
    val id: String,
    val status: String
)

client.subscribeJson<OrderUpdate>("orders/+").collect { result ->
    result
        .onSuccess { typed ->
            val order: OrderUpdate = typed.value
        }
        .onFailure { error ->
            println(error.message)
        }
}
```

For one raw MQTT message:

```kotlin
val result: Result<OrderUpdate> = message.decodePayloadAsJson<OrderUpdate>()
```

For publishing typed JSON:

```kotlin
client.publishJson(
    topic = "orders/123",
    value = OrderUpdate(id = "123", status = "accepted"),
    qos = 1,
    retained = false
)
```

If decoding fails, the error includes the topic, target type, payload preview, and original parser reason. For non-JSON payloads, use `MqttPayloadCodec`.

## Production Features In `core-mqtt`

- MQTT 5 HiveMQ async client.
- TLS switch through `MqttConfig.useTls`.
- `MqttAuth.None`, `MqttAuth.Basic`, and `MqttAuth.Token`.
- Last Will and Testament support.
- Persistent sessions through `cleanStart` and `sessionExpiryIntervalSec`.
- Automatic reconnect with bounded exponential backoff.
- Initial connection fallback endpoints.
- Connect and publish retry helpers.
- Manual-disconnect guard so intentional disconnects do not reconnect.
- Re-subscription after reconnect.
- Shared broker subscription per topic filter with fan-out to multiple collectors.
- Raw and typed publish/subscribe APIs.
- Config and message validation.
- `MqttSessionManager` for app-level lifecycle ownership.

## Demo App Notes

The demo app uses Koin, but `core-mqtt` does not depend on Koin. The app wires the module in:

```kotlin
factory<(MqttConfig) -> MqttClient> { { cfg -> HiveMqttClient(cfg) } }
single { MqttSessionManager(get()) }
```

This keeps the MQTT module portable across projects using Koin, Hilt, manual DI, or no DI framework.

## Build

Because `local.properties` is intentionally ignored, set `ANDROID_HOME` or let Android Studio generate `local.properties`.

```bash
ANDROID_HOME=/path/to/Android/sdk ./gradlew :core-mqtt:testDebugUnitTest :app:assembleDebug
```
