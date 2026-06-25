package com.pratik.divert.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.pratik.divert.data.Settings
import java.util.Calendar

/**
 * Schedules exact, Doze-friendly alarms that drive the whole automation:
 *  - window start  -> start the unlock listener
 *  - window end    -> stop the unlock listener
 *  - auto-on       -> enable forwarding on selected weekdays
 *
 * Each alarm reschedules its own next occurrence, so there are at most three
 * pending alarms at any time — negligible battery impact.
 */
object AlarmScheduler {

    const val ACTION_WINDOW_START = "com.pratik.divert.WINDOW_START"
    const val ACTION_WINDOW_END = "com.pratik.divert.WINDOW_END"
    const val ACTION_AUTO_ON = "com.pratik.divert.AUTO_ON"

    private const val RC_START = 101
    private const val RC_END = 102
    private const val RC_AUTO_ON = 103

    fun scheduleAll(context: Context) {
        val s = Settings.get(context)
        setExact(context, ACTION_WINDOW_START, nextTrigger(s.windowStart, null))
        setExact(context, ACTION_WINDOW_END, nextTrigger(s.windowEnd, null))
        if (s.autoOnEnabled && s.forwardingNumber.isNotBlank()) {
            setExact(context, ACTION_AUTO_ON, nextTrigger(s.autoOnTime, s.autoOnDays))
        } else {
            cancel(context, ACTION_AUTO_ON)
        }
    }

    /** Fire a window-start almost immediately (used after boot when already inside the window). */
    fun scheduleImmediateStart(context: Context) {
        setExact(context, ACTION_WINDOW_START, System.currentTimeMillis() + 2_000L)
    }

    fun rescheduleNext(context: Context, action: String) {
        val s = Settings.get(context)
        when (action) {
            ACTION_WINDOW_START -> setExact(context, action, nextTrigger(s.windowStart, null))
            ACTION_WINDOW_END -> setExact(context, action, nextTrigger(s.windowEnd, null))
            ACTION_AUTO_ON ->
                if (s.autoOnEnabled) setExact(context, action, nextTrigger(s.autoOnTime, s.autoOnDays))
        }
    }

    /** Next time the watch window will open (for diagnostics display). */
    fun nextWindowStart(context: Context): Long {
        val s = Settings.get(context)
        return nextTrigger(s.windowStart, null)
    }

    /** Next scheduled auto-enable, or null if disabled/no number (for diagnostics display). */
    fun nextAutoOn(context: Context): Long? {
        val s = Settings.get(context)
        return if (s.autoOnEnabled && s.forwardingNumber.isNotBlank())
            nextTrigger(s.autoOnTime, s.autoOnDays) else null
    }

    private fun nextTrigger(minuteOfDay: Int, days: Set<Int>?): Long {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, minuteOfDay / 60)
            set(Calendar.MINUTE, minuteOfDay % 60)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (cal.timeInMillis <= System.currentTimeMillis()) cal.add(Calendar.DAY_OF_YEAR, 1)
        if (!days.isNullOrEmpty()) {
            var guard = 0
            while (cal.get(Calendar.DAY_OF_WEEK) !in days && guard < 8) {
                cal.add(Calendar.DAY_OF_YEAR, 1)
                guard++
            }
        }
        return cal.timeInMillis
    }

    private fun requestCode(action: String): Int = when (action) {
        ACTION_WINDOW_START -> RC_START
        ACTION_WINDOW_END -> RC_END
        else -> RC_AUTO_ON
    }

    private fun pendingIntent(context: Context, action: String): PendingIntent {
        val intent = Intent(context, WindowReceiver::class.java).setAction(action)
        return PendingIntent.getBroadcast(
            context,
            requestCode(action),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun setExact(context: Context, action: String, triggerAtMillis: Long) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = pendingIntent(context, action)
        val canExact =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) am.canScheduleExactAlarms() else true
        if (canExact) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
        } else {
            // Fall back to inexact (still Doze-friendly) if exact alarms aren't permitted.
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
        }
    }

    private fun cancel(context: Context, action: String) {
        context.getSystemService(AlarmManager::class.java)?.cancel(pendingIntent(context, action))
    }
}
