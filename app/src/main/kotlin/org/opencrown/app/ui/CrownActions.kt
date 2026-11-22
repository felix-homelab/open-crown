package org.opencrown.app.ui

/** Callbacks the shell needs. Keeping them in one value avoids a wall of parameters. */
data class CrownActions(
    val refresh: () -> Unit,
    val requestPermissions: () -> Unit,
    val startScan: () -> Unit,
    val stopScan: () -> Unit,
    val selectWatch: (address: String, displayName: String?) -> Unit,
    val beginPairing: () -> Unit,
    val submitPin: (String) -> Unit,
    val completeOnboarding: () -> Unit,
    val resetOnboarding: () -> Unit,
    val prepareBoot: () -> Unit,
)
