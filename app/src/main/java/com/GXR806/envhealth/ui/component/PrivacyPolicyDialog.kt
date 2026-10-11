package com.GXR806.envhealth.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

const val LOCAL_PRIVACY_POLICY = """隐私说明（版本 1）
环境健康仅在本机处理环境与耳机使用信息，无账号、无广告、无上传功能。

麦克风用于计算噪音特征；相机用于估算环境光照。原始音频与图像仅在内存中处理，不落盘、不上传。噪音与光照为估算值，校准可配置，不是医疗诊断或标准测量结果。耳机记录为媒体音量百分比和估算使用时长，不是声压级。

本地保存噪音/光照特征采样 7 天；已结束会话、睡眠报告、建议与耳机记录 90 天。清理为同意后周期执行，并非到期瞬间删除。设置与提醒反馈也保存在本机。

麦克风、相机权限只用于您手动开始的监测，通知权限用于可停止的前台服务和健康提醒。系统权限可拒绝或在系统设置撤销。后台开关默认关闭，开启或同意本说明不会自动打开麦克风/相机；仍须手动开始。监测可随时停止。

同意后才恢复本地周期清理工作；未同意时不调度后台工作。拒绝将退出应用。可在设置查看本说明，并通过“清除全部数据并恢复默认设置”撤回同意，同时停止监测、删除本地数据并再次显示说明。

网络边界：当前合并 Manifest 未声明 INTERNET 权限，应用代码没有联网或上传功能；这属于当前构建的静态声明，不代表系统、设备备份或第三方环境的全面安全认证。数据库与设置已排除应用备份/设备转移。
"""

@Composable
fun PrivacyPolicyDialog(onAgree: () -> Unit, onDecline: () -> Unit, readOnly: Boolean = false,
    busy: Boolean = false, message: String? = null) {
    AlertDialog(
        onDismissRequest = { if (readOnly) onDecline() },
        title = { Text("本地隐私说明") },
        text = { Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
            Text(LOCAL_PRIVACY_POLICY)
            message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        } },
        confirmButton = { TextButton(onClick = if (readOnly) onDecline else onAgree, enabled = !busy) {
            Text(if (readOnly) "关闭" else if (busy) "保存中…" else "同意并保存")
        } },
        dismissButton = { if (!readOnly) TextButton(onClick = onDecline, enabled = !busy) { Text("拒绝并退出") } }
    )
}

/** null means persistent snapshot still loading: never render consent from default settings. */
@Composable
fun PrivacyConsentContent(settings: com.GXR806.envhealth.data.local.datastore.SettingsSnapshot?,
    onAgree: () -> Unit, onDecline: () -> Unit, busy: Boolean = false, message: String? = null,
    content: @Composable () -> Unit) {
    when {
        settings == null -> Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) { CircularProgressIndicator() }
        !settings.hasPrivacyConsent -> PrivacyPolicyDialog(onAgree, onDecline, busy = busy, message = message)
        else -> content()
    }
}
