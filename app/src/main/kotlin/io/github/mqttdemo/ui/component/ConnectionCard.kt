package io.github.mqttdemo.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mehedidevs.mqttkit.MqttConnectionState
import io.github.mqttdemo.presentation.MqttUiEvent
import io.github.mqttdemo.presentation.MqttUiState
import io.github.mqttdemo.presentation.publicBrokerPresets
import io.github.mqttdemo.ui.theme.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ConnectionCard(
    state: MqttUiState,
    onEvent: (MqttUiEvent) -> Unit
) {
    val isConnected = state.connectionState is MqttConnectionState.Connected
    val isBusy = state.connectionState is MqttConnectionState.Connecting ||
            state.connectionState is MqttConnectionState.Reconnecting ||
            state.isDisconnecting

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ── Status card ───────────────────────────────────────────────────────
        StatusBanner(state.connectionState)

        // ── Public test brokers ───────────────────────────────────────────────
        SectionCard(title = "Public Test Brokers") {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                publicBrokerPresets.forEach { preset ->
                    val selected = state.brokerHost == preset.host &&
                            state.brokerPort == preset.port &&
                            state.useTls == preset.useTls
                    FilterChip(
                        selected = selected,
                        onClick = { onEvent(MqttUiEvent.PublicBrokerSelected(preset)) },
                        enabled = !isConnected && !isBusy,
                        label = {
                            Column {
                                Text(preset.name, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "${preset.host}:${preset.port}",
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CyanPrimary,
                            selectedLabelColor = NavyDeep,
                            containerColor = NavyLight,
                            labelColor = OnNavy
                        )
                    )
                }
            }
            val selectedPreset = publicBrokerPresets.firstOrNull {
                state.brokerHost == it.host &&
                        state.brokerPort == it.port &&
                        state.useTls == it.useTls
            }
            Text(
                text = selectedPreset?.note ?: "Use manual host settings below.",
                color = OnNavySub,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
        }

        // ── Broker settings ───────────────────────────────────────────────────
        SectionCard(title = "Broker") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = state.brokerHost,
                    onValueChange = { onEvent(MqttUiEvent.BrokerHostChanged(it)) },
                    label = { Text("Host") },
                    singleLine = true,
                    enabled = !isConnected && !isBusy,
                    modifier = Modifier.weight(2f),
                    textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace)
                )
                OutlinedTextField(
                    value = state.brokerPort,
                    onValueChange = { onEvent(MqttUiEvent.BrokerPortChanged(it)) },
                    label = { Text("Port") },
                    singleLine = true,
                    enabled = !isConnected && !isBusy,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
            }
            OutlinedTextField(
                value = state.clientId,
                onValueChange = { onEvent(MqttUiEvent.ClientIdChanged(it)) },
                label = { Text("Client ID") },
                singleLine = true,
                enabled = !isConnected && !isBusy,
                modifier = Modifier.fillMaxWidth(),
                textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
                supportingText = { Text("Must be unique per connection", color = OnNavySub) }
            )
        }

        // ── Auth ──────────────────────────────────────────────────────────────
        SectionCard(title = "Authentication (optional)") {
            var showPassword by remember { mutableStateOf(false) }
            OutlinedTextField(
                value = state.username,
                onValueChange = { onEvent(MqttUiEvent.UsernameChanged(it)) },
                label = { Text("Username") },
                singleLine = true,
                enabled = !isConnected && !isBusy,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = state.password,
                onValueChange = { onEvent(MqttUiEvent.PasswordChanged(it)) },
                label = { Text("Password") },
                singleLine = true,
                enabled = !isConnected && !isBusy,
                visualTransformation = if (showPassword) VisualTransformation.None
                                       else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(
                            if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (showPassword) "Hide" else "Show"
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }

        // ── MQTT options ──────────────────────────────────────────────────────
        SectionCard(title = "Session Options") {
            LabeledSwitch(
                label = "TLS / SSL  (port 8883)",
                checked = state.useTls,
                onCheckedChange = { onEvent(MqttUiEvent.UseTlsChanged(it)) },
                enabled = !isConnected && !isBusy,
                helpText = "Encrypts the connection. Use port 8883."
            )
            LabeledSwitch(
                label = "Clean Start",
                checked = state.cleanStart,
                onCheckedChange = { onEvent(MqttUiEvent.CleanStartChanged(it)) },
                enabled = !isConnected && !isBusy,
                helpText = "ON → broker discards old session. OFF → resumes subscriptions & missed QoS 1/2 messages."
            )
            LabeledSwitch(
                label = "Last Will & Testament (LWT)",
                checked = state.enableLwt,
                onCheckedChange = { onEvent(MqttUiEvent.EnableLwtChanged(it)) },
                enabled = !isConnected && !isBusy,
                helpText = "Broker publishes 'offline' to your status topic if you disconnect ungracefully."
            )
            OutlinedTextField(
                value = state.keepAliveSec,
                onValueChange = { onEvent(MqttUiEvent.KeepAliveChanged(it)) },
                label = { Text("Keep-alive (seconds)") },
                singleLine = true,
                enabled = !isConnected && !isBusy,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                supportingText = { Text("Client sends PINGREQ if silent for this long.", color = OnNavySub) }
            )
        }

        // ── Connect / Disconnect button ───────────────────────────────────────
        Button(
            onClick = {
                if (isConnected) onEvent(MqttUiEvent.Disconnect)
                else onEvent(MqttUiEvent.Connect)
            },
            enabled = !isBusy,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isConnected) RedError else CyanPrimary,
                contentColor   = NavyDeep
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            if (isBusy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = NavyDeep,
                    strokeWidth = 2.dp
                )
                Spacer(Modifier.width(10.dp))
            }
            Text(
                text = when {
                    state.isDisconnecting -> "Disconnecting..."
                    isBusy      -> "Connecting…"
                    isConnected -> "Disconnect"
                    else        -> "Connect"
                },
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        }

        Spacer(Modifier.height(8.dp))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  StatusBanner
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun StatusBanner(state: MqttConnectionState) {
    val (label, color) = when (state) {
        is MqttConnectionState.Connected    -> "Connected"   to GreenConnected
        is MqttConnectionState.Connecting   -> "Connecting…" to AmberReconnect
        is MqttConnectionState.Reconnecting -> "Reconnecting (attempt ${state.attempt})" to AmberReconnect
        is MqttConnectionState.Disconnected -> "Disconnected" to GreyDisconnected
        is MqttConnectionState.Failed       -> "Failed: ${state.cause.message?.take(60)}" to RedError
        is MqttConnectionState.Idle         -> "Idle"         to GreyDisconnected
    }

    val isPulsing = state is MqttConnectionState.Connecting ||
            state is MqttConnectionState.Reconnecting

    val pulse = rememberInfiniteTransition(label = "pulse")
    val scale by pulse.animateFloat(
        initialValue = 1f, targetValue = 1.4f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "pulse_scale"
    )

    val animatedColor by animateColorAsState(targetValue = color, label = "status_color")

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(12.dp),
        color    = NavyMid,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .scale(if (isPulsing) scale else 1f)
                    .clip(CircleShape)
                    .background(animatedColor)
            )
            Column {
                Text("Status", color = OnNavySub, fontSize = 11.sp)
                Text(label, color = animatedColor, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Reusable helpers
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun SectionCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(12.dp),
        color    = NavyMid
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(title, color = CyanPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            content()
        }
    }
}

@Composable
fun LabeledSwitch(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    helpText: String? = null
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, color = OnNavy, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
                colors = SwitchDefaults.colors(checkedThumbColor = NavyDeep, checkedTrackColor = CyanPrimary)
            )
        }
        if (helpText != null) {
            Text(helpText, color = OnNavySub, fontSize = 11.sp, lineHeight = 15.sp)
        }
    }
}
