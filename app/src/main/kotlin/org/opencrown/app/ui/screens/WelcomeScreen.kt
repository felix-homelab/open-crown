package org.opencrown.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.opencrown.app.ui.CrownActions
import org.opencrown.app.ui.components.CrownBrandMark
import org.opencrown.app.ui.components.CrownButton
import org.opencrown.app.ui.components.CrownButtonStyle
import org.opencrown.app.ui.components.CrownGlyph
import org.opencrown.app.ui.components.CrownIcon
import org.opencrown.app.ui.components.CrownNote
import org.opencrown.app.ui.components.CrownTone
import org.opencrown.app.ui.theme.CrownTheme

@Composable
fun WelcomeScreen(
    pairingAvailable: Boolean,
    onGetStarted: () -> Unit,
    onViewEvidence: () -> Unit,
) {
    val colors = CrownTheme.colors
    val spacing = CrownTheme.spacing
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(colors.groupedBackground, colors.fill.copy(alpha = 0.55f), colors.groupedBackground)
                )
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 26.dp),
        ) {
            Spacer(Modifier.height(56.dp))
            CrownBrandMark(size = 76.dp, tint = colors.label)
            Spacer(Modifier.height(26.dp))
            Text(
                text = "Open Crown",
                style = CrownTheme.type.largeTitle.copy(fontSize = 40.sp, lineHeight = 46.sp),
                color = colors.label,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "A companion for the watch you already own.",
                style = CrownTheme.type.title3,
                color = colors.secondaryLabel,
            )
            Spacer(Modifier.height(30.dp))

            WelcomePoint(
                glyph = CrownGlyph.WATCH,
                title = "Pair on your phone",
                message = "Discovery, pairing and setup are driven from Android, without an iPhone in the room.",
            )
            WelcomePoint(
                glyph = CrownGlyph.SHIELD,
                title = "Your keys stay yours",
                message = "Pairing records are sealed by Android Keystore. No Apple account password is stored.",
            )
            WelcomePoint(
                glyph = CrownGlyph.INFO,
                title = "Blocked means blocked",
                message = "Every capability is verified, being built, or named as a blocker. Nothing is faked to look finished.",
            )

            Spacer(Modifier.height(18.dp))
            if (pairingAvailable) {
                CrownNote(
                    title = "A watch is connected",
                    message = "This build has a live transport. Pairing and setup actions follow the real link state.",
                    tone = CrownTone.SUCCESS,
                )
            } else {
                CrownNote(
                    title = "This build cannot pair yet",
                    message = "The privileged Bluetooth path is blocked on this phone: the app cannot reach the per-boot agent. Device state, diagnostics and the feature registry are real; pairing is not.",
                    tone = CrownTone.WARNING,
                )
            }
            Spacer(Modifier.height(30.dp))
            CrownButton(text = "Get started", onClick = onGetStarted)
            Spacer(Modifier.height(6.dp))
            CrownButton(
                text = "See what is verified",
                onClick = onViewEvidence,
                style = CrownButtonStyle.PLAIN,
            )
            Spacer(Modifier.height(28.dp))
            Text(
                text = "Open Crown is an independent project. It is not affiliated with, endorsed by, or connected to Apple.",
                style = CrownTheme.type.caption1,
                color = colors.tertiaryLabel,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun WelcomePoint(
    glyph: CrownGlyph,
    title: String,
    message: String,
) {
    val colors = CrownTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 11.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(colors.cardBackground),
            contentAlignment = Alignment.Center,
        ) {
            CrownIcon(glyph, tint = colors.tint, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(text = title, style = CrownTheme.type.headline, color = colors.label)
            Spacer(Modifier.height(3.dp))
            Text(text = message, style = CrownTheme.type.footnote, color = colors.secondaryLabel)
        }
    }
}
