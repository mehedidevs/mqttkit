package io.github.mqttdemo.ui.theme

import androidx.compose.ui.graphics.Color

// ── Dark IoT dashboard palette ────────────────────────────────────────────────
val NavyDeep    = Color(0xFF0A1628)   // deepest background
val NavyMid     = Color(0xFF112240)   // card / surface
val NavyLight   = Color(0xFF1A3460)   // elevated card
val CyanPrimary = Color(0xFF00E5FF)   // primary accent
val CyanLight   = Color(0xFF80FFFF)   // light variant
val BlueAccent  = Color(0xFF40C4FF)   // secondary accent

// ── Semantic colours ──────────────────────────────────────────────────────────
val GreenConnected   = Color(0xFF00E676)   // connected / success
val AmberReconnect   = Color(0xFFFFD740)   // reconnecting / warning
val RedError         = Color(0xFFFF5252)   // failed / error
val GreyDisconnected = Color(0xFF78909C)   // idle / disconnected

// ── QoS colours ───────────────────────────────────────────────────────────────
val Qos0Color = Color(0xFF78909C)   // grey   — at most once  (low guarantee)
val Qos1Color = Color(0xFF40C4FF)   // blue   — at least once (default)
val Qos2Color = Color(0xFF69F0AE)   // green  — exactly once  (highest guarantee)

// ── Direction colours ─────────────────────────────────────────────────────────
val InboundColor  = Color(0xFF40C4FF)   // ← received
val OutboundColor = Color(0xFF69F0AE)   // → sent

// ── Surface text ──────────────────────────────────────────────────────────────
val OnNavy    = Color(0xFFE0E8FF)
val OnNavySub = Color(0xFF8899BB)
