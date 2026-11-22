package org.opencrown.app.data

import android.content.Context

/** First-run state. Finishing onboarding is a user action, not a timer. */
class OnboardingStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("org.opencrown.onboarding", Context.MODE_PRIVATE)

    fun isComplete(): Boolean = prefs.getBoolean(KEY_COMPLETE, false)

    fun markComplete() {
        prefs.edit().putBoolean(KEY_COMPLETE, true).apply()
    }

    fun reset() {
        prefs.edit().remove(KEY_COMPLETE).apply()
    }

    private companion object {
        const val KEY_COMPLETE = "complete.v1"
    }
}
