package io.github.mqttdemo.ui.screen

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.food.core.mqtt.MqttConnectionState
import com.food.mqttdemo.presentation.MqttUiEvent
import com.food.mqttdemo.presentation.MqttViewModel
import com.food.mqttdemo.ui.component.*
import com.food.mqttdemo.ui.theme.*
import org.koin.androidx.compose.koinViewModel

private enum class Tab(
    val label: String,
    val icon: ImageVector,
    val badged: Boolean = false
) {
    CONNECT("Connect",   Icons.Default.Cable),
    DASHBOARD("Sensors", Icons.Default.Dashboard),
    PUBLISH("Publish",   Icons.AutoMirrored.Filled.Send),
    SUBSCRIBE("Topics",  Icons.Default.Subscriptions),
    LOG("Log",           Icons.AutoMirrored.Filled.List),
    LEARN("Guide",       Icons.Default.Book);
}

@Composable
fun MqttScreen(
    viewModel: MqttViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var selectedTab by remember { mutableStateOf(Tab.CONNECT) }

    val snackbarHostState = remember { SnackbarHostState() }

    // Show snackbar messages from ViewModel
    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let {
            snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Short)
            viewModel.onEvent(MqttUiEvent.SnackbarShown)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = NavyDeep,
        topBar = {
            TopBar(state.connectionState)
        },
        bottomBar = {
            BottomNav(
                selectedTab = selectedTab,
                unreadLog   = state.logEntries.size,
                onTabSelected = { selectedTab = it }
            )
        }
    ) { innerPadding ->
        AnimatedContent(
            targetState = selectedTab,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            label = "tab_transition"
        ) { tab ->
            when (tab) {
                Tab.CONNECT   -> ConnectionCard(state = state, onEvent = viewModel::onEvent)
                Tab.DASHBOARD -> DashboardPanel(state = state, onEvent = viewModel::onEvent)
                Tab.PUBLISH   -> PublishPanel(state = state, onEvent = viewModel::onEvent)
                Tab.SUBSCRIBE -> SubscribePanel(state = state, onEvent = viewModel::onEvent)
                Tab.LOG       -> MessageLogPanel(state = state, onEvent = viewModel::onEvent)
                Tab.LEARN     -> LearnPanel()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopBar(connectionState: MqttConnectionState) {
    val (label, color) = when (connectionState) {
        is MqttConnectionState.Connected    -> "broker.hivemq.com" to GreenConnected
        is MqttConnectionState.Connecting   -> "Connecting…"       to AmberReconnect
        is MqttConnectionState.Reconnecting -> "Reconnecting…"     to AmberReconnect
        is MqttConnectionState.Disconnected -> "Offline"           to GreyDisconnected
        is MqttConnectionState.Failed       -> "Error"             to RedError
        is MqttConnectionState.Idle         -> "Not connected"     to GreyDisconnected
    }

    TopAppBar(
        title = {
            Column {
                Text(
                    "MQTT Demo",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = OnNavy
                )
                Text(label, fontSize = 11.sp, color = color)
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = NavyMid)
    )
}

@Composable
private fun BottomNav(
    selectedTab: Tab,
    unreadLog: Int,
    onTabSelected: (Tab) -> Unit
) {
    NavigationBar(containerColor = NavyMid, tonalElevation = 0.dp) {
        Tab.entries.forEach { tab ->
            NavigationBarItem(
                selected = selectedTab == tab,
                onClick  = { onTabSelected(tab) },
                icon = {
                    if (tab == Tab.LOG && unreadLog > 0) {
                        BadgedBox(
                            badge = {
                                Badge(containerColor = CyanPrimary, contentColor = NavyDeep) {
                                    Text(
                                        if (unreadLog > 99) "99+" else unreadLog.toString(),
                                        fontSize = 9.sp
                                    )
                                }
                            }
                        ) {
                            Icon(tab.icon, contentDescription = tab.label)
                        }
                    } else {
                        Icon(tab.icon, contentDescription = tab.label)
                    }
                },
                label = { Text(tab.label, fontSize = 10.sp) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor   = CyanPrimary,
                    selectedTextColor   = CyanPrimary,
                    unselectedIconColor = OnNavySub,
                    unselectedTextColor = OnNavySub,
                    indicatorColor      = NavyLight
                )
            )
        }
    }
}
