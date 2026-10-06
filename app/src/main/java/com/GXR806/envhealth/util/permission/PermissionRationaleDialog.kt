package com.GXR806.envhealth.util.permission

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * 权限引导对话框
 *
 * 用于向用户说明权限用途，处理三种场景：
 * 1. 首次请求：说明用途，引导授权
 * 2. 拒绝后再次请求：强调必要性，再次引导
 * 3. 永久拒绝：提示跳转系统设置页面
 */
@Composable
fun PermissionRationaleDialog(
    permissions: List<String>,
    isPermanentlyDenied: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isPermanentlyDenied) "需要权限" else "权限说明",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column {
                if (isPermanentlyDenied) {
                    Text(
                        text = "您已拒绝授予以下权限，环境监测功能需要这些权限才能正常工作：",
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    Text(
                        text = "环境监测功能需要以下权限：",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                permissions.forEach { permission ->
                    val displayName = PermissionManager.getPermissionDisplayName(permission)
                    val rationale = PermissionManager.getPermissionRationale(permission)

                    Text(
                        text = "• $displayName",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    Text(
                        text = rationale,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 12.dp, top = 4.dp)
                    )
                }

                if (isPermanentlyDenied) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "请在系统设置中手动开启权限。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (isPermanentlyDenied) {
                    PermissionManager.openAppSettings(context)
                }
                onConfirm()
            }) {
                Text(if (isPermanentlyDenied) "前往设置" else "授权")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(if (isPermanentlyDenied) "暂不设置" else "取消")
            }
        }
    )
}
