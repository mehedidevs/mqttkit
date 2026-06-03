package io.github.mqttdemo.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.food.core.mqtt.MqttConnectionState
import com.food.mqttdemo.presentation.MqttUiEvent
import com.food.mqttdemo.presentation.MqttUiState
import com.food.mqttdemo.ui.theme.*

@Composable
fun SubscribePanel(
    state: MqttUiState,
    onEvent: (MqttUiEvent) -> Unit
) {
    val isConnected = state.connectionState is MqttConnectionState.Connected

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Subscriptions", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = OnNavy)

        // ── Add subscription ──────────────────────────────────────────────
        SectionCard(title = "Subscribe to Topic") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = state.subscribeTopic,
                    onValueChange = { onEvent(MqttUiEvent.SubscribeTopicChanged(it)) },
                    label = { Text("Topic filter") },
                    placeholder = { Text("home/+/temperature", color = OnNavySub) },
                    singleLine = true,
                    enabled = isConnected,
                    modifier = Modifier.weight(1f),
                    textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onEvent(MqttUiEvent.AddSubscription) })
                )
                IconButton(
                    onClick = { onEvent(MqttUiEvent.AddSubscription) },
                    enabled = isConnected && state.subscribeTopic.isNotBlank(),
                    modifier = Modifier
                        .size(48.dp)
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Subscribe",
                        tint = if (isConnected && state.subscribeTopic.isNotBlank())
                            CyanPrimary else GreyDisconnected
                    )
                }
            }

            // Wildcard quick-picks
            Text("Wildcard examples:", color = OnNavySub, fontSize = 11.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("mqttdemo/#", "mqttdemo/+/sensor/+", "mqttdemo/broadcast/#").forEach { hint ->
                    AssistChip(
                        onClick = { onEvent(MqttUiEvent.SubscribeTopicChanged(hint)) },
                        label = { Text(hint, fontFamily = FontFamily.Monospace, fontSize = 10.sp) },
                        enabled = isConnected,
                        colors = AssistChipDefaults.assistChipColors(
                            labelColor = CyanPrimary,
                            containerColor = NavyLight
                        )
                    )
                }
            }
        }

        // ── Wildcard reference ────────────────────────────────────────────
        SectionCard(title = "Wildcard Reference") {
            WildcardRow(
                wildcard = "+",
                example  = "home/+/temperature",
                desc     = "Matches exactly ONE level.\nMatches: home/room1/temperature\nNot: home/room1/sensor/temperature"
            )
            HorizontalDivider(color = NavyLight)
            WildcardRow(
                wildcard = "#",
                example  = "home/#",
                desc     = "Matches ZERO OR MORE levels. Must be last.\nMatches: home/room1, home/room1/temp, home/a/b/c"
            )
        }

        // ── Active subscriptions ──────────────────────────────────────────
        Text(
            "Active (${state.activeSubscriptions.size})",
            color = OnNavySub, fontSize = 13.sp, fontWeight = FontWeight.SemiBold
        )

        if (state.activeSubscriptions.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No active subscriptions\n(two defaults are added on connect)",
                    color = OnNavySub, fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(state.activeSubscriptions, key = { it }) { topic ->
                    SubscriptionRow(
                        topic = topic,
                        onRemove = { onEvent(MqttUiEvent.RemoveSubscription(topic)) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SubscriptionRow(topic: String, onRemove: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = NavyMid
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    topic,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = OnNavy
                )
                Text("Collecting messages…", color = GreenConnected, fontSize = 11.sp)
            }
            IconButton(onClick = onRemove, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Unsubscribe",
                    tint = GreyDisconnected, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun WildcardRow(wildcard: String, example: String, desc: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = NavyLight
        ) {
            Text(
                wildcard,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = AmberReconnect
            )
        }
        Column {
            Text(example, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = CyanPrimary)
            Text(desc, color = OnNavySub, fontSize = 11.sp, lineHeight = 15.sp)
        }
    }
}
