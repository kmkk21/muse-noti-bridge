package com.kmkk21.musenotibridge

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.kmkk21.musenotibridge.ui.BridgeViewModel
import com.kmkk21.musenotibridge.ui.screens.DashboardScreen
import com.kmkk21.musenotibridge.ui.screens.DeliverySettingsScreen
import com.kmkk21.musenotibridge.ui.screens.FiltersScreen
import com.kmkk21.musenotibridge.ui.screens.LogsScreen
import com.kmkk21.musenotibridge.ui.theme.MuseBridgeTheme

class MainActivity : ComponentActivity() {

    private val viewModel: BridgeViewModel by viewModels()

    private val postNotiPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* optional for keep-alive notification */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                postNotiPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        setContent {
            MuseBridgeTheme {
                MuseBridgeApp(viewModel = viewModel)
            }
        }
    }
}

private data class NavTab(
    val title: String,
    val icon: ImageVector
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MuseBridgeApp(viewModel: BridgeViewModel) {
    val tabs = remember {
        listOf(
            NavTab("대시보드", Icons.Default.Dashboard),
            NavTab("알림 기록", Icons.Default.History),
            NavTab("필터·광고", Icons.Default.FilterAlt),
            NavTab("Muse 연동", Icons.Default.Settings)
        )
    }

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val snackbarHostState = remember { SnackbarHostState() }

    val settings by viewModel.settings.collectAsState()
    val systemStatus by viewModel.systemStatus.collectAsState()
    val notifications by viewModel.recentNotifications.collectAsState()
    val deliveryLogs by viewModel.deliveryLogs.collectAsState()
    val pendingCount by viewModel.pendingCount.collectAsState()
    val sentTodayCount by viewModel.sentTodayCount.collectAsState()
    val adBlockedTodayCount by viewModel.adBlockedTodayCount.collectAsState()
    val estimatedPendingTokens by viewModel.estimatedPendingTokens.collectAsState()
    val installedApps by viewModel.installedApps.collectAsState()
    val isBusy by viewModel.isBusy.collectAsState()
    val snackbarMsg by viewModel.snackbarMessage.collectAsState()

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshSystemStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(snackbarMsg) {
        snackbarMsg?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearSnackbar()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Muse 알림 브릿지 · ${tabs[selectedTab].title}") }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = { Icon(tab.icon, contentDescription = tab.title) },
                        label = { Text(tab.title) }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (selectedTab) {
                0 -> DashboardScreen(
                    settings = settings,
                    systemStatus = systemStatus,
                    pendingCount = pendingCount,
                    sentTodayCount = sentTodayCount,
                    adBlockedTodayCount = adBlockedTodayCount,
                    estimatedPendingTokens = estimatedPendingTokens,
                    deliveryLogs = deliveryLogs,
                    isBusy = isBusy,
                    onToggleMaster = viewModel::setMasterEnabled,
                    onSendNow = viewModel::sendPendingNow,
                    onSendTest = { viewModel.sendTestMessage(settings) },
                    onNavigateToDeliverySettings = { selectedTab = 3 }
                )

                1 -> LogsScreen(
                    notifications = notifications,
                    onRequeue = viewModel::requeueNotification,
                    onClearAll = viewModel::clearAllLogs
                )

                2 -> FiltersScreen(
                    settings = settings,
                    installedApps = installedApps,
                    onLoadApps = viewModel::loadInstalledAppsIfNeeded,
                    onToggleAdBlock = viewModel::setAdBlockEnabled,
                    onAddAdKeyword = viewModel::addAdKeyword,
                    onRemoveAdKeyword = viewModel::removeAdKeyword,
                    onAddIgnoreKeyword = viewModel::addIgnoreKeyword,
                    onRemoveIgnoreKeyword = viewModel::removeIgnoreKeyword,
                    onToggleUrgentInstant = viewModel::setUrgentInstantSend,
                    onAddUrgentKeyword = viewModel::addUrgentKeyword,
                    onRemoveUrgentKeyword = viewModel::removeUrgentKeyword,
                    onChangeMaxChars = viewModel::setMaxContentChars,
                    onChangeAppFilterMode = viewModel::setAppFilterMode,
                    onTogglePackage = viewModel::togglePackageSelection
                )

                3 -> DeliverySettingsScreen(
                    settings = settings,
                    isBusy = isBusy,
                    onChangeInterval = viewModel::setBatchIntervalMinutes,
                    onToggleKeepAlive = viewModel::setKeepAliveEnabled,
                    onSaveDelivery = viewModel::saveDeliverySettings,
                    onTestDelivery = viewModel::sendTestMessage
                )
            }
        }
    }
}
