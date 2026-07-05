package io.github.mqttdemo.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mehedidevs.mqttkit.MqttConnectionState
import io.github.mqttdemo.presentation.MqttUiEvent
import io.github.mqttdemo.presentation.MqttUiState
import io.github.mqttdemo.ui.theme.CyanPrimary
import io.github.mqttdemo.ui.theme.NavyDeep
import io.github.mqttdemo.ui.theme.NavyLight
import io.github.mqttdemo.ui.theme.OnNavy
import io.github.mqttdemo.ui.theme.OnNavySub
import io.github.mqttdemo.ui.theme.Qos0Color
import io.github.mqttdemo.ui.theme.Qos1Color
import io.github.mqttdemo.ui.theme.Qos2Color
import io.github.mqttdemo.ui.theme.RedError

@Composable
fun PublishPanel(
    state: MqttUiState,
    onEvent: (MqttUiEvent) -> Unit
) {
    val isConnected = state.connectionState is MqttConnectionState.Connected

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Publish Message", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = OnNavy)

        SectionCard(title = "Message") {
            // Topic
            OutlinedTextField(
                value = state.publishTopic,
                onValueChange = { onEvent(MqttUiEvent.PublishTopicChanged(it)) },
                label = { Text("Topic") },
                placeholder = { Text("home/living-room/temperature", color = OnNavySub) },
                singleLine = true,
                enabled = isConnected,
                modifier = Modifier.fillMaxWidth(),
                textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
                supportingText = {
                    Text("Use /  to separate hierarchy levels", color = OnNavySub, fontSize = 11.sp)
                }
            )

            // Payload
            OutlinedTextField(
                value = state.publishPayload,
                onValueChange = { onEvent(MqttUiEvent.PublishPayloadChanged(it)) },
                label = { Text("Payload") },
                placeholder = { Text("""{"temp": 24.5, "unit": "°C"}""", color = OnNavySub) },
                enabled = isConnected,
                minLines = 4,
                maxLines = 8,
                modifier = Modifier.fillMaxWidth(),
                textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
                supportingText = {
                    Text("${state.publishPayload.length} bytes — any UTF-8 content (JSON, plain text, binary via base64)", color = OnNavySub, fontSize = 11.sp)
                }
            )

            // QoS selector
            Text("Quality of Service", color = OnNavySub, fontSize = 12.sp)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QosChip(level = 0, selected = state.publishQos == 0,
                    enabled = isConnected, onClick = { onEvent(MqttUiEvent.PublishQosChanged(0)) })
                QosChip(level = 1, selected = state.publishQos == 1,
                    enabled = isConnected, onClick = { onEvent(MqttUiEvent.PublishQosChanged(1)) })
                QosChip(level = 2, selected = state.publishQos == 2,
                    enabled = isConnected, onClick = { onEvent(MqttUiEvent.PublishQosChanged(2)) })
            }
            QosExplanation(state.publishQos)

            // Retain flag
            LabeledSwitch(
                label = "Retained message",
                checked = state.publishRetained,
                onCheckedChange = { onEvent(MqttUiEvent.PublishRetainedChanged(it)) },
                enabled = isConnected,
                helpText = "Broker stores this message and delivers it immediately to future subscribers."
            )
        }

        // Preset payloads for quick demo
        SectionCard(title = "Quick Presets") {
            listOf(
                "mqttdemo/test/hello"       to """{"message":"Hello, MQTT!","timestamp":${System.currentTimeMillis()}}""",
                "mqttdemo/test/command"     to """{"command":"toggle_led","value":true}""",
                "mqttdemo/broadcast/chat"   to """{"user":"demo","text":"Testing from Android!"}"""
            ).forEach { (topic, payload) ->
                PresetRow(topic, payload, isConnected) {
                    onEvent(MqttUiEvent.PublishTopicChanged(topic))
                    onEvent(MqttUiEvent.PublishPayloadChanged(payload))
                }
            }
        }

        // Error
        state.lastPublishError?.let {
            Text("Error: $it", color = RedError, fontSize = 12.sp)
        }

        // Publish button
        Button(
            onClick = { onEvent(MqttUiEvent.Publish) },
            enabled = isConnected && !state.isPublishing && state.publishTopic.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary, contentColor = NavyDeep),
            shape = RoundedCornerShape(12.dp)
        ) {
            if (state.isPublishing) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = NavyDeep, strokeWidth = 2.dp)
            } else {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(8.dp))
            Text("Publish", fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun QosChip(
    level: Int,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val color = when (level) {
        0    -> Qos0Color
        1    -> Qos1Color
        else -> Qos2Color
    }

    val bg = if (selected) color.copy(alpha = 0.2f) else Color.Transparent
    val border = if (selected) color else OnNavySub.copy(alpha = 0.4f)

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "QoS $level",
                color = if (selected) color else OnNavySub,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun QosExplanation(qos: Int) {
    val (title, desc) = when (qos) {
        0    -> "At most once"  to "Fire-and-forget. No acknowledgement. Fastest but may lose messages. Good for frequent telemetry."
        1    -> "At least once" to "Broker acknowledges receipt. May deliver duplicates if ACK is lost. Good for commands."
        else -> "Exactly once"  to "Two-phase handshake (PUBREC → PUBREL → PUBCOMP). Guaranteed exactly one delivery. Highest overhead."
    }
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = NavyLight
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(title, color = CyanPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text(desc, color = OnNavySub, fontSize = 12.sp, lineHeight = 17.sp)
        }
    }
}

@Composable
private fun PresetRow(
    topic: String,
    payload: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(topic, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = CyanPrimary)
            Text(payload.take(60) + if (payload.length > 60) "…" else "",
                fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = OnNavySub)
        }
        Text("Use", color = CyanPrimary, fontSize = 12.sp, modifier = Modifier.padding(start = 8.dp))
    }
    HorizontalDivider(color = NavyLight)
}
