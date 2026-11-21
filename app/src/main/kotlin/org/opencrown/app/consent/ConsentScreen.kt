package org.opencrown.app.consent

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * The consent screen. It renders the manifest facts, refuses to enable the
 * action until every blocker is cleared, and hands back a record to audit.
 */
@Composable
fun ConsentScreen(
    state: ConsentState,
    onStateChange: (ConsentState) -> Unit,
    onAccept: (ConsentRecord) -> Unit,
) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text("Before Open Crown borrows root", style = MaterialTheme.typography.headlineSmall)
        Text(state.request.payloadId + " " + state.request.version)
        Text("Publisher: " + state.request.publisher)
        Text("Family: " + state.request.family)
        Text("Capabilities: " + state.request.capabilities.joinToString())
        Text("Persistence: " + state.request.persistence)
        Text("Reboot risk: " + state.request.rebootRisk)
        Text("A root daemon runs behind a notification until you reboot or stop it.")

        OutlinedTextField(
            value = state.ownershipConfirmation,
            onValueChange = { onStateChange(state.copy(ownershipConfirmation = it)) },
            label = { Text("Type: " + ConsentState.CONFIRMATION_PHRASE) },
            singleLine = true,
        )

        Row {
            Checkbox(
                checked = state.bootDaemonAcknowledged,
                onCheckedChange = {
                    onStateChange(state.copy(bootDaemonAcknowledged = it))
                },
            )
            Text("I understand a root daemon runs until reboot")
        }

        state.blockers().forEach { blocker ->
            Text(blocker, color = MaterialTheme.colorScheme.error)
        }

        Button(
            onClick = {
                state.accept(System.currentTimeMillis())?.let(onAccept)
            },
            enabled = state.canProceed(),
        ) {
            Text("Borrow root")
        }
    }
}
