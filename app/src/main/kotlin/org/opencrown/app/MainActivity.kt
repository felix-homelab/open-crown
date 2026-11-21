package org.opencrown.app

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * The skeleton entry point: report the protocol revision, whether this device
 * is on the supported list, and the list itself. Real screens land after
 * Phase 0.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Open Crown", style = MaterialTheme.typography.headlineSmall)

                        val revision = remember { runCatching { RustBridge.protocolVersion() } }
                        Text(
                            revision.fold(
                                onSuccess = { "Protocol revision $it" },
                                onFailure = { "FFI unavailable: " + it.message },
                            )
                        )

                        val json = remember { runCatching { readAsset("supported-devices.json") }.getOrNull() }
                        if (json == null) {
                            Text("Device list unavailable")
                        } else {
                            val status = remember(json) {
                                runCatching {
                                    RustBridge.deviceStatus(
                                        json,
                                        Build.MODEL,
                                        System.getProperty("os.version") ?: "unknown",
                                        Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown",
                                        Build.VERSION.SDK_INT.toUInt(),
                                    )
                                }.getOrDefault("unknown")
                            }
                            Text("This device: $status")

                            Text("Supported devices", style = MaterialTheme.typography.titleMedium)
                            val devices = remember(json) {
                                runCatching { RustBridge.supportedDevices(json) }.getOrDefault(emptyList())
                            }
                            devices.forEach { device ->
                                Text(
                                    device.model + " / " + device.kernelBuild +
                                        " — " + device.status
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun readAsset(name: String): String =
        assets.open(name).bufferedReader().use { it.readText() }
}
