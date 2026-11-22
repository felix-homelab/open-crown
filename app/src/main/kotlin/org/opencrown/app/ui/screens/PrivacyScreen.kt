package org.opencrown.app.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import org.opencrown.app.model.CrownSnapshot
import org.opencrown.app.ui.components.CrownButton
import org.opencrown.app.ui.components.CrownButtonStyle
import org.opencrown.app.ui.components.CrownInsetGroup
import org.opencrown.app.ui.components.CrownNote
import org.opencrown.app.ui.components.CrownRow
import org.opencrown.app.ui.components.CrownScaffold
import org.opencrown.app.ui.components.CrownSectionHeader
import org.opencrown.app.ui.components.CrownStatusPill
import org.opencrown.app.ui.components.CrownTone

@Composable
fun PrivacyScreen(
    snapshot: CrownSnapshot?,
    onBack: () -> Unit,
    onRequestPermissions: () -> Unit,
) {
    val context = LocalContext.current
    var access by remember { mutableStateOf(hasNotificationAccess(context)) }
    val lifecycleOwner = context as? LifecycleOwner
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                access = hasNotificationAccess(context)
            }
        }
        lifecycleOwner?.lifecycle?.addObserver(observer)
        onDispose { lifecycleOwner?.lifecycle?.removeObserver(observer) }
    }
    val bluetoothGranted = snapshot?.bluetooth?.scanPermission == true

    CrownScaffold(
        title = "Privacy",
        subtitle = "What Open Crown knows and keeps",
        onBack = onBack,
    ) {
        item(key = "intro") {
            CrownNote(
                title = "Local by default",
                message = "Open Crown has no account system, no analytics and no server. Watch data is not uploaded anywhere in this build.",
                tone = CrownTone.INFO,
            )
            Spacer(Modifier.height(20.dp))
        }
        item(key = "data-header") {
            CrownSectionHeader("Data on this phone")
        }
        item(key = "data") {
            CrownInsetGroup(
                footer = "Uninstalling the app or clearing its data removes local records. Notification content is memory-only and disappears when the process ends.",
            ) {
                CrownRow(
                    title = "Pairing metadata",
                    subtitle = "Sealed with Android Keystore; no Apple account password is stored",
                    showDivider = true,
                )
                CrownRow(
                    title = "Notification content",
                    subtitle = "Captured in memory only, capped, and never sent to a watch in this build",
                    showDivider = true,
                )
                CrownRow(
                    title = "Diagnostics",
                    subtitle = "Model, Android, kernel and probe results; no serial number or pairing key",
                    showDivider = true,
                )
                CrownRow(
                    title = "Health and activity",
                    subtitle = "Not collected. The Health screen has no sample store yet",
                    showDivider = true,
                )
                CrownRow(
                    title = "Apple account",
                    subtitle = "Never requested, stored or transmitted",
                    showDivider = false,
                )
            }
        }
        item(key = "permissions-header") {
            Spacer(Modifier.height(24.dp))
            CrownSectionHeader("Permissions")
        }
        item(key = "permissions") {
            CrownInsetGroup(
                footer = "A permission is requested only when its screen needs it. Denying one leaves the app usable and unsupported features visibly unavailable.",
            ) {
                CrownRow(
                    title = "Nearby devices",
                    subtitle = "Bluetooth scanning for advertisements",
                    onClick = if (bluetoothGranted) null else onRequestPermissions,
                    trailing = {
                        CrownStatusPill(
                            if (bluetoothGranted) "Granted" else "Not granted",
                            if (bluetoothGranted) CrownTone.SUCCESS else CrownTone.WARNING,
                        )
                    },
                )
                CrownRow(
                    title = "Notification access",
                    subtitle = "Reads notification content for local capture only",
                    onClick = {
                        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        runCatching { context.startActivity(intent) }
                    },
                    trailing = {
                        CrownStatusPill(
                            if (access) "Granted" else "Not granted",
                            if (access) CrownTone.SUCCESS else CrownTone.WARNING,
                        )
                    },
                )
                CrownRow(
                    title = "Camera",
                    subtitle = "Not requested; optical pairing is blocked",
                    showDivider = false,
                    trailing = { CrownStatusPill("Not requested", CrownTone.NEUTRAL) },
                )
            }
        }
        item(key = "controls-header") {
            Spacer(Modifier.height(24.dp))
            CrownSectionHeader("Your controls")
        }
        item(key = "controls") {
            CrownInsetGroup {
                CrownRow(
                    title = "Open system app settings",
                    subtitle = "Review or revoke permissions and clear app data",
                    onClick = {
                        val intent = Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:" + context.packageName),
                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        runCatching { context.startActivity(intent) }
                    },
                    showDivider = true,
                )
                CrownRow(
                    title = "Notification access settings",
                    subtitle = "Grant or revoke the local listener",
                    onClick = {
                        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        runCatching { context.startActivity(intent) }
                    },
                    showDivider = false,
                )
            }
        }
        item(key = "limits-header") {
            Spacer(Modifier.height(24.dp))
            CrownSectionHeader("Known limits")
        }
        item(key = "limits") {
            CrownNote(
                message = "There is no data-export or selective-deletion screen yet. Pair records are removed only by an explicit unpair, which is not implemented. Apple account and Health services are documented blockers, not silent omissions.",
                tone = CrownTone.WARNING,
            )
            Spacer(Modifier.height(16.dp))
            CrownButton(
                text = "Back to settings",
                onClick = onBack,
                style = CrownButtonStyle.PLAIN,
            )
        }
    }
}
