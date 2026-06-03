package io.github.mqttdemo.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary          = CyanPrimary,
    onPrimary        = NavyDeep,
    primaryContainer = NavyLight,
    onPrimaryContainer = CyanLight,
    secondary        = BlueAccent,
    onSecondary      = NavyDeep,
    background       = NavyDeep,
    onBackground     = OnNavy,
    surface          = NavyMid,
    onSurface        = OnNavy,
    surfaceVariant   = NavyLight,
    onSurfaceVariant = OnNavySub,
    error            = RedError,
    onError          = NavyDeep,
    outline          = NavyLight,
)

@Composable
fun MqttDemoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography  = MqttTypography,
        content     = content
    )
}
