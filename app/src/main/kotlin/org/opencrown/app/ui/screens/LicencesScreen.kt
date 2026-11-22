package org.opencrown.app.ui.screens

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.opencrown.app.ui.components.CrownInsetGroup
import org.opencrown.app.ui.components.CrownNote
import org.opencrown.app.ui.components.CrownRow
import org.opencrown.app.ui.components.CrownScaffold
import org.opencrown.app.ui.components.CrownSectionHeader
import org.opencrown.app.ui.components.CrownTone

@Composable
fun LicencesScreen(onBack: () -> Unit) {
    CrownScaffold(
        title = "Licences",
        subtitle = "Open-source software",
        onBack = onBack,
    ) {
        item(key = "project") {
            CrownInsetGroup(title = "Open Crown") {
                CrownRow(
                    title = "Open Crown",
                    subtitle = "MIT licence. The reference project is not linked or distributed.",
                    showDivider = false,
                )
            }
        }
        item(key = "runtime-header") {
            Spacer(Modifier.height(24.dp))
            CrownSectionHeader("Runtime components")
        }
        item(key = "runtime") {
            CrownInsetGroup(
                footer = "This is a summary. The full inventory is docs/THIRD_PARTY.md in the source tree.",
            ) {
                CrownRow(
                    title = "Kotlin, AndroidX and Jetpack Compose",
                    subtitle = "Apache-2.0",
                    showDivider = true,
                )
                CrownRow(
                    title = "JNA",
                    subtitle = "Dual LGPL-2.1-or-later or Apache-2.0",
                    showDivider = true,
                )
                CrownRow(
                    title = "UniFFI runtime and generated binding",
                    subtitle = "MPL-2.0",
                    showDivider = true,
                )
                CrownRow(
                    title = "Rust crates",
                    subtitle = "MIT/Apache-2.0, MPL-2.0, BSD-3-Clause, Zlib, Unicode-3.0",
                    showDivider = false,
                )
            }
        }
        item(key = "assets-header") {
            Spacer(Modifier.height(24.dp))
            CrownSectionHeader("Apple assets")
        }
        item(key = "assets") {
            CrownNote(
                message = "No Apple code, fonts, SF Symbols, face artwork or other Apple-owned assets are bundled. Product icons are original Canvas drawings.",
                tone = CrownTone.INFO,
            )
        }
        item(key = "notice-header") {
            Spacer(Modifier.height(24.dp))
            CrownSectionHeader("Notice bundle")
        }
        item(key = "notice") {
            CrownNote(
                message = "Licence texts are not bundled in this build yet, and an SBOM for the exact release artifact has not been generated. Both are release gates; this screen is an inventory, not a completed notice file.",
                tone = CrownTone.WARNING,
            )
        }
    }
}
