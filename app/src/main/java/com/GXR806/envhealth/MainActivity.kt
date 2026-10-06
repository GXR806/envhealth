package com.GXR806.envhealth

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.GXR806.envhealth.ui.screen.ReportScreen
import com.GXR806.envhealth.ui.theme.环境健康Theme
import com.GXR806.envhealth.ui.viewmodel.ReportViewModel

private val tabTitles = listOf("首页", "环境监测", "耳机守护", "报告")

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // 初始化通知渠道（只需在应用启动时调用一次）
        com.GXR806.envhealth.notification.NotificationHelper.createNotificationChannels(this)
        
        setContent {
            环境健康Theme {
                EnvHealthApp()
            }
        }
    }
}

@Composable
private fun EnvHealthApp() {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var showSettings by rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    val app = LocalContext.current.applicationContext as EnvHealthApplication
    val settingsViewModel: com.GXR806.envhealth.ui.viewmodel.SettingsViewModel =
        androidx.lifecycle.viewmodel.compose.viewModel(factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return com.GXR806.envhealth.ui.viewmodel.SettingsViewModel(app.appContainer) as T
            }
        })

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabTitles.forEachIndexed { index, title ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index; showSettings = false },
                        icon = {},
                        label = { Text(title) },
                    )
                }
            }
        },
    ) { innerPadding ->
        if (showSettings) {
            com.GXR806.envhealth.ui.screen.SettingsScreen(settingsViewModel, { showSettings = false }, Modifier.padding(innerPadding))
        } else TabContent(
            title = tabTitles[selectedTab],
            modifier = Modifier.padding(innerPadding),
            onGoHome = { selectedTab = 0 },
            onSettings = { showSettings = true },
        )
    }
}

@Composable
private fun TabContent(title: String, modifier: Modifier = Modifier, onGoHome: () -> Unit, onSettings: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as EnvHealthApplication
    
    when (title) {
        "首页" -> {
            // 首页使用真实的 HomeScreen
            com.GXR806.envhealth.ui.screen.HomeScreen(modifier = modifier, onSettings = onSettings)
        }
        "环境监测" -> {
            // 环境监测页使用真实的 MonitorScreen
            com.GXR806.envhealth.ui.screen.MonitorScreen(modifier = modifier)
        }
        "耳机守护" -> {
            val guardViewModel: com.GXR806.envhealth.ui.viewmodel.HeadphoneGuardViewModel =
                androidx.lifecycle.viewmodel.compose.viewModel(factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                        @Suppress("UNCHECKED_CAST")
                        return com.GXR806.envhealth.ui.viewmodel.HeadphoneGuardViewModel(app.appContainer.headphoneGuard) as T
                    }
                })
            com.GXR806.envhealth.ui.screen.HeadphoneGuardScreen(guardViewModel, onGoHome, modifier)
        }
        "报告" -> {
            // 报告页使用真实的 ReportScreen
            val context = LocalContext.current
            val viewModel: ReportViewModel = androidx.lifecycle.viewmodel.compose.viewModel(factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                    @Suppress("UNCHECKED_CAST")
                    return ReportViewModel(
                        context = context.applicationContext,
                        sleepSessionManager = app.appContainer.sleepSessionManager,
                        generateSleepReportUseCase = app.appContainer.generateSleepReportUseCase,
                        generateHealthAdviceUseCase = app.appContainer.generateHealthAdviceUseCase,
                        sleepRepository = app.appContainer.sleepRepository,
                        adviceRepository = app.appContainer.adviceRepository
                    ) as T
                }
            })
            ReportScreen(viewModel = viewModel, modifier = modifier)
        }
        else -> {
            // 其他页面仍为占位
            Column(
                modifier = modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(text = title, style = MaterialTheme.typography.headlineMedium)
                Text(
                    text = "功能将在后续阶段实现",
                    modifier = Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}
