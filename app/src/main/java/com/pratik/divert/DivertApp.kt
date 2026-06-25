package com.pratik.divert

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager

class DivertApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val channel = NotificationChannel(
            CHANNEL_WINDOW,
            getString(R.string.channel_window_name),
            NotificationManager.IMPORTANCE_MIN
        ).apply {
            description = getString(R.string.channel_window_desc)
            setShowBadge(false)
            enableLights(false)
            enableVibration(false)
            setSound(null, null)
        }
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_WINDOW = "divert_window"
    }
}
