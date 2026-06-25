package com.pratik.divert.telephony

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat

data class SimOption(val subscriptionId: Int, val label: String)

object Telephony {

    fun hasPhonePermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) ==
            PackageManager.PERMISSION_GRANTED

    fun hasReadPhoneState(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) ==
            PackageManager.PERMISSION_GRANTED

    fun telephonyFor(context: Context, subId: Int): TelephonyManager {
        val tm = context.getSystemService(TelephonyManager::class.java)
        return if (subId >= 0) tm.createForSubscriptionId(subId) else tm
    }

    fun simOptions(context: Context): List<SimOption> {
        if (!hasReadPhoneState(context)) return emptyList()
        val sm = context.getSystemService(SubscriptionManager::class.java) ?: return emptyList()
        val list: List<SubscriptionInfo> = try {
            sm.activeSubscriptionInfoList ?: emptyList()
        } catch (e: SecurityException) {
            emptyList()
        }
        return list.map { info ->
            val name = info.displayName?.toString()?.takeIf { it.isNotBlank() }
                ?: info.carrierName?.toString()?.takeIf { it.isNotBlank() }
                ?: "SIM ${info.simSlotIndex + 1}"
            SimOption(info.subscriptionId, "$name · slot ${info.simSlotIndex + 1}")
        }
    }
}
