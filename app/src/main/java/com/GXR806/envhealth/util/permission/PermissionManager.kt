package com.GXR806.envhealth.util.permission

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.GXR806.envhealth.util.LogX

/**
 * 权限管理器
 *
 * 封装运行时权限的检查、请求与三态处理逻辑
 * 三态：已授权、已拒绝（可再次请求）、永久拒绝（需跳转设置）
 */
object PermissionManager {

    /**
     * 环境监测所需的核心权限
     */
    val MONITOR_PERMISSIONS = buildList {
        add(Manifest.permission.RECORD_AUDIO)
        add(Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()

    /**
     * 检查单个权限是否已授权
     */
    fun isPermissionGranted(context: Context, permission: String): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            permission
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * 检查所有监测权限是否已授权
     */
    fun areMonitorPermissionsGranted(context: Context): Boolean {
        return MONITOR_PERMISSIONS.all { isPermissionGranted(context, it) }
    }

    /**
     * 获取未授权的权限列表
     */
    fun getDeniedPermissions(context: Context): List<String> {
        return MONITOR_PERMISSIONS.filter { !isPermissionGranted(context, it) }
    }

    /**
     * 获取权限的中文名称（用于 UI 提示）
     */
    fun getPermissionDisplayName(permission: String): String {
        return when (permission) {
            Manifest.permission.RECORD_AUDIO -> "麦克风"
            Manifest.permission.CAMERA -> "相机"
            Manifest.permission.POST_NOTIFICATIONS -> "通知"
            else -> "未知权限"
        }
    }

    /**
     * 获取权限的用途说明（用于引导对话框）
     */
    fun getPermissionRationale(permission: String): String {
        return when (permission) {
            Manifest.permission.RECORD_AUDIO -> "用于监测环境噪音并估算分贝值（仅内存计算，不保存原始音频）"
            Manifest.permission.CAMERA -> "用于分析环境光照并估算照度（仅内存计算，不保存原始图像）"
            Manifest.permission.POST_NOTIFICATIONS -> "用于及时提醒您环境异常与健康建议"
            else -> "用于提供完整的环境监测功能"
        }
    }

    /**
     * 打开应用设置页面（用于永久拒绝后的引导）
     */
    fun openAppSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            LogX.i("PermissionManager", "打开应用设置页面")
        } catch (e: Exception) {
            LogX.e("PermissionManager", "无法打开设置页面", e)
        }
    }

    /**
     * 权限请求结果
     */
    sealed class PermissionResult {
        /** 所有权限已授权 */
        object AllGranted : PermissionResult()

        /** 部分权限被拒绝（可再次请求） */
        data class PartiallyDenied(val deniedPermissions: List<String>) : PermissionResult()

        /** 部分权限被永久拒绝（需跳转设置） */
        data class PermanentlyDenied(val permanentlyDeniedPermissions: List<String>) : PermissionResult()
    }
}
