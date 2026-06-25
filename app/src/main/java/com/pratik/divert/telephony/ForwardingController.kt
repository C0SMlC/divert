package com.pratik.divert.telephony

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.telephony.TelephonyManager
import com.pratik.divert.data.EventLog
import com.pratik.divert.data.Settings
import com.pratik.divert.widget.DivertWidget

/**
 * Sends call-forwarding MMI/USSD codes via [TelephonyManager.sendUssdRequest]
 * (silent, no dialer UI) and keeps the locally tracked state in sync.
 */
object ForwardingController {

    /** Result delivered on the main thread. */
    fun enable(context: Context, callback: (Boolean, String) -> Unit) {
        val s = Settings.get(context)
        if (s.forwardingNumber.isBlank()) {
            callback(false, "Set a forwarding number first")
            return
        }
        send(context, s.activateCode(), targetActive = true, callback)
    }

    fun disable(context: Context, callback: (Boolean, String) -> Unit) {
        send(context, Settings.get(context).deactivateCode, targetActive = false, callback)
    }

    fun toggle(context: Context, callback: (Boolean, String) -> Unit) {
        if (Settings.get(context).isForwardingActive) disable(context, callback)
        else enable(context, callback)
    }

    private fun send(
        context: Context,
        code: String,
        targetActive: Boolean,
        callback: (Boolean, String) -> Unit
    ) {
        val app = context.applicationContext
        if (!Telephony.hasPhonePermission(app)) {
            callback(false, "Phone permission needed")
            return
        }
        val s = Settings.get(app)
        val tm = Telephony.telephonyFor(app, s.subscriptionId)
        val handler = Handler(Looper.getMainLooper())
        try {
            tm.sendUssdRequest(code, object : TelephonyManager.UssdResponseCallback() {
                override fun onReceiveUssdResponse(
                    telephonyManager: TelephonyManager,
                    request: String,
                    response: CharSequence
                ) {
                    s.isForwardingActive = targetActive
                    DivertWidget.refresh(app)
                    EventLog.add(app, if (targetActive) "Forwarding turned ON" else "Forwarding turned OFF")
                    callback(
                        true,
                        response.toString().ifBlank {
                            if (targetActive) "Forwarding enabled" else "Forwarding cancelled"
                        }
                    )
                }

                override fun onReceiveUssdResponseFailed(
                    telephonyManager: TelephonyManager,
                    request: String,
                    failureCode: Int
                ) {
                    EventLog.add(app, "Carrier rejected code (code $failureCode)")
                    callback(false, "Carrier rejected the request (code $failureCode)")
                }
            }, handler)
        } catch (e: SecurityException) {
            callback(false, "Phone permission needed")
        } catch (e: Exception) {
            callback(false, e.message ?: "Could not send the code")
        }
    }
}
