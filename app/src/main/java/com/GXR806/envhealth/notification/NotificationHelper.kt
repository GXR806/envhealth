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
                setShowBadge(true)
                enableVibration(true)
                enableLights(true)
            }

            notificationManager.createNotificationChannels(listOf(monitorChannel, healthAlertChannel))
            LogX.i("NotificationHelper", "通知渠道创建成功")
        }
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
