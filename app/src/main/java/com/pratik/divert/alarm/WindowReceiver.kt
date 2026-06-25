package com.pratik.divert.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.pratik.divert.data.EventLog
import com.pratik.divert.data.Settings
import com.pratik.divert.service.ServiceControl
import com.pratik.divert.telephony.ForwardingController
import com.pratik.divert.widget.DivertWidget
import java.util.Calendar

class WindowReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val s = Settings.get(context)
        when (intent.action) {
            AlarmScheduler.ACTION_WINDOW_START -> {
                EventLog.add(context, "Window started (alarm fired)")
                if (s.autoOffOnUnlock) ServiceControl.start(context)
                AlarmScheduler.rescheduleNext(context, AlarmScheduler.ACTION_WINDOW_START)
            }

            AlarmScheduler.ACTION_WINDOW_END -> {
                EventLog.add(context, "Window ended (alarm fired)")
                ServiceControl.stop(context)
                AlarmScheduler.rescheduleNext(context, AlarmScheduler.ACTION_WINDOW_END)
            }

            AlarmScheduler.ACTION_AUTO_ON -> {
                val today = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
                if (s.autoOnEnabled && today in s.autoOnDays && s.forwardingNumber.isNotBlank()) {
                    EventLog.add(context, "Auto-enable alarm fired \u2192 turning forwarding ON")
                    val pending = goAsync()
                    ForwardingController.enable(context) { _, _ ->
                        DivertWidget.refresh(context)
                        pending.finish()
                    }
                } else {
                    EventLog.add(
                        context,
                        "Auto-enable alarm fired \u2014 skipped (enabled=${s.autoOnEnabled}, " +
                            "todayMatches=${today in s.autoOnDays}, hasNumber=${s.forwardingNumber.isNotBlank()})"
                    )
                }
                AlarmScheduler.rescheduleNext(context, AlarmScheduler.ACTION_AUTO_ON)
            }
        }
    }
}
