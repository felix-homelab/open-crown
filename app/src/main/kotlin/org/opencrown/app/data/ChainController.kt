package org.opencrown.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.opencrown.app.payload.Compatibility
import org.opencrown.app.payload.PayloadManifest
import org.opencrown.app.root.PageCacheRelayChain

sealed interface ChainState {
    data object Idle : ChainState
    data object Running : ChainState
    data class Failed(val detail: String) : ChainState
    data class Unavailable(val detail: String) : ChainState
    data object Patched : ChainState
}

/**
 * The per-boot page-cache chain, kept as an explicit privileged action.
 *
 * It is NOT run automatically. The payload is quarantined research code with an
 * open provenance review (docs/cleanroom/PROVENANCE.md section 7). Running it
 * patches init's logging path for this boot and can destabilise the phone; the
 * consent sheet says so before the first byte is written.
 *
 * A successful patch is not a broker. The app still cannot connect to the
 * init-domain relay; the probe reports that separately.
 */
class ChainController(
    context: Context,
    private val chain: PageCacheRelayChain = PageCacheRelayChain(context.applicationContext),
) {
    private val appContext = context.applicationContext
    private val _state = MutableStateFlow<ChainState>(ChainState.Idle)
    val state: StateFlow<ChainState> = _state.asStateFlow()

    suspend fun run() {
        if (_state.value == ChainState.Running) return
        _state.value = ChainState.Running
        withContext(Dispatchers.IO) {
            val manifest = runCatching { PayloadManifest.load(appContext) }.getOrElse {
                _state.value = ChainState.Unavailable("The payload manifest could not be read.")
                return@withContext
            }
            val match = manifest.match(PayloadManifest.currentDevice())
            if (match.compatibility != Compatibility.VERIFIED) {
                _state.value = ChainState.Unavailable(match.reason)
                return@withContext
            }
            val payload = runCatching { manifest.payloadBytes(appContext) }.getOrElse {
                _state.value = ChainState.Failed("The payload entry could not be read.")
                return@withContext
            }
            if (manifest.entrySize > 0 && payload.size != manifest.entrySize) {
                _state.value = ChainState.Failed(
                    "Payload size " + payload.size + " does not match " + manifest.entrySize + "."
                )
                return@withContext
            }
            var lastLine = "Preparing"
            val ok = runCatching {
                chain.run(payload, object : PageCacheRelayChain.Listener {
                    override fun onLog(line: String) {
                        lastLine = line.trim().take(160)
                    }
                })
            }.getOrElse { error ->
                _state.value = ChainState.Failed("The chain threw " + error.javaClass.simpleName + ".")
                return@withContext
            }
            _state.value = if (ok) {
                ChainState.Patched
            } else {
                ChainState.Failed(lastLine.ifBlank { "The chain did not complete." })
            }
        }
    }
}
