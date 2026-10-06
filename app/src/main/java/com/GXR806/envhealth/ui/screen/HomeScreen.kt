package com.GXR806.envhealth.ui.screen

import android.Manifest
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.GXR806.envhealth.service.EnvMonitorService
import com.GXR806.envhealth.util.MonitorState
import com.GXR806.envhealth.util.permission.PermissionManager
import com.GXR806.envhealth.util.permission.PermissionRationaleDialog
import com.GXR806.envhealth.util.permission.getDeniedPermissions
import com.GXR806.envhealth.util.permission.getPermanentlyDeniedPermissions
import com.GXR806.envhealth.util.permission.isPermanentlyDenied
import com.GXR806.envhealth.util.permission.rememberMonitorPermissionsState
import com.google.accompanist.permissions.ExperimentalPermissionsApi

/**
 * 首页屏幕
 *
 * 功能：
 * - 显示"开始/停止监测"按钮
 * - 处理权限请求（三态：授权/拒绝/永久拒绝）
 * - 启动/停止前台服务
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun HomeScreen(modifier: Modifier = Modifier, onSettings: () -> Unit = {}) {
    val context = LocalContext.current
    val container = (context.applicationContext as com.GXR806.envhealth.EnvHealthApplication).appContainer
    val settings by container.settingsRepository.snapshot.collectAsState(com.GXR806.envhealth.data.local.datastore.SettingsSnapshot())
    val blocked by container.maintenanceGate.blocked.collectAsState()
    val isMonitoring by MonitorState.isMonitoring.collectAsState()

    // 权限状态
    val permissionsState = rememberMonitorPermissionsState()
    
    // 权限引导对话框状态
    var showPermissionDialog by remember { mutableStateOf(false) }
    var deniedPermissionsList by remember { mutableStateOf<List<String>>(emptyList()) }
    var isPermanentlyDenied by remember { mutableStateOf(false) }
    // 仅本次可见页面的明确开始点击可在授权返回后启动，返回首页不自动采集。
    var pendingUserStart by remember { mutableStateOf(false) }
    var startError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(settings.backgroundMonitoringEnabled, blocked, settings.maintenanceState) {
        if (!settings.backgroundMonitoringEnabled || blocked || settings.maintenanceState.isNotEmpty()) pendingUserStart = false
    }

    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "环境健康",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(24.dp))

        // 监测状态指示
        Text(
            text = if (isMonitoring) "监测中..." else "未监测",
            style = MaterialTheme.typography.bodyLarge,
            color = if (isMonitoring) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 开始/停止监测按钮
        androidx.compose.material3.TextButton(onClick = onSettings) { Text("设置与数据管理") }
        if (!settings.backgroundMonitoringEnabled) Text("后台监测默认关闭，请先在设置中明确开启，再手动开始。开启不会自动采集。")
        if (blocked || settings.maintenanceState.isNotEmpty()) Text("数据维护未完成，请在设置中重试清除。")
        Button(
            enabled = isMonitoring || (settings.backgroundMonitoringEnabled && !blocked && settings.maintenanceState.isEmpty()),
            onClick = {
                if (isMonitoring) {
                    // 停止监测
                    pendingUserStart = false
                    EnvMonitorService.stopMonitoring(context)
                } else {
                    pendingUserStart = true
                    // 检查权限
                    if (permissionsState.allPermissionsGranted) {
                        // 权限已授予，启动监测
                        pendingUserStart = false
                        val success = EnvMonitorService.startMonitoring(context)
                        if (!success) {
                            startError = "启动失败：请确认权限已授予，并保持应用在前台重试"
                        }
                    } else {
                        // 权限未授予，显示权限引导对话框
                        deniedPermissionsList = if (permissionsState.isPermanentlyDenied()) {
                            permissionsState.getPermanentlyDeniedPermissions()
                        } else {
                            permissionsState.getDeniedPermissions().ifEmpty {
                                // 第一次请求权限
                                PermissionManager.MONITOR_PERMISSIONS.toList()
                            }
                        }
                        isPermanentlyDenied = permissionsState.isPermanentlyDenied()
                        showPermissionDialog = true
                    }
                }
            }
        ) {
            Text(if (isMonitoring) "停止监测" else "开始监测")
        }

        Spacer(modifier = Modifier.height(8.dp))

        startError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        // 权限状态提示
        if (!permissionsState.allPermissionsGranted && !isMonitoring) {
            Text(
                text = "需要麦克风、相机${if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) "和通知" else ""}权限",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }

    // 权限引导对话框
    if (showPermissionDialog) {
        PermissionRationaleDialog(
            permissions = deniedPermissionsList,
            isPermanentlyDenied = isPermanentlyDenied,
            onConfirm = {
                if (!isPermanentlyDenied) {
                    // 请求权限
                    permissionsState.launchMultiplePermissionRequest()
                }
                showPermissionDialog = false
            },
            onDismiss = {
                pendingUserStart = false
                showPermissionDialog = false
            }
        )
    }

    // 监听权限变化
    LaunchedEffect(permissionsState.allPermissionsGranted) {
        if (pendingUserStart && permissionsState.allPermissionsGranted && !isMonitoring && settings.backgroundMonitoringEnabled && !blocked && settings.maintenanceState.isEmpty()) {
            pendingUserStart = false
            // 只延续本页明确点击的授权流程。
            if (!EnvMonitorService.startMonitoring(context)) startError = "启动失败：请保持应用在前台重试"
        }
    }
}
