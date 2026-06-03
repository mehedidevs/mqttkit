# Public API Review

This file records public API naming decisions before publishing `core-mqtt`.

## Stable Names To Keep

| API | Decision |
| --- | --- |
| `MqttClient` | Stable. Clear main transport contract. |
| `HiveMqttClient` | Stable for now. Names the concrete HiveMQ-backed implementation. |
| `MqttConfig` | Stable. Configuration object for one MQTT session. |
| `MqttBrokerEndpoint` | Stable. Clear meaning for primary/fallback endpoints. |
| `MqttAuth` | Stable. Simple auth model with `None`, `Basic`, `Token`. |
| `MqttMessage` | Stable. Raw publish/subscribe message model. |
| `MqttConnectionState` | Stable. Readable state model for UI/services. |
| `MqttSessionManager` | Stable. Owns one active session. |
| `MqttPayloadCodec` / `MqttPayloadDecoder` | Stable. Useful for non-JSON payloads. |
| `MqttTypedMessage` | Stable. Wraps decoded value plus raw metadata. |
| `MqttRetryPolicy` | Stable. Explicit retry configuration. |

## Stable Extension Names

| API | Decision |
| --- | --- |
| `publishJson` | Stable. Encodes and publishes JSON payloads. |
| `publishJsonWithRetry` | Stable. JSON publish plus retry policy. |
| `decodePayloadAsJson` | Stable. Returns `Result<T>` for safe decoding. |
| `requirePayloadAsJson` | Stable. Throws on decode failure. |
| `subscribeJson` | Stable. Emits `Result<MqttTypedMessage<T>>`. |
| `subscribeJsonOrThrow` | Stable. Throws on decode failure. |
| `publishWithRetry` | Stable. Raw publish retry helper. |
| `startWithRetry` | Stable. Initial connect retry helper. |

## Internalized Helpers

The following helpers are intentionally not part of the public API:

- `retryResult`
- `decodeError`
- `previewForError`

## Names To Revisit Before Maven Central

The package is still `com.food.core.mqtt`. For a public library, rename it to a neutral namespace such as:

```text
io.github.<owner>.mqttkit
```

Do this before first public release. Renaming after public release is a breaking change.

## Versioning Guidance

Start with `0.1.0` while the API is still settling. Move to `1.0.0` only after:

- Package namespace is final.
- Artifact coordinates are final.
- Integration tests pass in CI.
- Public docs are complete.
- Binary/source compatibility expectations are documented.
