package com.pratik.divert.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.pratik.divert.R
import com.pratik.divert.data.Settings
import com.pratik.divert.telephony.ForwardingController
import com.pratik.divert.telephony.Telephony

class DivertWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { id -> appWidgetManager.updateAppWidget(id, buildViews(context)) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_TOGGLE) {
            if (!Telephony.hasPhonePermission(context)) {
                Toast.makeText(context, "Open Divert and grant phone permission", Toast.LENGTH_SHORT).show()
                return
            }
            val pending = goAsync()
            ForwardingController.toggle(context) { ok, msg ->
                refresh(context)
                if (!ok) Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_TOGGLE = "com.pratik.divert.action.WIDGET_TOGGLE"

        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, DivertWidget::class.java))
            ids.forEach { id -> manager.updateAppWidget(id, buildViews(context)) }
        }

        private fun buildViews(context: Context): RemoteViews {
            val active = Settings.get(context).isForwardingActive
            return RemoteViews(context.packageName, R.layout.widget_divert).apply {
                setImageViewResource(
                    R.id.widget_dot,
                    if (active) R.drawable.circle_on else R.drawable.circle_off
                )
                setTextViewText(R.id.widget_status, if (active) "ON" else "OFF")
                setTextColor(
                    R.id.widget_status,
                    ContextCompat.getColor(context, if (active) R.color.accent else R.color.muted)
                )
                setOnClickPendingIntent(R.id.widget_root, togglePendingIntent(context))
            }
        }

        private fun togglePendingIntent(context: Context): PendingIntent {
            val intent = Intent(context, DivertWidget::class.java).setAction(ACTION_TOGGLE)
            return PendingIntent.getBroadcast(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}
