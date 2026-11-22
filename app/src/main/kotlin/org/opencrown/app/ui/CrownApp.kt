package org.opencrown.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import org.opencrown.app.data.ChainState
import org.opencrown.app.data.CoreStatus
import org.opencrown.app.data.DiscoveryState
import org.opencrown.app.data.FeatureRegistry
import org.opencrown.app.data.pairing.PairingStage
import org.opencrown.app.model.CrownSnapshot
import org.opencrown.app.ui.navigation.CrownNavigator
import org.opencrown.app.ui.navigation.Destination
import org.opencrown.app.ui.screens.AboutScreen
import org.opencrown.app.ui.screens.AccessibilityScreen
import org.opencrown.app.ui.screens.AppsScreen
import org.opencrown.app.ui.screens.DiagnosticsScreen
import org.opencrown.app.ui.screens.DiscoveryScreen
import org.opencrown.app.ui.screens.EvidenceScreen
import org.opencrown.app.ui.screens.FacesScreen
import org.opencrown.app.ui.screens.FeatureDetailScreen
import org.opencrown.app.ui.screens.HealthScreen
import org.opencrown.app.ui.screens.LicencesScreen
import org.opencrown.app.ui.screens.HomeScreen
import org.opencrown.app.ui.screens.NotificationsScreen
import org.opencrown.app.ui.screens.PairingScreen
import org.opencrown.app.ui.screens.PrivacyScreen
import org.opencrown.app.ui.screens.SettingsScreen
import org.opencrown.app.ui.screens.SetupScreen
import org.opencrown.app.ui.screens.WelcomeScreen

@Composable
fun CrownApp(
    snapshot: CrownSnapshot?,
    registryResult: FeatureRegistry.LoadResult,
    discoveryState: DiscoveryState,
    chainState: ChainState,
    coreStatus: CoreStatus,
    pairingStage: PairingStage,
    selectedWatchName: String?,
    onboardingComplete: Boolean,
    refreshing: Boolean,
    appVersion: String,
    actions: CrownActions,
) {
    val navigator = remember {
        CrownNavigator(if (onboardingComplete) Destination.Home else Destination.Welcome)
    }
    val registry = (registryResult as? FeatureRegistry.LoadResult.Loaded)?.registry
    val registryError = (registryResult as? FeatureRegistry.LoadResult.Unavailable)?.detail

    BackHandler(enabled = navigator.canGoBack) {
        navigator.pop()
    }

    AnimatedContent(
        targetState = navigator.current,
        transitionSpec = {
            val direction = navigator.direction
            val enter = slideInHorizontally(
                animationSpec = tween(340),
                initialOffsetX = { width -> if (direction > 0) width else -width / 3 },
            ) + fadeIn(animationSpec = tween(220))
            val exit = slideOutHorizontally(
                animationSpec = tween(340),
                targetOffsetX = { width -> if (direction > 0) -width / 3 else width },
            ) + fadeOut(animationSpec = tween(180))
            enter togetherWith exit
        },
        label = "crown-navigation",
    ) { destination ->
        val openFeature: (String) -> Unit = { id -> navigator.push(Destination.FeatureDetail(id)) }
        val navigate: (Destination) -> Unit = { navigator.push(it) }
        val back: () -> Unit = { navigator.pop() }

        when (destination) {
            Destination.Welcome -> WelcomeScreen(
                pairingAvailable = snapshot?.transportReady == true,
                onGetStarted = {
                    actions.completeOnboarding()
                    navigator.reset(Destination.Home)
                },
                onViewEvidence = {
                    actions.completeOnboarding()
                    navigator.reset(Destination.Home)
                    navigator.push(Destination.Evidence)
                },
            )

            Destination.Home -> HomeScreen(
                snapshot = snapshot,
                registry = registry,
                refreshing = refreshing,
                onRefresh = actions.refresh,
                onNavigate = navigate,
                onOpenFeature = openFeature,
            )

            Destination.Discovery -> DiscoveryScreen(
                state = discoveryState,
                onRequestPermissions = actions.requestPermissions,
                onStartScan = actions.startScan,
                onStopScan = actions.stopScan,
                onBack = back,
            )

            Destination.Pairing -> PairingScreen(
                stage = pairingStage,
                selectedName = selectedWatchName,
                onBegin = actions.beginPairing,
                onSubmitPin = actions.submitPin,
                onBack = back,
            )

            Destination.Setup -> SetupScreen(snapshot = snapshot, onBack = back)

            Destination.Faces -> FacesScreen(
                snapshot = snapshot,
                registry = registry,
                onBack = back,
                onOpenFeature = openFeature,
            )

            Destination.Notifications -> NotificationsScreen(
                snapshot = snapshot,
                registry = registry,
                onBack = back,
                onOpenFeature = openFeature,
            )

            Destination.Health -> HealthScreen(
                snapshot = snapshot,
                registry = registry,
                onBack = back,
                onOpenFeature = openFeature,
            )

            Destination.Apps -> AppsScreen(
                snapshot = snapshot,
                registry = registry,
                onBack = back,
                onOpenFeature = openFeature,
            )

            Destination.Settings -> SettingsScreen(
                snapshot = snapshot,
                registry = registry,
                appVersion = appVersion,
                onBack = back,
                onNavigate = navigate,
                onOpenFeature = openFeature,
                onClearLocalData = {
                    val cleared = actions.clearLocalData()
                    if (cleared) navigator.reset(Destination.Welcome)
                    cleared
                },
                onResetOnboarding = {
                    actions.resetOnboarding()
                    navigator.reset(Destination.Welcome)
                },
            )

            Destination.Privacy -> PrivacyScreen(
                snapshot = snapshot,
                onBack = back,
                onRequestPermissions = actions.requestPermissions,
            )

            Destination.Licences -> LicencesScreen(onBack = back)

            Destination.Accessibility -> AccessibilityScreen(onBack = back)

            Destination.Diagnostics -> DiagnosticsScreen(
                snapshot = snapshot,
                registry = registry,
                registryError = registryError,
                appVersion = appVersion,
                refreshing = refreshing,
                chainState = chainState,
                coreStatus = coreStatus,
                onRefresh = actions.refresh,
                onRequestPermissions = actions.requestPermissions,
                onPrepareBoot = actions.prepareBoot,
                onBack = back,
            )

            Destination.Evidence -> EvidenceScreen(
                registry = registry,
                error = registryError,
                onBack = back,
                onOpenFeature = openFeature,
            )

            Destination.About -> AboutScreen(
                registry = registry,
                appVersion = appVersion,
                snapshot = snapshot,
                onBack = back,
                onOpenEvidence = { navigator.push(Destination.Evidence) },
                onOpenPrivacy = { navigator.push(Destination.Privacy) },
                onOpenLicences = { navigator.push(Destination.Licences) },
                onResetOnboarding = {
                    actions.resetOnboarding()
                    navigator.reset(Destination.Welcome)
                },
            )

            is Destination.FeatureDetail -> FeatureDetailScreen(
                featureId = destination.id,
                registry = registry,
                onBack = back,
            )
        }
    }
}