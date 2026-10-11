package com.GXR806.envhealth.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.GXR806.envhealth.MainActivity
import com.GXR806.envhealth.R
import com.GXR806.envhealth.util.LogX

/**
 * 通知辅助类
 *
 * 封装通知渠道创建、通知构建与发送逻辑
 */
object NotificationHelper {

    /**
     * 通知渠道 ID
     */
    object ChannelId {
        /** 环境监测渠道（低优先级） */
        const val MONITOR = "channel_monitor"

        /** 健康提醒渠道（高优先级） */
        const val HEALTH_ALERT = "channel_health_alert"
        /** 独立静默渠道，不借普通高优先渠道绕过免打扰。 */
        const val HEALTH_SILENT = "channel_health_silent"
    }

    /**
     * 通知 ID
     */
    object NotificationId {
        /** 前台服务通知 */
        const val FOREGROUND_SERVICE = 1001

        /** 噪音异常提醒 */
        const val NOISE_ALERT = 2001

        /** 光照异常提醒 */
        const val LIGHT_ALERT = 2002

        /** 睡眠质量提醒 */
        const val SLEEP_ALERT = 2003

        /** 耳机音量提醒 */
        const val HEADPHONE_ALERT = 2004
    }

    /**
     * 创建通知渠道（在应用启动时调用一次）
     */
    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // 环境监测渠道（低优先级，不发声）
            val monitorChannel = NotificationChannel(
                ChannelId.MONITOR,
                "环境监测",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "显示环境监测服务运行状态"
                setShowBadge(false)
                enableVibration(false)
                enableLights(false)
            }

            // 健康提醒渠道（高优先级，发声提醒）
            val healthAlertChannel = NotificationChannel(
                ChannelId.HEALTH_ALERT,
                "健康提醒",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "重要的环境健康提醒，包括噪音、光照、睡眠与耳机音量异常"
                // 仅首次创建生效；旧渠道声音/重要性始终由系统与用户控制。
                setSound(android.provider.Settings.System.DEFAULT_NOTIFICATION_URI,
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                setShowBadge(true)
                enableVibration(true)
                enableLights(true)
            }

            val silentChannel = NotificationChannel(ChannelId.HEALTH_SILENT,
                "静默健康提醒", NotificationManager.IMPORTANCE_LOW).apply {
                description = "睡眠免打扰期间的健康提醒，无声音与振动"
                setSound(null, null)
                enableVibration(false)
                enableLights(false)
            }
            notificationManager.createNotificationChannels(listOf(monitorChannel, healthAlertChannel, silentChannel))
            LogX.i("NotificationHelper", "通知渠道创建成功")
        }
    }

    /** 发送已校验事件，返回实际 notify 是否成功；权限/系统/频道关闭不记分母。
     * 这里的成功仅代表系统接受 notify，不代表用户看到/听到，也不保证和 DataStore 原子提交。
     */
    fun canSendReminder(context: Context, event: com.GXR806.envhealth.domain.analysis.ReminderPolicy.Event): Boolean {
        createNotificationChannels(context)
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = if (event.silent) ChannelId.HEALTH_SILENT else ChannelId.HEALTH_ALERT
        if (Build.VERSION.SDK_INT >= 33 && androidx.core.content.ContextCompat.checkSelfPermission(context,
                android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            LogX.i("Reminder", "类型=${event.type.name} 原因=通知权限关闭"); return false
        }
        if (!androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            LogX.i("Reminder", "类型=${event.type.name} 原因=应用通知关闭"); return false
        }
        val enabled = manager.getNotificationChannel(channel)?.importance?.let { it != NotificationManager.IMPORTANCE_NONE } == true
        if (!enabled) LogX.i("Reminder", "类型=${event.type.name} 原因=健康频道关闭（前台频道独立）")
        return enabled
    }

    fun sendReminder(context: Context, event: com.GXR806.envhealth.domain.analysis.ReminderPolicy.Event): Boolean {
        if (!canSendReminder(context, event)) return false
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = if (event.silent) ChannelId.HEALTH_SILENT else ChannelId.HEALTH_ALERT
        val content = when (event.type) {
            com.GXR806.envhealth.domain.analysis.ReminderPolicy.Type.NOISE -> "环境噪音估算值偏高，请远离噪音源。降低音量仅调整媒体音量，不会消除外界噪音。"
            com.GXR806.envhealth.domain.analysis.ReminderPolicy.Type.VOLUME -> "媒体音量偏高，请降低音量；百分比不代表声压级。"
            com.GXR806.envhealth.domain.analysis.ReminderPolicy.Type.DURATION -> "连续收听时间较长，建议休息。降低音量不能替代休息。"
            com.GXR806.envhealth.domain.analysis.ReminderPolicy.Type.WEEKLY -> "最近7天收听累计超过40小时，建议休息；40小时不是安全保证。"
        }
        val open = PendingIntent.getActivity(context, 3000 + event.type.ordinal,
            Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val builder = NotificationCompat.Builder(context, channel)
            .setSmallIcon(android.R.drawable.ic_dialog_alert).setContentTitle("环境健康提醒")
            .setContentText(content).setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setContentIntent(open).setAutoCancel(true).setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPriority(if (event.silent) NotificationCompat.PRIORITY_LOW else NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
        if (event.silent) builder.setSilent(true).setSound(null).setVibrate(longArrayOf(0))
        for (action in com.GXR806.envhealth.domain.analysis.ReminderPolicy.Action.entries) {
            val intent = Intent(context, HealthActionReceiver::class.java).apply {
                this.action = HealthActionReceiver.ACTION
                data = android.net.Uri.parse("envhealth://reminder/${event.id}/${action.name}")
                putExtra("event", event.id); putExtra("type", event.type.name); putExtra("operation", action.name)
            }
            val pending = PendingIntent.getBroadcast(context, action.ordinal, intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val label = when (action) {
                com.GXR806.envhealth.domain.analysis.ReminderPolicy.Action.LOWER_VOLUME -> "降低音量"
                com.GXR806.envhealth.domain.analysis.ReminderPolicy.Action.LATER -> "稍后提醒"
                com.GXR806.envhealth.domain.analysis.ReminderPolicy.Action.MUTE -> "不再提醒（7天）"
            }
            builder.addAction(0, label, pending)
        }
        return try { manager.notify(3000 + event.type.ordinal, builder.build()); true }
        catch (e: SecurityException) { false }
    }

    /** 只诊断，低重要性/无声音不改变 canSendReminder 的资格契约。 */
    fun healthChannelStatus(context: Context): String {
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = manager.getNotificationChannel(ChannelId.HEALTH_ALERT)
        val permission = Build.VERSION.SDK_INT < 33 || androidx.core.content.ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val appEnabled = androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()
        val silent = manager.getNotificationChannel(ChannelId.HEALTH_SILENT)
        val active = try {
            manager.activeNotifications.filter { it.id in 3000..3003 }.joinToString { "${it.id}/${it.notification.channelId}" }.ifEmpty { "无" }
        } catch (_: Exception) { "读取失败" }
        return "当前系统活动健康通知（非横幅证明）：$active\n" +
            "静默健康渠道重要性：${silent?.importance ?: "未注册"}（0=关闭，无声是预期）\n" +
            "通知权限：${if (permission) "已允许" else "未允许"}；应用通知：${if (appEnabled) "开启" else "关闭"}\n" +
            "健康提醒渠道重要性：${channel?.importance ?: "未注册"}（HIGH=4）；声音：${if (channel?.sound != null) "已设置" else "无"}\n" +
            "系统免打扰：${when (manager.currentInterruptionFilter) {
                NotificationManager.INTERRUPTION_FILTER_ALL -> "未限制"
                NotificationManager.INTERRUPTION_FILTER_UNKNOWN -> "未知"
                else -> "限制中"
            }}。悬浮横幅由系统/OEM与用户设置决定，HIGH且有声音也不保证弹出。"
    }

    fun openHealthChannelSettings(context: Context) {
        val intent = Intent(android.provider.Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
            putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
            putExtra(android.provider.Settings.EXTRA_CHANNEL_ID, ChannelId.HEALTH_ALERT)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try { context.startActivity(intent) }
        catch (_: android.content.ActivityNotFoundException) {
            try {
                context.startActivity(Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            } catch (_: android.content.ActivityNotFoundException) {
                android.widget.Toast.makeText(context, "无法打开通知设置，请在系统设置中查找本应用", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    fun cancelReminders(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        com.GXR806.envhealth.domain.analysis.ReminderPolicy.Type.entries.forEach { manager.cancel(3000 + it.ordinal) }
    }

    /**
     * 构建前台服务通知
     *
     * @param context 上下文
     * @param isMonitoring 是否正在监测
     * @param stopIntent 停止监测的 PendingIntent
     * @return 前台服务通知
     */
    fun buildForegroundServiceNotification(
        context: Context,
        isMonitoring: Boolean,
        stopIntent: PendingIntent?
    ): Notification {
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(context, ChannelId.MONITOR)
            .setSmallIcon(android.R.drawable.ic_menu_compass) // 使用系统默认图标
            .setContentTitle("环境健康")
            .setContentText(if (isMonitoring) "正在监测环境噪音与光照..." else "环境监测已暂停")
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC) // 添加可见性
            .setShowWhen(true) // 显示时间

        if (isMonitoring && stopIntent != null) {
            builder.addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "停止监测",
                stopIntent
            )
        }

        return builder.build()
    }

    /**
     * 发送健康提醒通知
     *
     * @param context 上下文
     * @param notificationId 通知 ID
     * @param title 标题
     * @param content 内容
     */
    fun sendHealthAlertNotification(
        context: Context,
        notificationId: Int,
        title: String,
        content: String
    ) {
        val contentIntent = PendingIntent.getActivity(
            context,
            notificationId,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, ChannelId.HEALTH_ALERT)
            .setSmallIcon(android.R.drawable.ic_dialog_alert) // 使用系统默认图标，后续可替换为专用图标
            .setContentTitle(title)
            .setContentText(content)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(notificationId, notification)

        LogX.i("NotificationHelper", "发送健康提醒通知: $title")
    }
}
