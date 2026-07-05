package io.github.mqttdemo.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mqttdemo.ui.theme.CyanPrimary
import io.github.mqttdemo.ui.theme.NavyLight
import io.github.mqttdemo.ui.theme.NavyMid
import io.github.mqttdemo.ui.theme.OnNavy
import io.github.mqttdemo.ui.theme.OnNavySub
import io.github.mqttdemo.ui.theme.Qos0Color
import io.github.mqttdemo.ui.theme.Qos1Color
import io.github.mqttdemo.ui.theme.Qos2Color

private data class Concept(
    val emoji: String,
    val title: String,
    val summary: String,
    val body: String
)

private val CONCEPTS = listOf(
    Concept(
        emoji = "📡",
        title = "What is MQTT?",
        summary = "Lightweight publish/subscribe protocol designed for constrained IoT devices.",
        body = """
MQTT (Message Queuing Telemetry Transport) is a lightweight messaging protocol
based on the publish-subscribe pattern. It was invented in 1999 by Andy Stanford-Clark
and Arlen Nipper for monitoring oil pipelines via satellite — where bandwidth was
expensive and connectivity was unreliable.

Today it's the de-facto standard for IoT: smart home sensors, industrial machines,
vehicle telemetry, mobile push notifications (Facebook Messenger used it!).

Key advantages over HTTP:
• Tiny packet overhead (~2 byte minimum header vs HTTP's ~700 byte minimum)
• Persistent connections — no reconnect per message
• Built-in QoS, retain, and LWT
• Scales to millions of devices with the right broker
        """.trimIndent()
    ),
    Concept(
        emoji = "🏗️",
        title = "Brokers, Clients & Topics",
        summary = "The broker is the central hub. Clients publish to topics; others subscribe.",
        body = """
BROKER — the server that routes all messages. It never stores business logic,
only routes and optionally persists messages. Popular brokers:
  • HiveMQ (commercial, cloud, free public test)
  • Mosquitto (open-source, lightweight, Raspberry Pi-friendly)
  • EMQX (high-scale, Kubernetes-native)
  • AWS IoT Core, Google Cloud IoT, Azure IoT Hub (managed)

CLIENT — any device or service that connects to the broker. A client can be:
  • Publisher: sends PUBLISH packets to a topic
  • Subscriber: sends SUBSCRIBE packets, receives matching PUBLISH packets
  • Both simultaneously — most real devices do both

TOPIC — a UTF-8 string that acts as the message address:
  home/living-room/temperature
  home/living-room/humidity
  factory/line-2/machine-5/rpm

Topic levels are separated by /. The broker matches topics to subscribers —
no point-to-point addressing, just topic-based routing.
        """.trimIndent()
    ),
    Concept(
        emoji = "🎯",
        title = "Quality of Service (QoS)",
        summary = "Three levels trade delivery guarantees against network overhead.",
        body = """
QoS 0 — AT MOST ONCE (fire and forget)
  • Publisher sends once. No ACK. Message may be lost.
  • Best for: frequent sensor readings where losing one is fine.
  • Overhead: 1 packet

QoS 1 — AT LEAST ONCE (acknowledged)
  • Publisher sends until it gets PUBACK. May deliver duplicates.
  • Receiver must handle idempotency (process duplicate safely).
  • Best for: commands, alerts where loss is unacceptable.
  • Overhead: 2 packets minimum

QoS 2 — EXACTLY ONCE (four-way handshake)
  • PUBLISH → PUBREC → PUBREL → PUBCOMP
  • Guaranteed exactly one delivery. Highest latency and overhead.
  • Best for: billing events, financial transactions, critical state changes.
  • Overhead: 4 packets minimum

⚠️ The QoS in subscribe() acts as a ceiling — if you subscribe at QoS 1
and the publisher sent QoS 2, you'll receive at QoS 1. Always match QoS
to the sensitivity of the data, not the network quality.
        """.trimIndent()
    ),
    Concept(
        emoji = "📌",
        title = "Retained Messages",
        summary = "The broker caches the last value. New subscribers get it instantly.",
        body = """
When a publisher sets retained = true, the broker stores that message as
the "last known value" for that topic.

When a NEW subscriber subscribes to a matching topic, the broker immediately
delivers the retained message — even if the publisher is offline.

Classic use cases:
  • Device status:  topic = "devices/sensor-1/online"
                    payload = "true"    retained = true
  → Any new subscriber instantly knows if the device is online.

  • Configuration:  topic = "devices/sensor-1/config"
                    payload = {"interval":5000}  retained = true
  → Device reads its config immediately on subscribe.

  • Last reading:   topic = "home/garage/temperature"
                    payload = "18.5"    retained = true
  → Dashboard shows current temp without waiting for next publish.

Only ONE retained message exists per topic at any time. Publishing a new
retained message replaces the previous one. Sending an empty payload with
retained = true clears the retained message.
        """.trimIndent()
    ),
    Concept(
        emoji = "🪦",
        title = "Last Will & Testament (LWT)",
        summary = "The broker publishes your goodbye message if you disconnect ungracefully.",
        body = """
LWT is configured at connect time — you tell the broker: "If I disappear
without sending a clean DISCONNECT, publish THIS message to THAT topic."

How it triggers:
  ✓ Network drop (TCP connection lost)
  ✓ App crash
  ✓ Battery pulled
  ✓ Keep-alive timeout exceeded
  ✗ Clean disconnect (DISCONNECT packet sent) — LWT is NOT sent

The will message:
  will_topic   = "devices/{id}/status"
  will_payload = {"status": "offline"}
  will_qos     = 1
  will_retain  = true    ← combine with retain for "last known status" pattern

This creates a presence pattern:
  1. On connect: publish {"status":"online"} retained to status topic
  2. LWT: {"status":"offline"} retained to status topic
  → Subscribers always see the current online/offline state of any device.

In this app: our will message is set to the client's status topic with
"offline" payload retained, and we manually publish "online" after connect.
        """.trimIndent()
    ),
    Concept(
        emoji = "🔄",
        title = "Clean Start & Persistent Sessions",
        summary = "Sessions let the broker remember your subscriptions and queue missed messages.",
        body = """
CLEAN START = true (default for most clients)
  • Broker discards any previous session state on connect.
  • Subscriptions and queued messages from a previous connection are gone.
  • Simple, stateless — reconnect = start fresh.

CLEAN START = false (persistent session)
  • Broker remembers the session identified by clientId.
  • Subscriptions persist across disconnects.
  • QoS 1 and QoS 2 messages queued while offline are delivered on reconnect.
  • sessionExpiryInterval controls how long the broker keeps the session.

When to use persistent sessions:
  • Mobile devices that go in/out of coverage — don't miss commands.
  • Constrained devices — don't re-subscribe on every reconnect.
  • Any scenario where offline message delivery matters.

⚠️ clientId must be consistent across connections for sessions to work.
    If clientId changes, the broker creates a new session.
    This is why we generate a stable UUID per install, not per launch.
        """.trimIndent()
    ),
    Concept(
        emoji = "💓",
        title = "Keep-Alive",
        summary = "Prevents zombie connections by requiring periodic PINGREQ packets.",
        body = """
MQTT runs over TCP. TCP doesn't detect dead connections quickly — a dropped
connection might not be noticed for minutes (or hours!).

Keep-alive solves this: the client must send *some* packet (PUBLISH,
SUBSCRIBE, PINGREQ) at least once every [keepAlive] seconds. If the client
is idle, it sends a PINGREQ. The broker responds with PINGRESP.

If the broker doesn't hear from a client for 1.5× keepAlive seconds, it
closes the connection and triggers the LWT.

Guidelines:
  • Too short (< 5s) → excessive traffic on mobile networks, battery drain
  • Too long (> 60s) → slow detection of dead connections
  • Sweet spot: 30–60 seconds for most mobile/IoT applications

HiveMQ client handles this automatically — you just set the interval in
MqttConfig.keepAliveSec and the library sends PINGREQs as needed.
        """.trimIndent()
    ),
    Concept(
        emoji = "🔒",
        title = "Security",
        summary = "TLS + credentials + ACLs = production-grade security.",
        body = """
Layer 1 — Transport Security (TLS/SSL)
  • Port 8883 (MQTTS) instead of 1883 (plain MQTT)
  • Encrypts all traffic — topics, payloads, credentials
  • Client certificates for mutual TLS (mTLS) — device identity
  • In HiveMQ client: useTls = true in MqttConfig

Layer 2 — Authentication
  • Username/password (CONNECT packet)
  • JWT tokens (passed as password)
  • X.509 client certificates (mTLS)

Layer 3 — Authorization (ACLs)
  • Topic-level permissions: device A can only publish to its own topic
  • Broker-enforced: HiveMQ, EMQX, Mosquitto all support ACLs
  • Example: client "device-001" → PUBLISH allowed on "devices/device-001/#"
                               → SUBSCRIBE allowed on "config/device-001/#"

Production checklist:
  ☐ Enable TLS (port 8883)
  ☐ Use certificate-based auth or strong passwords
  ☐ Configure ACLs per device/role
  ☐ Never use broker.hivemq.com or test.mosquitto.org in production!
  ☐ Rotate credentials / certificates regularly
        """.trimIndent()
    ),
    Concept(
        emoji = "🔌",
        title = "Reconnect Strategy",
        summary = "Exponential back-off avoids thundering herds when the broker recovers.",
        body = """
When a client drops, all the devices it serves may try to reconnect simultaneously
— overwhelming the broker with thousands of CONNECT packets. This is the
"thundering herd" problem.

Solution: exponential back-off with jitter

attempt 1 → wait 1 s
attempt 2 → wait 2 s
attempt 3 → wait 4 s
attempt 4 → wait 8 s
attempt 5 → wait 16 s
attempt 6+ → wait 30 s (capped)

In HiveMqttClient:
  delay = min(initialDelay × 2^attempt, maxDelay)

Jitter (random ±20%) prevents synchronized reconnects.
HiveMQ client handles this automatically via ctx.reconnector.delay().

After reconnect, subscriptions must be re-applied. This app fixes the common
bug where HiveMQ's built-in resubscribeIfSessionPresent misses subscriptions
after session expiry by always re-subscribing in addConnectedListener.
        """.trimIndent()
    ),
)

@Composable
fun LearnPanel() {
    var expandedIndex by remember { mutableStateOf<Int?>(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("MQTT Guide", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = OnNavy)
        Text(
            "Tap any section to expand. Concepts are ordered from basics to advanced.",
            color = OnNavySub, fontSize = 12.sp, lineHeight = 17.sp
        )

        Spacer(Modifier.height(4.dp))

        CONCEPTS.forEachIndexed { index, concept ->
            ConceptCard(
                concept  = concept,
                expanded = expandedIndex == index,
                onClick  = { expandedIndex = if (expandedIndex == index) null else index }
            )
        }

        Spacer(Modifier.height(24.dp))

        // ── Quick reference table ─────────────────────────────────────────
        SectionCard(title = "QoS Comparison Table") {
            QosTable()
        }

        SectionCard(title = "Broker Ports Reference") {
            listOf(
                "1883" to "MQTT plain TCP (no encryption)",
                "8883" to "MQTT over TLS/SSL",
                "8083" to "MQTT over WebSocket",
                "8084" to "MQTT over WebSocket + TLS",
                "8080" to "HiveMQ WebSocket (varies by broker)",
            ).forEach { (port, desc) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        port,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = CyanPrimary,
                        modifier = Modifier.width(48.dp)
                    )
                    Text(desc, color = OnNavySub, fontSize = 12.sp, modifier = Modifier.weight(1f))
                }
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun ConceptCard(concept: Concept, expanded: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(12.dp),
        color    = NavyMid
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onClick)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(concept.emoji, fontSize = 22.sp)
                    Column {
                        Text(
                            concept.title,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            color = OnNavy
                        )
                        Text(
                            concept.summary,
                            fontSize = 12.sp,
                            color = OnNavySub,
                            lineHeight = 16.sp
                        )
                    }
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = CyanPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit  = shrinkVertically()
            ) {
                Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                    HorizontalDivider(color = NavyLight, modifier = Modifier.padding(bottom = 12.dp))
                    Text(
                        concept.body,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = OnNavy,
                        lineHeight = 18.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun QosTable() {
    val headers = listOf("Level", "Name", "Packets", "Duplicates?", "Best For")
    val rows = listOf(
        listOf("0", "At most once", "1", "Never", "Telemetry"),
        listOf("1", "At least once", "2+", "Possible", "Commands"),
        listOf("2", "Exactly once", "4", "Never", "Billing"),
    )
    val colors = listOf(Qos0Color, Qos1Color, Qos2Color)

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            headers.forEach { h ->
                Text(h, color = CyanPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f))
            }
        }
        HorizontalDivider(color = NavyLight)
        rows.forEachIndexed { i, row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                row.forEachIndexed { j, cell ->
                    Text(
                        cell,
                        color = if (j == 0) colors[i] else OnNavy,
                        fontSize = 10.sp,
                        fontFamily = if (j < 2) FontFamily.Default else FontFamily.Monospace,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
