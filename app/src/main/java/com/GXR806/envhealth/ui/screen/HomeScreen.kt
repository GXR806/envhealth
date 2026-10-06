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
fun HomeScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val isMonitoring by MonitorState.isMonitoring.collectAsState()

    // 权限状态
    val permissionsState = rememberMonitorPermissionsState()
    
    // 权限引导对话框状态
    var showPermissionDialog by remember { mutableStateOf(false) }
    var deniedPermissionsList by remember { mutableStateOf<List<String>>(emptyList()) }
    var isPermanentlyDenied by remember { mutableStateOf(false) }

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
        Button(
            onClick = {
                if (isMonitoring) {
                    // 停止监测
                    EnvMonitorService.stopMonitoring(context)
                } else {
                    // 检查权限
                    if (permissionsState.allPermissionsGranted) {
                        // 权限已授予，启动监测
                        val success = EnvMonitorService.startMonitoring(context)
                        if (!success) {
                            // Android 12+ 可能因后台启动限制失败
                            // TODO: 显示错误提示
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
                showPermissionDialog = false
            }
        )
    }

    // 监听权限变化
    LaunchedEffect(permissionsState.allPermissionsGranted) {
        if (permissionsState.allPermissionsGranted && !isMonitoring) {
            // 权限授予后自动启动监测
            EnvMonitorService.startMonitoring(context)
        }
    }
}
