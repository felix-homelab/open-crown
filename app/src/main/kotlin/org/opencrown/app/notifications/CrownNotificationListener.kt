package org.opencrown.app.notifications

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Local notification ingress.
 *
 * The service captures content for the Notifications screen. It does not send
 * anything anywhere: there is no watch transport in this build. Content stays
 * in memory and is never logged.
 */
class CrownNotificationListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val status = sbn ?: return
        if (status.packageName == packageName) return
        val notification = status.notification ?: return
        val extras = notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_TITLE_BIG)?.toString()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
        val appLabel = runCatching {
            packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(status.packageName, 0)
            ).toString()
        }.getOrNull()
        val captured = NotificationSanitizer.from(
            key = status.packageName + ":" + status.id + ":" + status.postTime,
            packageName = status.packageName,
            appLabel = appLabel,
            title = title,
            text = text,
            postedAtMillis = status.postTime,
            category = notification.category,
            ongoing = status.isOngoing,
        ) ?: return
        NotificationIngress.record(captured)
    }
}
