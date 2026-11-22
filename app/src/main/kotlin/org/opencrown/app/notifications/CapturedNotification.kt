package org.opencrown.app.notifications

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * A notification captured on the phone. This is ingress only: nothing here has
 * been delivered to a watch.
 */
data class CapturedNotification(
    val key: String,
    val packageName: String,
    val appLabel: String?,
    val title: String?,
    val text: String?,
    val postedAtMillis: Long,
    val category: String?,
    val ongoing: Boolean,
)

object NotificationSanitizer {
    const val MAX_TITLE = 120
    const val MAX_TEXT = 400

    fun clean(value: String?): String? {
        val trimmed = value
            ?.replace(Regex("[\\u0000-\\u001F\\u007F]"), " ")
            ?.trim()
            ?.replace(Regex("\\s+"), " ")
            ?: return null
        return trimmed.ifEmpty { null }
    }

    /**
     * Returns null when there is nothing worth showing. Notification content is
     * sensitive, so it is bounded and kept in memory only.
     */
    fun from(
        key: String,
        packageName: String,
        appLabel: String?,
        title: String?,
        text: String?,
        postedAtMillis: Long,
        category: String?,
        ongoing: Boolean,
    ): CapturedNotification? {
        val cleanTitle = clean(title)?.take(MAX_TITLE)
        val cleanText = clean(text)?.take(MAX_TEXT)
        if (cleanTitle == null && cleanText == null) return null
        return CapturedNotification(
            key = key,
            packageName = packageName,
            appLabel = clean(appLabel),
            title = cleanTitle,
            text = cleanText,
            postedAtMillis = postedAtMillis,
            category = clean(category),
            ongoing = ongoing,
        )
    }
}

/**
 * In-memory ingress buffer. Deliberately not persisted: notification content is
 * sensitive and there is no watch transport to deliver it to yet.
 */
object NotificationIngress {
    private const val MAX_ITEMS = 40
    private val _items = MutableStateFlow<List<CapturedNotification>>(emptyList())
    val items: StateFlow<List<CapturedNotification>> = _items.asStateFlow()

    fun record(item: CapturedNotification) {
        _items.update { current ->
            (listOf(item) + current.filterNot { it.key == item.key })
                .sortedByDescending { it.postedAtMillis }
                .take(MAX_ITEMS)
        }
    }

    fun clear() {
        _items.value = emptyList()
    }
}
