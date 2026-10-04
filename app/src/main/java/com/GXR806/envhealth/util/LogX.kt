package com.GXR806.envhealth.util

import android.util.Log
import com.GXR806.envhealth.BuildConfig

/** 统一日志入口；正式版本不输出日志，且禁止记录原始采集内容。 */
object LogX {
    fun d(tag: String, message: String) {
        if (BuildConfig.DEBUG) Log.d(formatTag(tag), message)
    }

    fun i(tag: String, message: String) {
        if (BuildConfig.DEBUG) Log.i(formatTag(tag), message)
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        if (BuildConfig.DEBUG) Log.w(formatTag(tag), message, throwable)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (BuildConfig.DEBUG) Log.e(formatTag(tag), message, throwable)
    }

    private fun formatTag(tag: String): String = "${Constants.TAG_PREFIX}:$tag"
}
