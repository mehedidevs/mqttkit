package io.github.mqttdemo.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.food.core.mqtt.MqttConnectionState
import com.food.mqttdemo.presentation.MqttUiEvent
import com.food.mqttdemo.presentation.MqttUiState
import com.food.mqttdemo.ui.theme.*

@Composable
fun DashboardPanel(
    state: MqttUiState,
    onEvent: (MqttUiEvent) -> Unit
) {
    val isConnected = state.connectionState is com.food.core.mqtt.MqttConnectionState.Connected

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ── Header ─────────────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Sensor Dashboard", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = OnNavy)
                Text(
                    if (state.isAutoPublishing) "Publishing every 3 s → broker" else "Tap ▶ to start simulation",
                    color = OnNavySub, fontSize = 12.sp
                )
            }
            Button(
                onClick = { onEvent(MqttUiEvent.ToggleAutoPublish) },
                enabled = isConnected,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (state.isAutoPublishing) AmberReconnect else GreenConnected,
                    contentColor = NavyDeep
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(
                    imageVector = if (state.isAutoPublishing) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(if (state.isAutoPublishing) "Stop" else "Start")
            }
        }

        // ── 2x2 sensor grid ───────────────────────────────────────────────
        val reading = state.sensorReading
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SensorCard(
                modifier    = Modifier.weight(1f),
                icon        = Icons.Default.Thermostat,
                label       = "Temperature",
                value       = "%.1f °C".format(reading.temperature),
                progress    = (reading.temperature - 10f) / 30f,
                color       = Color(0xFFFF7043),
                topic       = "sensor/temperature"
            )
            SensorCard(
                modifier    = Modifier.weight(1f),
                icon        = Icons.Default.WaterDrop,
                label       = "Humidity",
                value       = "%.1f %%RH".format(reading.humidity),
                progress    = reading.humidity / 100f,
                color       = BlueAccent,
                topic       = "sensor/humidity"
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SensorCard(
                modifier    = Modifier.weight(1f),
                icon        = Icons.Default.Air,
                label       = "Pressure",
                value       = "%.0f hPa".format(reading.pressure),
                progress    = (reading.pressure - 950f) / 100f,
                color       = Color(0xFFBA68C8),
                topic       = "sensor/pressure"
            )
            SensorCard(
                modifier    = Modifier.weight(1f),
                icon        = Icons.Default.LightMode,
                label       = "Light",
                value       = "${reading.lightLevel} lux",
                progress    = reading.lightLevel / 1000f,
                color       = AmberReconnect,
                topic       = "sensor/light"
            )
        }

        // ── Published topic info ──────────────────────────────────────────
        if (isConnected) {
            SectionCard(title = "Published Topics (QoS 0)") {
                listOf("sensor/temperature", "sensor/humidity", "sensor/pressure",
                        "sensor/light", "sensor/all (QoS 1 — full JSON)").forEach { suffix ->
                    Text(
                        "mqttdemo/${state.clientId}/$suffix",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = OnNavySub,
                        lineHeight = 16.sp
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  SensorCard — arc gauge + value display
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun SensorCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    value: String,
    progress: Float,
    color: Color,
    topic: String
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(600),
        label = "gauge_anim"
    )

    Surface(
        modifier = modifier,
        shape    = RoundedCornerShape(16.dp),
        color    = NavyMid
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Arc gauge
            Box(
                modifier = Modifier.size(90.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val stroke = 8.dp.toPx()
                    val padding = stroke / 2
                    // Background track
                    drawArc(
                        color       = NavyLight,
                        startAngle  = 150f,
                        sweepAngle  = 240f,
                        useCenter   = false,
                        style       = Stroke(stroke, cap = StrokeCap.Round),
                        topLeft     = Offset(padding, padding),
                        size        = Size(size.width - stroke, size.height - stroke)
                    )
                    // Filled arc
                    drawArc(
                        color       = color,
                        startAngle  = 150f,
                        sweepAngle  = 240f * animatedProgress,
                        useCenter   = false,
                        style       = Stroke(stroke, cap = StrokeCap.Round),
                        topLeft     = Offset(padding, padding),
                        size        = Size(size.width - stroke, size.height - stroke)
                    )
                }
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(28.dp))
            }

            Text(label, color = OnNavySub, fontSize = 11.sp)
            Text(value, color = OnNavy, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(topic, color = color.copy(alpha = 0.7f), fontSize = 10.sp,
                fontFamily = FontFamily.Monospace)
        }
    }
}
