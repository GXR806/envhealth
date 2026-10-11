package com.GXR806.envhealth.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import com.GXR806.envhealth.EnvHealthApplication
import com.GXR806.envhealth.domain.analysis.ReminderPolicy
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.withLock

/** 私有显式广播。动作仅由用户点击 immutable PendingIntent 执行，不启动 Activity。 */
class HealthActionReceiver : BroadcastReceiver() {
    companion object { const val ACTION = "com.GXR806.envhealth.action.HEALTH_RESPONSE" }
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        val id = intent.getStringExtra("event") ?: return
        if (runCatching { java.util.UUID.fromString(id) }.isFailure) return
        val type = runCatching { ReminderPolicy.Type.valueOf(intent.getStringExtra("type") ?: "") }.getOrNull() ?: return
        val action = runCatching { ReminderPolicy.Action.valueOf(intent.getStringExtra("operation") ?: "") }.getOrNull() ?: return
        if (intent.data?.toString() != "envhealth://reminder/$id/${action.name}") return
        val pending = goAsync()
        val owner = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        owner.launch {
            try {
                withTimeout(8_000L) {
                    val container = (context.applicationContext as EnvHealthApplication).appContainer
                    container.maintenanceGate.mutex.withLock {
                        val settings = container.settingsRepository.snapshot.first()
                        if (settings.maintenanceState.isNotEmpty()) return@withLock
                        val store = container.settingsRepository.reminderStore
                        val now = System.currentTimeMillis()
                        store.claimAction(id, type, action, now) ?: return@withLock
                        if (action == ReminderPolicy.Action.LOWER_VOLUME) {
                            val audio = context.getSystemService(AudioManager::class.java)
                            val current = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
                            val target = ReminderPolicy.lowerVolumeTarget(current,
                                audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC), settings.volumeThresholdPercent)
                            if (target != null) {
                                // 用户明确点击后执行；无需 WRITE_SETTINGS，绝不提高当前媒体音量。
                                audio.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
                                if (audio.getStreamVolume(AudioManager.STREAM_MUSIC) < current) store.markEffective(id)
                            }
                        }
                        context.getSystemService(android.app.NotificationManager::class.java).cancel(3000 + type.ordinal)
                    }
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { android.util.Log.w("HealthAction", "提醒操作未完成", e) }
            finally { pending.finish(); owner.cancel() }
        }
    }
}
