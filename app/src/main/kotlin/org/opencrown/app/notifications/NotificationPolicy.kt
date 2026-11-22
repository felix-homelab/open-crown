package org.opencrown.app.notifications

import android.content.Context

/**
 * Local per-app capture policy.
 *
 * This controls only whether Open Crown reads and retains an app's notification
 * content on this phone. It does not imply watch mirroring: no transport exists.
 * The policy is deliberately small and local; clearing app data resets it.
 */
class NotificationPolicy(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun mutedPackages(): Set<String> =
        prefs.getStringSet(KEY_MUTED, emptySet())?.toSet() ?: emptySet()

    fun isCaptureEnabled(packageName: String): Boolean =
        NotificationPolicyRules.shouldCapture(mutedPackages(), packageName)

    fun setMuted(packageName: String, muted: Boolean) {
        if (packageName.isBlank()) return
        val next = mutedPackages().toMutableSet()
        if (muted) next.add(packageName) else next.remove(packageName)
        prefs.edit().putStringSet(KEY_MUTED, next).apply()
    }

    fun clear() {
        prefs.edit().remove(KEY_MUTED).apply()
    }

    private companion object {
        const val PREFS = "org.opencrown.notifications"
        const val KEY_MUTED = "muted.v1"
    }
}

/** Pure decision rules, kept testable without Android. */
object NotificationPolicyRules {
    fun shouldCapture(muted: Set<String>, packageName: String): Boolean =
        packageName.isNotBlank() && packageName !in muted
}
