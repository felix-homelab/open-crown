package org.opencrown.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text

/**
 * The skeleton entry point.
 *
 * Real screens (scan, consent, progress, diagnostics) land in Phase 3-5.
 * See docs/02-architecture.md section 2.1.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface {
                    Text("Open Crown — skeleton")
                }
            }
        }
    }
}
