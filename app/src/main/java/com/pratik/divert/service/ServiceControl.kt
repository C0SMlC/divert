package com.pratik.divert.service

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/** Starts/stops the unlock-listener foreground service. */
object ServiceControl {

    fun start(context: Context) {
        val intent = Intent(context, UnlockListenerService::class.java)
        runCatching { ContextCompat.startForegroundService(context, intent) }
    }

    fun stop(context: Context) {
        runCatching { context.stopService(Intent(context, UnlockListenerService::class.java)) }
    }
}
