package org.opencrown.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.opencrown.app.data.CrownRepository
import org.opencrown.app.data.FeatureRegistry
import org.opencrown.app.data.pairing.PairingController
import org.opencrown.app.data.pairing.UnavailablePairingGateway
import org.opencrown.app.model.CrownSnapshot

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val graph = (application as OpenCrownApplication).graph
    private val crown: CrownRepository = graph.crown

    val snapshot: StateFlow<CrownSnapshot?> = crown.snapshot
    val refreshing: StateFlow<Boolean> = crown.refreshing
    val registry: FeatureRegistry.LoadResult = graph.featureRegistry
    val coreStatus = graph.core
    val discoveryState = graph.discovery.state
    val chainState = graph.chain.state

    private val _onboardingComplete = MutableStateFlow(graph.onboarding.isComplete())
    val onboardingComplete: StateFlow<Boolean> = _onboardingComplete.asStateFlow()

    private val pairing = PairingController(UnavailablePairingGateway())
    val pairingStage = pairing.stage

    private val _selectedWatchName = MutableStateFlow<String?>(null)
    val selectedWatchName: StateFlow<String?> = _selectedWatchName.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch { crown.refresh() }
    }

    fun startScan() {
        graph.discovery.start()
    }

    fun stopScan() {
        graph.discovery.stop()
    }

    fun selectWatch(address: String, displayName: String?) {
        _selectedWatchName.value = displayName
        pairing.select(address, displayName)
    }

    fun beginPairing() {
        viewModelScope.launch { pairing.begin() }
    }

    fun submitPin(pin: String) {
        viewModelScope.launch { pairing.submitPin(pin) }
    }

    fun prepareBoot() {
        viewModelScope.launch {
            graph.chain.run()
            crown.refresh()
        }
    }

    fun completeOnboarding() {
        graph.onboarding.markComplete()
        _onboardingComplete.value = true
    }

    fun resetOnboarding() {
        graph.onboarding.reset()
        _onboardingComplete.value = false
    }

    /** Returns false when a pair record exists and must be unpaired first. */
    fun clearLocalData(): Boolean {
        if (!graph.clearLocalData()) return false
        graph.discovery.stop()
        _onboardingComplete.value = false
        return true
    }
}