package org.opencrown.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.opencrown.app.data.DiscoveryState
import org.opencrown.app.data.NearbyAdvertisement
import org.opencrown.app.data.pairing.PairingFailure
import org.opencrown.app.data.pairing.PairingStage
import org.opencrown.app.model.CrownSnapshot
import org.opencrown.app.model.Observation
import org.opencrown.app.ui.components.CrownButton
import org.opencrown.app.ui.components.CrownButtonStyle
import org.opencrown.app.ui.components.CrownEmptyState
import org.opencrown.app.ui.components.CrownGlyph
import org.opencrown.app.ui.components.CrownInsetGroup
import org.opencrown.app.ui.components.CrownNote
import org.opencrown.app.ui.components.CrownRow
import org.opencrown.app.ui.components.CrownScaffold
import org.opencrown.app.ui.components.CrownSectionHeader
import org.opencrown.app.ui.components.CrownStatusPill
import org.opencrown.app.ui.components.CrownTone
import org.opencrown.app.ui.state.CheckpointRow
import org.opencrown.app.ui.state.CheckpointState
import org.opencrown.app.ui.state.ShellState
import org.opencrown.app.ui.theme.CrownTheme

@Composable
fun DiscoveryScreen(
    state: DiscoveryState,
    onRequestPermissions: () -> Unit,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onBack: () -> Unit,
) {
    LaunchedEffect(Unit) {
        if (state is DiscoveryState.Idle) onStartScan()
    }
    DisposableEffect(Unit) {
        onDispose { onStopScan() }
    }
    CrownScaffold(
        title = "Pair a watch",
        subtitle = "Nearby Apple advertisements",
        onBack = onBack,
    ) {
        item(key = "intro") {
            CrownNote(
                title = "Observed, not selectable",
                message = "An advertisement is not a watch and not a connection. The FE25 setup-metadata layout is UNKNOWN, so this build can list nearby Apple advertisements but cannot identify a pairable Watch or offer a selection.",
                tone = CrownTone.NEUTRAL,
            )
            Spacer(Modifier.height(20.dp))
        }
        when (state) {
            DiscoveryState.PermissionRequired -> item(key = "perm") {
                CrownEmptyState(
                    glyph = CrownGlyph.ANTENNA,
                    title = "Nearby devices permission",
                    message = "Android requires the Nearby devices permission to see Bluetooth advertisements. No location is requested.",
                    actionLabel = "Allow nearby devices",
                    onAction = onRequestPermissions,
                )
            }
            DiscoveryState.BluetoothOff -> item(key = "btoff") {
                CrownEmptyState(
                    glyph = CrownGlyph.BLUETOOTH,
                    title = "Bluetooth is off",
                    message = "Turn on Bluetooth in Android settings, then refresh. The app cannot switch it on without a user action.",
                )
            }
            DiscoveryState.Unsupported -> item(key = "unsupported") {
                CrownEmptyState(
                    glyph = CrownGlyph.ANTENNA,
                    title = "Bluetooth LE is unavailable",
                    message = "This phone does not expose a Bluetooth LE scanner to the app.",
                )
            }
            is DiscoveryState.Unavailable -> item(key = "unavailable") {
                CrownEmptyState(
                    glyph = CrownGlyph.INFO,
                    title = "Scan could not start",
                    message = state.detail,
                )
            }
            DiscoveryState.Idle -> item(key = "idle") {
                CrownEmptyState(
                    glyph = CrownGlyph.ANTENNA,
                    title = "Not scanning",
                    message = "Start a scan to see nearby Apple advertisements.",
                    actionLabel = "Start scan",
                    onAction = onStartScan,
                )
            }
            is DiscoveryState.Scanning -> {
                val ads = state.advertisements
                if (ads.isEmpty()) {
                    item(key = "scanning-empty") {
                        Column(Modifier.fillMaxWidth().padding(vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            org.opencrown.app.ui.components.CrownSkeleton(height = 54.dp, shape = CrownTheme.shapes.card)
                            Spacer(Modifier.height(10.dp))
                            org.opencrown.app.ui.components.CrownSkeleton(height = 54.dp, shape = CrownTheme.shapes.card)
                            Spacer(Modifier.height(16.dp))
                            Text(
                                text = "Scanning for Apple advertisements\u2026",
                                style = CrownTheme.type.footnote,
                                color = CrownTheme.colors.secondaryLabel,
                            )
                        }
                    }
                } else {
                    item(key = "ads") {
                        CrownInsetGroup(
                            title = "Seen nearby",
                            footer = "The list is sorted by signal strength. No address is shown or logged.",
                        ) {
                            ads.forEachIndexed { index, ad ->
                                AdvertisementRow(
                                    advertisement = ad,
                                    showDivider = index != ads.lastIndex,
                                )
                            }
                        }
                    }
                }
                item(key = "scan-controls") {
                    Spacer(Modifier.height(22.dp))
                    CrownButton(
                        text = "Stop scanning",
                        onClick = onStopScan,
                        style = CrownButtonStyle.TINTED,
                    )
                }
            }
        }
    }
}

@Composable
private fun AdvertisementRow(
    advertisement: NearbyAdvertisement,
    showDivider: Boolean,
) {
    val colors = CrownTheme.colors
    CrownRow(
        title = advertisement.displayName ?: "Apple advertisement",
        subtitle = "Type 0x" + (advertisement.appleManufacturerType?.toString(16)?.padStart(2, '0') ?: "??") +
            " \u00b7 RSSI " + advertisement.rssi + " dBm \u00b7 seen " + advertisement.seenCount + "\u00d7" +
            (advertisement.productType?.let { " \u00b7 " + it } ?: ""),
        showDivider = showDivider,
        trailing = {
            CrownStatusPill(
                if (advertisement.selectable) "Selectable" else "Not selectable",
                CrownTone.NEUTRAL,
            )
        },
    )
}

@Composable
fun PairingScreen(
    stage: PairingStage,
    selectedName: String?,
    onBegin: () -> Unit,
    onSubmitPin: (String) -> Unit,
    onBack: () -> Unit,
) {
    var pin by remember { mutableStateOf("") }
    CrownScaffold(
        title = "Pairing",
        subtitle = selectedName ?: "Selected advertisement",
        onBack = onBack,
    ) {
        item(key = "stage") {
            PairingStageCard(stage = stage)
            Spacer(Modifier.height(20.dp))
        }
        item(key = "pin") {
            CrownInsetGroup(
                title = "Pairing code",
                footer = "A six-digit code can only be verified while the pairing transport is open. The field is disabled until then.",
            ) {
                Column(Modifier.padding(16.dp)) {
                    BasicTextField(
                        value = pin,
                        onValueChange = { value ->
                            if (value.length <= 6 && value.all { it.isDigit() }) pin = value
                        },
                        enabled = stage is PairingStage.AwaitingPin,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        textStyle = CrownTheme.type.title2.copy(
                            color = CrownTheme.colors.label,
                            letterSpacing = androidx.compose.ui.unit.TextUnit.Unspecified,
                        ),
                        cursorBrush = SolidColor(CrownTheme.colors.tint),
                        modifier = Modifier.fillMaxWidth(),
                        decorationBox = { inner ->
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(CrownTheme.shapes.control)
                                    .background(CrownTheme.colors.fill)
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                            ) {
                                if (pin.isEmpty()) {
                                    Text(
                                        text = "6-digit code",
                                        style = CrownTheme.type.body,
                                        color = CrownTheme.colors.tertiaryLabel,
                                    )
                                }
                                inner()
                            }
                        },
                    )
                    Spacer(Modifier.height(12.dp))
                    CrownButton(
                        text = "Verify code",
                        onClick = { onSubmitPin(pin) },
                        enabled = stage is PairingStage.AwaitingPin && pin.length == 6,
                        style = CrownButtonStyle.TINTED,
                    )
                }
            }
        }
        item(key = "optical") {
            Spacer(Modifier.height(20.dp))
            CrownInsetGroup(title = "Optical pairing") {
                CrownRow(
                    title = "Scan the watch face",
                    subtitle = "The camera path needs the transport and a captured optical frame. Not available in this build.",
                    leadingGlyph = CrownGlyph.FACE,
                    showDivider = false,
                )
            }
        }
        item(key = "action") {
            Spacer(Modifier.height(22.dp))
            CrownButton(
                text = when (stage) {
                    is PairingStage.Failed -> "Try to start pairing again"
                    else -> "Start pairing"
                },
                onClick = onBegin,
            )
            Spacer(Modifier.height(8.dp))
            CrownButton(
                text = "Cancel",
                onClick = onBack,
                style = CrownButtonStyle.PLAIN,
            )
        }
    }
}

@Composable
private fun PairingStageCard(stage: PairingStage) {
    val colors = CrownTheme.colors
    val (title, message, tone) = when (stage) {
        PairingStage.Idle -> Triple(
            "Not started",
            "The pairing transport must open before a code can be exchanged.",
            CrownTone.NEUTRAL,
        )
        is PairingStage.Selected -> Triple(
            "Ready to start",
            "The app has an advertisement, not a connection. Starting pairing will ask the transport.",
            CrownTone.INFO,
        )
        is PairingStage.AwaitingPin -> Triple(
            "Enter the code",
            "The watch is waiting for a six-digit code. Attempt " + stage.attempt + ".",
            CrownTone.INFO,
        )
        is PairingStage.Securing -> Triple("Securing", stage.detail, CrownTone.INFO)
        PairingStage.Scanning -> Triple("Scanning", "Looking for a pairable watch.", CrownTone.INFO)
        PairingStage.Secured -> Triple("Secured", "The control channel reports an encrypted association.", CrownTone.SUCCESS)
        is PairingStage.Failed -> Triple("Pairing unavailable", failureText(stage.failure), CrownTone.CRITICAL)
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(CrownTheme.shapes.card)
            .background(colors.cardBackground)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = title, style = CrownTheme.type.title3, color = colors.label, modifier = Modifier.weight(1f))
            CrownStatusPill(
                text = when (tone) {
                    CrownTone.SUCCESS -> "Secured"
                    CrownTone.CRITICAL -> "Blocked"
                    CrownTone.INFO -> "In progress"
                    else -> "Waiting"
                },
                tone = tone,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(text = message, style = CrownTheme.type.footnote, color = colors.secondaryLabel)
    }
}

private fun failureText(failure: PairingFailure): String = when (failure) {
    PairingFailure.TransportUnavailable ->
        "The app cannot reach the privileged agent. The init-domain relay refuses untrusted_app connections, so no pairing channel exists."
    PairingFailure.PrivilegeUnavailable -> "No per-boot privilege is present on this phone."
    PairingFailure.PermissionDenied -> "A required Android permission is missing."
    PairingFailure.BluetoothOff -> "Bluetooth is off."
    is PairingFailure.Rejected -> failure.detail
    is PairingFailure.Protocol -> failure.detail
}

@Composable
fun SetupScreen(snapshot: CrownSnapshot?, onBack: () -> Unit) {
    val model = ShellState.home(snapshot, null)
    CrownScaffold(
        title = "Setup",
        subtitle = "Activation and first sync",
        onBack = onBack,
    ) {
        item(key = "note") {
            CrownNote(
                title = "Setup cannot start yet",
                message = "Activation, language, Wi-Fi handoff and PairedSync require a secure pairing. The rows below show the real checkpoint state.",
                tone = CrownTone.WARNING,
            )
            Spacer(Modifier.height(20.dp))
        }
        item(key = "checkpoints") {
            CrownInsetGroup(footer = "A checkpoint is complete only when the watch reports the corresponding state.") {
                model.checkpoints.forEachIndexed { index, checkpoint ->
                    SetupCheckpointRow(checkpoint, index != model.checkpoints.lastIndex)
                }
            }
        }
        item(key = "phase") {
            Spacer(Modifier.height(24.dp))
            CrownSectionHeader("Protocol phases")
        }
        item(key = "phases") {
            CrownInsetGroup {
                val phases = listOf(
                    "Discovery" to "Advertisement seen and decoded",
                    "Pairing" to "Control security and code exchange",
                    "Activation" to "Owner-authorised device activation",
                    "Language and region" to "Locale sent to the watch",
                    "Wi-Fi handoff" to "Credentials delivered by a supported path",
                    "PairedSync" to "Initial sync completes",
                    "Face observed" to "A live report confirms the watch face",
                )
                phases.forEachIndexed { index, (name, detail) ->
                    CrownRow(
                        title = name,
                        subtitle = detail,
                        showDivider = index != phases.lastIndex,
                        trailing = { CrownStatusPill("Not built", CrownTone.NEUTRAL) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SetupCheckpointRow(checkpoint: CheckpointRow, showDivider: Boolean) {
    val tone = when (checkpoint.state) {
        CheckpointState.DONE -> CrownTone.SUCCESS
        CheckpointState.ACTIVE -> CrownTone.INFO
        CheckpointState.PENDING, CheckpointState.NOT_INCLUDED -> CrownTone.NEUTRAL
        CheckpointState.BLOCKED, CheckpointState.FAILED -> CrownTone.CRITICAL
    }
    CrownRow(
        title = checkpoint.title,
        subtitle = checkpoint.detail,
        showDivider = showDivider,
        trailing = {
            CrownStatusPill(
                text = when (checkpoint.state) {
                    CheckpointState.DONE -> "Done"
                    CheckpointState.ACTIVE -> "Active"
                    CheckpointState.PENDING -> "Waiting"
                    CheckpointState.NOT_INCLUDED -> "Not included"
                    CheckpointState.BLOCKED -> "Blocked"
                    CheckpointState.FAILED -> "Failed"
                },
                tone = tone,
            )
        },
    )
}