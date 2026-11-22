package org.opencrown.app

import android.Manifest
import android.graphics.Color as AndroidColor
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import org.opencrown.app.ui.CrownActions
import org.opencrown.app.ui.CrownApp
import org.opencrown.app.ui.theme.CrownTheme

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        viewModel.refresh()
        if (grants[Manifest.permission.BLUETOOTH_SCAN] == true) {
            viewModel.startScan()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT),
        )
        val versionName = runCatching {
            packageManager.getPackageInfo(packageName, 0).versionName
        }.getOrNull() ?: "0.1.0"

        setContent {
            CrownTheme {
                val snapshot by viewModel.snapshot.collectAsState()
                val refreshing by viewModel.refreshing.collectAsState()
                val onboarding by viewModel.onboardingComplete.collectAsState()
                val pairingStage by viewModel.pairingStage.collectAsState()
                val selectedWatchName by viewModel.selectedWatchName.collectAsState()
                val discoveryState by viewModel.discoveryState.collectAsState()
                val chainState by viewModel.chainState.collectAsState()

                CrownApp(
                    snapshot = snapshot,
                    registryResult = viewModel.registry,
                    discoveryState = discoveryState,
                    chainState = chainState,
                    pairingStage = pairingStage,
                    selectedWatchName = selectedWatchName,
                    onboardingComplete = onboarding,
                    refreshing = refreshing,
                    appVersion = versionName,
                    actions = CrownActions(
                        refresh = viewModel::refresh,
                        requestPermissions = {
                            permissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.BLUETOOTH_SCAN,
                                    Manifest.permission.BLUETOOTH_CONNECT,
                                )
                            )
                        },
                        startScan = viewModel::startScan,
                        stopScan = viewModel::stopScan,
                        selectWatch = viewModel::selectWatch,
                        beginPairing = viewModel::beginPairing,
                        submitPin = viewModel::submitPin,
                        completeOnboarding = viewModel::completeOnboarding,
                        resetOnboarding = viewModel::resetOnboarding,
                        prepareBoot = viewModel::prepareBoot,
                    ),
                )
            }
        }
    }
}