package com.pratik.divert.boot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.pratik.divert.alarm.AlarmScheduler
import com.pratik.divert.data.EventLog
import com.pratik.divert.data.Settings
import com.pratik.divert.widget.DivertWidget

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        EventLog.add(context, "Device booted \u2014 alarms rescheduled")
        AlarmScheduler.scheduleAll(context)

        val s = Settings.get(context)
        if (s.autoOffOnUnlock && s.isWithinWindow()) {
            // Use an (exempt) alarm to start the foreground service from the background.
            AlarmScheduler.scheduleImmediateStart(context)
        }
        DivertWidget.refresh(context)
    }
}
