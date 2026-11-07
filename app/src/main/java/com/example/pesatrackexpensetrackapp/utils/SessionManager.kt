package com.example.pesatrackexpensetrackapp.utils

import android.content.Context

/** Keeps the signed-in app session alive only while the user is active. */
class SessionManager(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun isExpired(nowMillis: Long = System.currentTimeMillis()): Boolean {
        val lastActivity = preferences.getLong(KEY_LAST_ACTIVITY, 0L)
        if (lastActivity == 0L) return false
        // Treat a backwards clock adjustment as expired rather than extending a session indefinitely.
        return nowMillis < lastActivity || nowMillis - lastActivity >= SESSION_TIMEOUT_MILLIS
    }

    fun recordActivity(nowMillis: Long = System.currentTimeMillis()) {
        preferences.edit().putLong(KEY_LAST_ACTIVITY, nowMillis).apply()
    }

    fun clear() {
        preferences.edit().remove(KEY_LAST_ACTIVITY).apply()
    }

    companion object {
        const val SESSION_TIMEOUT_MILLIS = 30L * 60L * 1000L
        private const val PREFERENCES_NAME = "pesatrack_session"
        private const val KEY_LAST_ACTIVITY = "last_activity_at"
    }
}
