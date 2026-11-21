package org.opencrown.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.remember

/**
 * The skeleton entry point.
 *
 * It calls Rust once and reports the result. Real screens (scan, consent,
 * progress, diagnostics) land after Phase 0; see docs/06-roadmap.md.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface {
                    val revision = remember { runCatching { RustBridge.protocolVersion() } }
                    Text(
                        revision.fold(
                            onSuccess = { "Open Crown — protocol revision $it" },
                            onFailure = { "Open Crown — FFI unavailable: ${it.message}" },
                        )
                    )
                }
            }
        }
    }
}
