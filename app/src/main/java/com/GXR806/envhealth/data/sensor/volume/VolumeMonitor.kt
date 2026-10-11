package com.GXR806.envhealth.data.sensor.volume

import android.content.Context
import android.database.ContentObserver
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import com.GXR806.envhealth.domain.model.DeviceType

/** 只读系统音量，不请求 WRITE_SETTINGS，也不录音。所有方法均由主线程调用。 */
data class VolumeReading(val device: DeviceType?, val deviceLabel: String, val percent: Float,
                         val playing: Boolean, val mono: Long, val wall: Long)

class VolumeMonitor(context: Context) {
    private val resolver = context.applicationContext.contentResolver
    private val audio = context.getSystemService(AudioManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var running = false
    private var listener: ((VolumeReading?, String?) -> Unit)? = null
    private var musicActive = false
    private var lastMusicPoll = Long.MIN_VALUE
    private var lastPercent = Float.NaN
    private val observer = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) {
            // Settings.System 也会收到无关变化；只有实际媒体音量变化才强制刷新播放状态。
            refresh(false) // refresh 内检测实际音量变化并强制读取播放状态。
        }
    }
    private val callback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) { refresh(true) }
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) { refresh(true) }
    }
    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            refresh(true) // 原有tick每5秒实读播放，避免观察者刷新挪动轮询窗口。
            handler.postDelayed(this, PlaybackPollPolicy.INTERVAL_MS)
        }
    }

    fun start(onReading: (VolumeReading?, String?) -> Unit): Boolean {
        if (running) return true
        listener = onReading
        running = true
        try {
            resolver.registerContentObserver(Settings.System.CONTENT_URI, true, observer)
            audio.registerAudioDeviceCallback(callback, handler)
            refresh(true)
            handler.postDelayed(tick, PlaybackPollPolicy.INTERVAL_MS)
            return true
        } catch (e: Exception) {
            onReading(null, "音量监测启动失败：${e.message}")
            stop()
            return false
        }
    }

    private fun readPercent(): Float {
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        return if (max > 0) audio.getStreamVolume(AudioManager.STREAM_MUSIC) * 100f / max else 0f
    }

    private fun refresh(forceMusic: Boolean) {
        if (!running) return
        try {
            val mono = SystemClock.elapsedRealtime()
            val percent = readPercent()
            val volumeChanged = lastPercent.isNaN() || percent != lastPercent
            lastPercent = percent
            if (PlaybackPollPolicy.shouldPoll(forceMusic, volumeChanged, lastMusicPoll, mono)) {
                musicActive = audio.isMusicActive
                lastMusicPoll = mono
            }
            val devices = audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            val device = devices.firstOrNull { it.type in setOf(AudioDeviceInfo.TYPE_WIRED_HEADSET,
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_USB_HEADSET,
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP) }
            val type = when (device?.type) {
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> DeviceType.BLUETOOTH
                null -> null
                else -> DeviceType.WIRED
            }
            val label = when (device?.type) {
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "蓝牙 A2DP 耳机"
                AudioDeviceInfo.TYPE_USB_HEADSET -> "USB 耳机"
                null -> "未连接耳机"
                else -> "有线耳机"
            }
            listener?.invoke(VolumeReading(type, label, percent, musicActive, mono, System.currentTimeMillis()), null)
        } catch (e: Exception) { listener?.invoke(null, "读取耳机状态失败：${e.message}") }
    }

    fun stop() {
        if (!running) return
        running = false
        handler.removeCallbacks(tick)
        try { resolver.unregisterContentObserver(observer) }
        catch (e: Exception) { listener?.invoke(null, "注销音量监听失败：${e.message}") }
        try { audio.unregisterAudioDeviceCallback(callback) }
        catch (e: Exception) { listener?.invoke(null, "注销设备监听失败：${e.message}") }
        listener = null
    }
}
