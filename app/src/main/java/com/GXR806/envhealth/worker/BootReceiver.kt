package com.GXR806.envhealth.worker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.*

/** BOOT_COMPLETED 为受保护系统广播；只恢复唯一周期配置，绝不启动采集服务。 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        val owner = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        owner.launch {
            try { withTimeout(8_000L) { ReminderScheduler.restoreIfConsented(context.applicationContext) } }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { android.util.Log.w("BootReceiver", "周期工作恢复失败", e) }
            finally { pending.finish(); owner.cancel() }
        }
    }
}
