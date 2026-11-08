package rocks.lumina.app.feature.notifications

import rocks.lumina.app.core.data.NotificationGroup

/** Pure helpers for the notifications list. */
object NotificationGroups {
    /**
     * Appends [next] (an older page) to [current]. Groups with the same key (e.g. likes on the same post
     * that straddle a page boundary) are merged instead of shown twice.
     */
    fun merge(current: List<NotificationGroup>, next: List<NotificationGroup>): List<NotificationGroup> {
        val byKey = LinkedHashMap<String, NotificationGroup>()
        (current + next).forEach { group ->
            val existing = byKey[group.key]
            byKey[group.key] = if (existing == null) {
                group
            } else {
                val newer = if (group.createdAt > existing.createdAt) group else existing
                val older = if (newer === group) existing else group
                newer.copy(
                    actors = (newer.actors + older.actors).distinct(),
                    zapSats = existing.zapSats + group.zapSats,
                    unread = existing.unread || group.unread,
                )
            }
        }
        return byKey.values.sortedByDescending { it.createdAt }
    }

    /** Splits into the "New" (unread) and "Earlier" sections, preserving order. */
    fun sections(groups: List<NotificationGroup>): Pair<List<NotificationGroup>, List<NotificationGroup>> =
        groups.partition { it.unread }
}
