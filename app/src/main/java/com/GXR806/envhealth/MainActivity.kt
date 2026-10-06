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

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabTitles.forEachIndexed { index, title ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = {},
                        label = { Text(title) },
                    )
                }
            }
        },
    ) { innerPadding ->
        TabContent(
            title = tabTitles[selectedTab],
            modifier = Modifier.padding(innerPadding),
        )
    }
}

@Composable
private fun TabContent(title: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val app = context.applicationContext as EnvHealthApplication
    
    when (title) {
        "首页" -> {
            // 首页使用真实的 HomeScreen
            com.GXR806.envhealth.ui.screen.HomeScreen(modifier = modifier)
        }
        "环境监测" -> {
            // 环境监测页使用真实的 MonitorScreen
            com.GXR806.envhealth.ui.screen.MonitorScreen(modifier = modifier)
        }
        "报告" -> {
            // 报告页使用真实的 ReportScreen
            val context = LocalContext.current
            val viewModel = remember {
                ReportViewModel(
                    context = context,
                    sleepSessionManager = app.appContainer.sleepSessionManager,
                    generateSleepReportUseCase = app.appContainer.generateSleepReportUseCase,
                    generateHealthAdviceUseCase = app.appContainer.generateHealthAdviceUseCase,
                    sleepRepository = app.appContainer.sleepRepository,
                    adviceRepository = app.appContainer.adviceRepository
                )
            }
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
