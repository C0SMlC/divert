package com.pratik.divert.service

import android.app.KeyguardManager
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.pratik.divert.DivertApp
import com.pratik.divert.MainActivity
import com.pratik.divert.R
import com.pratik.divert.data.EventLog
import com.pratik.divert.data.Settings
import com.pratik.divert.telephony.ForwardingController
import com.pratik.divert.widget.DivertWidget

/**
 * Runs only during the configured window (started/stopped by alarms).
 * Listens for device unlock and cancels call forwarding once per day.
 * Purely event-driven — no polling, no wakelocks.
 */
class UnlockListenerService : Service() {

    private var receiver: BroadcastReceiver? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIF_ID, buildNotification())
        EventLog.add(this, "Watching for unlock (window active)")
        val r = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    Intent.ACTION_USER_PRESENT -> onUnlock("unlock", logIdle = true)
                    Intent.ACTION_SCREEN_ON -> {
                        // Phones without a secure lock screen don't broadcast USER_PRESENT,
                        // so treat "screen on while not locked" as an unlock too.
                        val km = getSystemService(KeyguardManager::class.java)
                        if (km == null || !km.isKeyguardLocked) onUnlock("wake", logIdle = false)
                    }
                }
            }
        }
        receiver = r
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        ContextCompat.registerReceiver(this, r, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Re-assert foreground state if the system restarts us.
        startForeground(NOTIF_ID, buildNotification())
        return START_STICKY
    }

    private fun onUnlock(source: String, logIdle: Boolean) {
        val s = Settings.get(this)
        if (!s.autoOffOnUnlock || !s.isWithinWindow()) return
        if (s.isForwardingActive) {
            EventLog.add(this, "Unlock detected ($source) \u2192 cancelling forwarding")
            ForwardingController.disable(this) { ok, msg ->
                DivertWidget.refresh(this)
                EventLog.add(this, if (ok) "Auto-cancel succeeded" else "Auto-cancel failed: $msg")
            }
        } else if (logIdle) {
            EventLog.add(this, "Unlock detected ($source) \u2014 forwarding already off")
        }
    }

    override fun onDestroy() {
        receiver?.let { r -> runCatching { unregisterReceiver(r) } }
        receiver = null
        EventLog.add(this, "Stopped watching (window ended)")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification {
        val tap = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, DivertApp.CHANNEL_WINDOW)
            .setSmallIcon(R.drawable.ic_stat_divert)
            .setContentTitle("Divert is watching")
            .setContentText("Unlocking now will cancel call forwarding")
            .setOngoing(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(tap)
            .build()
    }

    companion object {
        private const val NOTIF_ID = 42
    }
}
