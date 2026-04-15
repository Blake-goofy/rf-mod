package com.bbecker.whmkeyboardtoggle

import android.content.Context

object AppLogger {
    @Suppress("UNUSED_PARAMETER")
    fun i(context: Context, tag: String, message: String) {
    }

    @Suppress("UNUSED_PARAMETER")
    fun w(context: Context, tag: String, message: String, error: Throwable? = null) {
    }
}