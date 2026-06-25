package com.pratik.divert.data

import android.content.Context

/**
 * Tiny prefs-backed ring buffer of timestamped events, surfaced in the app so the
 * user can see exactly what the automation is doing (unlocks, alarms, USSD results).
 */
object EventLog {
    private const val FILE = "divert_log"
    private const val KEY = "events"
    private const val MAX = 50

    fun add(context: Context, message: String) {
        val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val line = "${System.currentTimeMillis()}|$message"
        val existing = prefs.getString(KEY, "").orEmpty()
        val lines = if (existing.isBlank()) ArrayDeque() else ArrayDeque(existing.split("\n"))
        lines.addFirst(line)
        while (lines.size > MAX) lines.removeLast()
        prefs.edit().putString(KEY, lines.joinToString("\n")).apply()
    }

    fun entries(context: Context): List<Pair<Long, String>> {
        val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val existing = prefs.getString(KEY, "").orEmpty()
        if (existing.isBlank()) return emptyList()
        return existing.split("\n").mapNotNull { raw ->
            val i = raw.indexOf('|')
            if (i <= 0) return@mapNotNull null
            val ts = raw.substring(0, i).toLongOrNull() ?: return@mapNotNull null
            ts to raw.substring(i + 1)
        }
    }

    fun clear(context: Context) {
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit().clear().apply()
    }
}
