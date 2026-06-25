package com.pratik.divert.data

import android.content.Context
import android.content.SharedPreferences
import java.util.Calendar

/**
 * Lightweight synchronous settings store backed by SharedPreferences.
 * Synchronous reads keep widget/alarm/broadcast code simple and fast.
 */
class Settings private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun registerListener(l: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.registerOnSharedPreferenceChangeListener(l)

    fun unregisterListener(l: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.unregisterOnSharedPreferenceChangeListener(l)

    // --- Forwarding target -------------------------------------------------
    var forwardingNumber: String
        get() = prefs.getString(KEY_NUMBER, "").orEmpty()
        set(v) = prefs.edit().putString(KEY_NUMBER, v.trim()).apply()

    // --- MMI / USSD codes (editable so any carrier works) ------------------
    var activateTemplate: String
        get() = prefs.getString(KEY_ACTIVATE, DEFAULT_ACTIVATE)!!
        set(v) = prefs.edit().putString(KEY_ACTIVATE, v.trim()).apply()

    var deactivateCode: String
        get() = prefs.getString(KEY_DEACTIVATE, DEFAULT_DEACTIVATE)!!
        set(v) = prefs.edit().putString(KEY_DEACTIVATE, v.trim()).apply()

    var interrogateCode: String
        get() = prefs.getString(KEY_INTERROGATE, DEFAULT_INTERROGATE)!!
        set(v) = prefs.edit().putString(KEY_INTERROGATE, v.trim()).apply()

    /** Activation code with the configured number substituted in. */
    fun activateCode(): String =
        activateTemplate.replace("{number}", forwardingNumber)

    // --- Window (minutes since midnight, local time) -----------------------
    var windowStart: Int
        get() = prefs.getInt(KEY_WIN_START, DEFAULT_WIN_START)
        set(v) = prefs.edit().putInt(KEY_WIN_START, v).apply()

    var windowEnd: Int
        get() = prefs.getInt(KEY_WIN_END, DEFAULT_WIN_END)
        set(v) = prefs.edit().putInt(KEY_WIN_END, v).apply()

    // --- Automation toggles ------------------------------------------------
    var autoOffOnUnlock: Boolean
        get() = prefs.getBoolean(KEY_AUTO_OFF, true)
        set(v) = prefs.edit().putBoolean(KEY_AUTO_OFF, v).apply()

    var autoOnEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_ON, false)
        set(v) = prefs.edit().putBoolean(KEY_AUTO_ON, v).apply()

    var autoOnTime: Int
        get() = prefs.getInt(KEY_AUTO_ON_TIME, DEFAULT_AUTO_ON_TIME)
        set(v) = prefs.edit().putInt(KEY_AUTO_ON_TIME, v).apply()

    /** Days of week using Calendar constants (SUNDAY=1 .. SATURDAY=7). */
    var autoOnDays: Set<Int>
        get() = prefs.getStringSet(KEY_AUTO_ON_DAYS, DEFAULT_DAYS)!!
            .mapNotNull { it.toIntOrNull() }.toSet()
        set(v) = prefs.edit()
            .putStringSet(KEY_AUTO_ON_DAYS, v.map { it.toString() }.toSet())
            .apply()

    // --- SIM ---------------------------------------------------------------
    /** Subscription id, or -1 to use the system default SIM. */
    var subscriptionId: Int
        get() = prefs.getInt(KEY_SUB_ID, -1)
        set(v) = prefs.edit().putInt(KEY_SUB_ID, v).apply()

    // --- Last known forwarding state (source of truth for the UI/widget) ---
    var isForwardingActive: Boolean
        get() = prefs.getBoolean(KEY_ACTIVE, false)
        set(v) = prefs.edit()
            .putBoolean(KEY_ACTIVE, v)
            .putLong(KEY_UPDATED, System.currentTimeMillis())
            .apply()

    val lastUpdated: Long
        get() = prefs.getLong(KEY_UPDATED, 0L)

    // --- Helpers -----------------------------------------------------------
    fun isWithinWindow(now: Calendar = Calendar.getInstance()): Boolean {
        val minutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        val start = windowStart
        val end = windowEnd
        return if (start <= end) minutes in start until end
        else minutes >= start || minutes < end
    }

    companion object {
        private const val FILE = "divert_prefs"

        private const val KEY_NUMBER = "number"
        private const val KEY_ACTIVATE = "activate"
        private const val KEY_DEACTIVATE = "deactivate"
        private const val KEY_INTERROGATE = "interrogate"
        private const val KEY_WIN_START = "win_start"
        private const val KEY_WIN_END = "win_end"
        private const val KEY_AUTO_OFF = "auto_off"
        private const val KEY_AUTO_ON = "auto_on"
        private const val KEY_AUTO_ON_TIME = "auto_on_time"
        private const val KEY_AUTO_ON_DAYS = "auto_on_days"
        private const val KEY_SUB_ID = "sub_id"
        private const val KEY_ACTIVE = "active"
        private const val KEY_UPDATED = "updated"

        // Jio / standard GSM defaults
        const val DEFAULT_ACTIVATE = "*21*{number}#"
        const val DEFAULT_DEACTIVATE = "##21#"
        const val DEFAULT_INTERROGATE = "*#21#"

        private const val DEFAULT_WIN_START = 12 * 60   // 12:00
        private const val DEFAULT_WIN_END = 17 * 60      // 17:00
        private const val DEFAULT_AUTO_ON_TIME = 9 * 60 + 30 // 09:30

        // Mon..Fri
        private val DEFAULT_DAYS = setOf(
            Calendar.MONDAY.toString(),
            Calendar.TUESDAY.toString(),
            Calendar.WEDNESDAY.toString(),
            Calendar.THURSDAY.toString(),
            Calendar.FRIDAY.toString()
        )

        @Volatile
        private var INSTANCE: Settings? = null

        fun get(context: Context): Settings =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Settings(context).also { INSTANCE = it }
            }
    }
}
