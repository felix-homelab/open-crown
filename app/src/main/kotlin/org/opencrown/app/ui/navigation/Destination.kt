package org.opencrown.app.ui.navigation

/** Product destinations. Titles and presentation live in the UI layer. */
sealed interface Destination {
    data object Welcome : Destination
    data object Home : Destination
    data object Discovery : Destination
    data object Pairing : Destination
    data object Setup : Destination
    data object Faces : Destination
    data object Notifications : Destination
    data object Health : Destination
    data object Apps : Destination
    data object Settings : Destination
    data object Accessibility : Destination
    data object Diagnostics : Destination
    data object Evidence : Destination
    data object Privacy : Destination
    data object About : Destination
    data class FeatureDetail(val id: String) : Destination
}
