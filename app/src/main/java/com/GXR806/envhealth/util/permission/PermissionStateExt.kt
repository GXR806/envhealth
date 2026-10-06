package com.GXR806.envhealth.util.permission

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.MultiplePermissionsState
import com.google.accompanist.permissions.PermissionStatus
import com.google.accompanist.permissions.rememberMultiplePermissionsState

/**
 * Compose 权限状态扩展
 *
 * 封装 Accompanist Permissions 的使用，提供更便捷的 API
 */

/**
 * 记住监测权限状态
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun rememberMonitorPermissionsState(
    onPermissionsResult: (Map<String, Boolean>) -> Unit = {}
): MultiplePermissionsState {
    val permissions = remember {
        PermissionManager.MONITOR_PERMISSIONS.toList()
    }

    return rememberMultiplePermissionsState(
        permissions = permissions,
        onPermissionsResult = onPermissionsResult
    )
}

/**
 * 判断权限是否被永久拒绝
 */
@OptIn(ExperimentalPermissionsApi::class)
fun MultiplePermissionsState.isPermanentlyDenied(): Boolean {
    return permissions.any { perm ->
        when (val status = perm.status) {
            is PermissionStatus.Denied -> !status.shouldShowRationale
            else -> false
        }
    }
}

/**
 * 获取被永久拒绝的权限列表
 */
@OptIn(ExperimentalPermissionsApi::class)
fun MultiplePermissionsState.getPermanentlyDeniedPermissions(): List<String> {
    return permissions
        .filter { perm ->
            when (val status = perm.status) {
                is PermissionStatus.Denied -> !status.shouldShowRationale
                else -> false
            }
        }
        .map { it.permission }
}

/**
 * 获取被拒绝的权限列表（可再次请求）
 */
@OptIn(ExperimentalPermissionsApi::class)
fun MultiplePermissionsState.getDeniedPermissions(): List<String> {
    return permissions
        .filter { perm ->
            when (val status = perm.status) {
                is PermissionStatus.Denied -> status.shouldShowRationale
                else -> false
            }
        }
        .map { it.permission }
}
