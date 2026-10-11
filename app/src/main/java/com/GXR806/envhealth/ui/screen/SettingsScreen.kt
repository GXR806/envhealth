package com.GXR806.envhealth.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import com.GXR806.envhealth.data.local.datastore.SettingsSnapshot
import com.GXR806.envhealth.ui.viewmodel.SettingsViewModel

@Composable
fun SettingsScreen(viewModel: SettingsViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    androidx.activity.compose.BackHandler(onBack = onBack)
    val context = androidx.compose.ui.platform.LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    var channelStatus by remember { mutableStateOf(com.GXR806.envhealth.notification.NotificationHelper.healthChannelStatus(context)) }
    DisposableEffect(lifecycleOwner, context) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                channelStatus = com.GXR806.envhealth.notification.NotificationHelper.healthChannelStatus(context)
                viewModel.refreshReminderHistory()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val reminderDiagnostics by viewModel.reminderDiagnostics.collectAsState()
    val reminderHistory by viewModel.reminderHistory.collectAsState()
    LaunchedEffect(Unit) { viewModel.refreshReminderHistory() }
    val settings by viewModel.settings.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val message by viewModel.message.collectAsState()
    var calibrationText by remember(settings.dbCalibrationOffset, settings.maintenanceState) { mutableStateOf(settings.dbCalibrationOffset.toString()) }
    var showPrivacy by rememberSaveable { mutableStateOf(false) }
    var confirm by rememberSaveable { mutableStateOf(false) }
    var start by remember(settings.sleepStartHour, settings.sleepStartMinute) { mutableStateOf("%02d:%02d".format(settings.sleepStartHour, settings.sleepStartMinute)) }
    var end by remember(settings.sleepEndHour, settings.sleepEndMinute) { mutableStateOf("%02d:%02d".format(settings.sleepEndHour, settings.sleepEndMinute)) }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(onClick = onBack) { Text("返回首页") }
        Text("设置与数据管理", style = MaterialTheme.typography.headlineSmall)
        TextButton(onClick = { showPrivacy = true }) { Text("查看隐私说明（只读）") }
        Text("应用主题", style = MaterialTheme.typography.titleMedium)
        ThemeModeOptions(settings.themeMode, !busy && settings.maintenanceState.isEmpty(), viewModel::theme)
        Text("选择后立即保存并应用于所有页面；清除全部数据后恢复跟随系统。")
        Text("开启后台授权不会自动启动采集；请回首页手动开始。关闭会立即停止全部监测并等待存储结束。")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("后台监测与提醒")
            Switch(checked = settings.backgroundMonitoringEnabled, enabled = !busy && settings.maintenanceState.isEmpty(), onCheckedChange = viewModel::background)
        }
        SettingSlider("噪音提醒阈值（估算值 dB）", settings.noiseThresholdDb, 40f..90f, !busy, viewModel::noise)
        SettingSlider("媒体音量提醒阈值（百分比，不是 dB）", settings.volumeThresholdPercent, 20f..80f, !busy, viewModel::volume)
        SettingSlider("连续收听提醒阈值（分钟）", settings.continuousListeningThresholdMinutes.toFloat(), 15f..90f, !busy) { viewModel.duration(it.toInt()) }
        Text("当前持久校准偏移：${settings.dbCalibrationOffset} dB（估算值；默认 96）")
        OutlinedTextField(
            value = calibrationText,
            onValueChange = { calibrationText = it },
            label = { Text("dB 校准偏移输入（估算值，0–120）") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            enabled = !busy
        )
        Button(onClick = { viewModel.calibrationText(calibrationText) }, enabled = !busy) { Text("保存校准偏移") }
        Text("校准对后续采样即时生效；Leq 窗口最多一分钟含旧校准帧，不改历史或睡眠评分公式。")
        OutlinedTextField(value = start, onValueChange = { start = it }, label = { Text("静默开始 HH:mm") }, enabled = !busy)
        OutlinedTextField(value = end, onValueChange = { end = it }, label = { Text("静默结束 HH:mm") }, enabled = !busy)
        Button(onClick = { viewModel.quiet(start, end) }, enabled = !busy) { Text("保存静默时段") }
        Text("健康提醒与前台服务使用不同通知渠道。有前台通知不代表健康提醒已开启；请在系统应用通知设置中检查‘健康提醒’和‘静默健康提醒’。同类提醒最多每24小时一次，选择不再提醒后静音7天；系统免打扰仍然生效。")
        Text("系统提醒状态 / 最近发出与阻断原因", style = MaterialTheme.typography.titleMedium)
        Text(channelStatus)
        Text(reminderHistory)
        Text(if (reminderDiagnostics.isEmpty()) "本进程尚无检查记录" else reminderDiagnostics.entries.joinToString("\n") { "${it.key}：${it.value}" })
        OutlinedButton(onClick = {
            viewModel.checkHealthReminder()
            channelStatus = com.GXR806.envhealth.notification.NotificationHelper.healthChannelStatus(context)
        }, enabled = !busy && settings.maintenanceState.isEmpty(), modifier = Modifier.heightIn(min = 48.dp)) { Text("检查健康提醒（遵守冷却）") }
        OutlinedButton(onClick = {
            viewModel.refreshReminderHistory()
            channelStatus = com.GXR806.envhealth.notification.NotificationHelper.healthChannelStatus(context)
        }, modifier = Modifier.heightIn(min = 48.dp)) { Text("刷新系统与持久记录") }
        Text("节流占位不等于已发出：发送结果未知或进程中断会保留占位。notify 正常返回不证明用户看到；系统活动列表也不证明悬浮横幅。已在前台或静默时发出不会因按Home退后台重发。")
        OutlinedButton(onClick = { com.GXR806.envhealth.notification.NotificationHelper.openHealthChannelSettings(context) },
            modifier = Modifier.heightIn(min = 48.dp)) { Text("打开健康提醒系统设置") }
        Text("界面HIGH使用固定产品风险阈值；系统提醒另受设置阈值、适应阈值与同类24小时/7天静音门控，HIGH不等于每次通知。应用内醒目提示不会自动降音量、振动或绕过系统免打扰。")
        Text("可跨午夜；开始等于结束表示全天静默。静默使用独立无声通知渠道。提醒并非医疗建议。")
        Text("特征采样保留 7 天；已结束会话、报告、建议、耳机区间保留 90 天。原始音频与图像不保存、不上传。")
        if (settings.maintenanceState.isNotEmpty()) Text("数据维护状态：${settings.maintenanceState}；请再次清除恢复", color = MaterialTheme.colorScheme.error)
        message?.let { Text(it) }
        if (busy) CircularProgressIndicator()
        OutlinedButton(onClick = { confirm = true }, enabled = !busy) { Text("清除全部数据并恢复默认设置") }
    }
    if (showPrivacy) com.GXR806.envhealth.ui.component.PrivacyPolicyDialog({}, { showPrivacy = false }, readOnly = true)
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("永久清除全部数据？") }, text = { Text("停止采集，清除所有采样、会话、报告、建议、耳机记录、提醒反馈和设置。无法撤销，后台开关恢复关闭。") }, confirmButton = { TextButton(onClick = { confirm = false; viewModel.clearAll() }) { Text("确认清除") } }, dismissButton = { TextButton(onClick = { confirm = false }) { Text("取消") } })
}

@Composable
private fun SettingSlider(label: String, saved: Float, range: ClosedFloatingPointRange<Float>, enabled: Boolean, save: (Float) -> Unit) {
    var value by remember(saved) { mutableFloatStateOf(saved) }
    Text("$label：${value.toInt()}")
    Slider(value = value, onValueChange = { value = it }, valueRange = range, enabled = enabled)
    TextButton(onClick = { save(value) }, enabled = enabled) { Text("保存$label") }
}
