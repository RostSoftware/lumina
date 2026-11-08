package rocks.lumina.app.feature.discover

import rocks.lumina.app.core.nostr.model.PicturePost

/** Pure helpers for the Discover screen (unit tested). */
internal object DiscoverLogic {
    private val tagRegex = Regex("^#?[\\p{L}\\p{N}_]{1,64}$")

    /** `#Sunset ` → `sunset`; returns null when [input] is not a single hashtag-like word. */
    fun normalizeHashtag(input: String?): String? {
        val trimmed = input?.trim() ?: return null
        if (!tagRegex.matches(trimmed)) return null
        return trimmed.removePrefix("#").lowercase().takeIf { it.isNotEmpty() }
    }

    /** Appends [page] to [existing], dropping duplicates, keeping the existing order first. */
    fun appendPage(existing: List<PicturePost>, page: List<PicturePost>): List<PicturePost> {
        if (page.isEmpty()) return existing
        val seen = existing.mapTo(HashSet()) { it.id }
        return existing + page.filter { seen.add(it.id) }
    }

    /** Merges fresh posts on top of [existing], newest first, without duplicates. */
    fun prepend(fresh: List<PicturePost>, existing: List<PicturePost>): List<PicturePost> =
        (fresh + existing).distinctBy { it.id }.sortedByDescending { it.createdAt }

    /** Index at which the grid should start loading the next page. */
    fun shouldLoadMore(lastVisibleIndex: Int, total: Int, threshold: Int = 9): Boolean =
        total > 0 && lastVisibleIndex >= total - threshold
}
