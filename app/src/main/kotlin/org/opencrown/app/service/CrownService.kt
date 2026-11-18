package org.opencrown.app.service

import android.app.Service
import android.content.Intent
import android.os.IBinder

/**
 * Foreground service that will host the session and own the agent lifetime.
 *
 * It is declared in the manifest now so the permission and foreground-service
 * shape is reviewable, but it is not started yet. Phase 4 adds the notification
 * channel, the stop action, and the watchdog. See docs/02-architecture.md
 * section 6 and docs/adr/0008-resident-agent.md.
 */
class CrownService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
}
