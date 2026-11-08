package rocks.lumina.app.feature.post

import rocks.lumina.app.core.nostr.model.Comment

/** A top-level comment with its (flattened) replies, oldest first. */
data class CommentThread(val comment: Comment, val replies: List<Comment>)

/**
 * Builds two-level threads like Instagram: every reply (at any depth) is shown under its top-level
 * ancestor. Comments whose parent is missing are treated as top-level.
 */
fun buildThreads(comments: List<Comment>): List<CommentThread> {
    val byId = comments.associateBy { it.id }
    fun rootOf(c: Comment): Comment {
        var current = c
        val seen = HashSet<String>()
        while (!current.isTopLevel && seen.add(current.id)) {
            current = byId[current.parentId] ?: return current
        }
        return current
    }
    val groups = LinkedHashMap<String, MutableList<Comment>>()
    val roots = LinkedHashMap<String, Comment>()
    comments.sortedBy { it.createdAt }.forEach { c ->
        val root = rootOf(c)
        roots.putIfAbsent(root.id, root)
        if (root.id != c.id) groups.getOrPut(root.id) { mutableListOf() }.add(c)
    }
    return roots.values.map { CommentThread(it, groups[it.id].orEmpty()) }
}

/** The `@query` being typed at the cursor (for mention autocomplete), or null. */
fun mentionQueryAt(text: String, cursor: Int): String? {
    if (cursor <= 0 || cursor > text.length) return null
    val before = text.substring(0, cursor)
    val start = before.lastIndexOf('@')
    if (start < 0) return null
    if (start > 0 && !before[start - 1].isWhitespace()) return null
    val query = before.substring(start + 1)
    if (query.isEmpty() || query.length > 30 || query.any { it.isWhitespace() }) return null
    return query
}

/** Replaces the `@query` before [cursor] with [replacement] + space. Returns new text and cursor. */
fun replaceMention(text: String, cursor: Int, replacement: String): Pair<String, Int> {
    val start = text.substring(0, cursor).lastIndexOf('@')
    if (start < 0) return text to cursor
    val newText = text.substring(0, start) + replacement + " " + text.substring(cursor)
    return newText to (start + replacement.length + 1)
}
