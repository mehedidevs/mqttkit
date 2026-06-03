package io.github.mqttdemo.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.food.mqttdemo.domain.LogEntry
import com.food.mqttdemo.domain.MessageDirection
import com.food.mqttdemo.presentation.MqttUiEvent
import com.food.mqttdemo.presentation.MqttUiState
import com.food.mqttdemo.ui.theme.*
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val TIME_FMT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneId.systemDefault())

@Composable
fun MessageLogPanel(
    state: MqttUiState,
    onEvent: (MqttUiEvent) -> Unit
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Auto-scroll to top when a new message arrives (newest-first list)
    LaunchedEffect(state.logEntries.size) {
        if (state.logEntries.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // ── Header bar ────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("Message Log", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = OnNavy)
                Text(
                    "${state.logEntries.size} messages (newest first, max 200)",
                    color = OnNavySub, fontSize = 11.sp
                )
            }
            IconButton(
                onClick = { onEvent(MqttUiEvent.ClearLog) },
                enabled = state.logEntries.isNotEmpty()
            ) {
                Icon(Icons.Default.DeleteSweep, contentDescription = "Clear log",
                    tint = if (state.logEntries.isNotEmpty()) RedError else GreyDisconnected)
            }
        }

        // ── Legend ────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            LegendItem(color = InboundColor,  icon = "←", label = "Received")
            LegendItem(color = OutboundColor, icon = "→", label = "Published")
        }

        if (state.logEntries.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No messages yet", color = OnNavySub)
                    Text("Subscribe to topics or publish a message", color = GreyDisconnected, fontSize = 12.sp)
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                reverseLayout = false
            ) {
                items(state.logEntries, key = { it.id }) { entry ->
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn() + slideInVertically(initialOffsetY = { -it / 3 })
                    ) {
                        LogEntryCard(entry)
                    }
                }
            }
        }
    }
}

@Composable
private fun LogEntryCard(entry: LogEntry) {
    val isInbound = entry.direction == MessageDirection.INBOUND
    val accentColor = if (isInbound) InboundColor else OutboundColor
    val qosColor = when (entry.qos) { 0 -> Qos0Color; 2 -> Qos2Color; else -> Qos1Color }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = NavyMid
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // ── Top row: direction + topic + timestamp ─────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (isInbound) Icons.Default.ArrowDownward
                                      else Icons.Default.ArrowUpward,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        entry.topic,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = accentColor,
                        modifier = Modifier.weight(1f)
                    )
                }
                Text(
                    TIME_FMT.format(entry.timestamp),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = OnNavySub
                )
            }

            Spacer(Modifier.height(6.dp))

            // ── Payload ────────────────────────────────────────────────
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = NavyDeep
            ) {
                Text(
                    entry.payload.take(300) + if (entry.payload.length > 300) "\n…(truncated)" else "",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = OnNavy,
                    lineHeight = 17.sp,
                    modifier = Modifier.padding(8.dp)
                )
            }

            Spacer(Modifier.height(6.dp))

            // ── Footer: QoS + retained badge ──────────────────────────
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Badge(
                    label = "QoS ${entry.qos}",
                    color = qosColor
                )
                if (entry.retained) {
                    Badge(label = "retained", color = AmberReconnect)
                }
                Badge(
                    label = if (isInbound) "inbound" else "outbound",
                    color = accentColor
                )
            }
        }
    }
}

@Composable
private fun Badge(label: String, color: androidx.compose.ui.graphics.Color) {
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(label, color = color, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun LegendItem(color: androidx.compose.ui.graphics.Color, icon: String, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(icon, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text(label, color = OnNavySub, fontSize = 11.sp)
    }
}
