package rocks.lumina.app.core.data

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import rocks.lumina.app.core.nostr.Kinds
import rocks.lumina.app.core.nostr.NostrNetwork
import rocks.lumina.app.core.nostr.model.MuteList
import rocks.lumina.app.core.signer.AccountManager

/**
 * NIP-51 lists: mute list (kind 10000; public + private items) and bookmarks (kind 10003).
 * Mutations fetch the newest list first and preserve every tag and the encrypted content.
 * New mute items are written publicly (same as web); removing a private item re-encrypts the
 * private part with NIP-44.
 */
@Singleton
public class ListsRepository @Inject constructor(
    private val network: NostrNetwork,
    private val cache: EventCache,
    private val relays: RelayRepository,
    private val publisher: Publisher,
    private val accountManager: AccountManager,
) {
    private val mutex = Mutex()
    private var muteEvent: Event? = null
    private var privateMuteTags: List<List<String>> = emptyList()
    private var bookmarkEvent: Event? = null

    private val _mutes = MutableStateFlow(MuteList())
    public val mutes: StateFlow<MuteList> = _mutes.asStateFlow()

    private val _bookmarks = MutableStateFlow<List<String>>(emptyList())
    /** Bookmarked event ids, newest first. */
    public val bookmarks: StateFlow<List<String>> = _bookmarks.asStateFlow()

    public fun reset() {
        muteEvent = null
        privateMuteTags = emptyList()
        bookmarkEvent = null
        _mutes.value = MuteList()
        _bookmarks.value = emptyList()
    }

    public suspend fun loadOwn(pubkey: String) {
        val filter = Filter(kinds = listOf(Kinds.MUTE_LIST, Kinds.BOOKMARKS), authors = listOf(pubkey))
        applyAll(cache.query(filter), decrypt = false)
        val fresh = network.query((relays.writeRelays() + relays.discoveryRelays()).distinct(), listOf(filter))
        cache.insert(fresh)
        applyAll(fresh + listOfNotNull(muteEvent, bookmarkEvent), decrypt = true)
    }

    private suspend fun applyAll(events: List<Event>, decrypt: Boolean) {
        events.filter { it.kind == Kinds.MUTE_LIST }.maxByOrNull { it.createdAt }?.let { applyMute(it, decrypt) }
        events.filter { it.kind == Kinds.BOOKMARKS }.maxByOrNull { it.createdAt }?.let(::applyBookmarks)
    }

    private suspend fun applyMute(event: Event, decrypt: Boolean) {
        val current = muteEvent
        if (current != null && current.createdAt > event.createdAt) return
        val changed = current?.id != event.id
        muteEvent = event
        if (decrypt && (changed || privateMuteTags.isEmpty())) privateMuteTags = decryptPrivate(event)
        val public = MuteList.fromTags(event.tags.map { it.toList() })
        _mutes.value = public + MuteList.fromTags(privateMuteTags)
    }

    private suspend fun decryptPrivate(event: Event): List<List<String>> {
        if (event.content.isBlank()) return emptyList()
        val signer = accountManager.signerOrNull() ?: return emptyList()
        if (signer.pubKey != event.pubKey) return emptyList()
        return runCatching { MuteList.parsePrivateTags(signer.decrypt(event.content, event.pubKey)) }.getOrDefault(emptyList())
    }

    private fun applyBookmarks(event: Event) {
        val current = bookmarkEvent
        if (current != null && current.createdAt > event.createdAt) return
        bookmarkEvent = event
        _bookmarks.value = event.tags.filter { it.size > 1 && it[0] == "e" }.map { it[1] }.reversed()
    }

    private suspend fun newest(kind: Int, me: String): Event? {
        val filter = Filter(kinds = listOf(kind), authors = listOf(me))
        val fresh = network.query((relays.writeRelays() + relays.discoveryRelays()).distinct(), listOf(filter))
        cache.insert(fresh)
        val local = if (kind == Kinds.MUTE_LIST) muteEvent else bookmarkEvent
        return (fresh + listOfNotNull(local)).maxByOrNull { it.createdAt }
    }

    public suspend fun mutePubkey(pubkey: String): Unit = mutateMute("p", pubkey, add = true)
    public suspend fun unmutePubkey(pubkey: String): Unit = mutateMute("p", pubkey, add = false)
    public suspend fun muteWord(word: String): Unit = mutateMute("word", word.trim().lowercase(), add = true)
    public suspend fun unmuteWord(word: String): Unit = mutateMute("word", word, add = false)
    public suspend fun muteHashtag(tag: String): Unit = mutateMute("t", tag.trim().removePrefix("#").lowercase(), add = true)
    public suspend fun unmuteHashtag(tag: String): Unit = mutateMute("t", tag, add = false)

    private suspend fun mutateMute(tagName: String, value: String, add: Boolean) = mutex.withLock {
        if (value.isBlank()) return@withLock
        val signer = accountManager.requireSigner()
        val previous = _mutes.value
        val optimistic = when (tagName) {
            "p" -> previous.copy(pubkeys = if (add) previous.pubkeys + value else previous.pubkeys - value)
            "word" -> previous.copy(words = if (add) previous.words + value else previous.words - value.lowercase())
            else -> previous.copy(hashtags = if (add) previous.hashtags + value else previous.hashtags - value.lowercase())
        }
        _mutes.value = optimistic
        try {
            val latest = newest(Kinds.MUTE_LIST, signer.pubKey)
            var privateTags = latest?.let { decryptPrivate(it) } ?: emptyList()
            var content = latest?.content.orEmpty()
            val matches = { t: List<String> -> t.size > 1 && t[0] == tagName && t[1].equals(value, ignoreCase = tagName != "p") }
            var publicTags = latest?.tags?.map { it.toList() } ?: emptyList()
            if (add) {
                if (publicTags.none(matches) && privateTags.none(matches)) publicTags = publicTags + listOf(listOf(tagName, value))
            } else {
                publicTags = publicTags.filterNot(matches)
                if (privateTags.any(matches)) {
                    privateTags = privateTags.filterNot(matches)
                    content = if (privateTags.isEmpty()) "" else signer.nip44Encrypt(MuteList.privateTagsToJson(privateTags), signer.pubKey)
                }
            }
            val event = publisher.signAndPublish(Kinds.MUTE_LIST, content, publicTags.map { it.toTypedArray() }, notifyTagged = false)
            privateMuteTags = privateTags
            muteEvent = event
            _mutes.value = MuteList.fromTags(event.tags.map { it.toList() }) + MuteList.fromTags(privateTags)
        } catch (e: Exception) {
            _mutes.value = previous
            throw e
        }
    }

    public fun isBookmarked(eventId: String): Boolean = eventId in _bookmarks.value

    /** Adds/removes `["e", id]` in kind 10003, preserving other tags and content. */
    public suspend fun toggleBookmark(eventId: String): Boolean = mutex.withLock {
        val signer = accountManager.requireSigner()
        val previous = _bookmarks.value
        val adding = eventId !in previous
        _bookmarks.value = if (adding) listOf(eventId) + previous else previous - eventId
        try {
            val latest = newest(Kinds.BOOKMARKS, signer.pubKey)
            val tags = (latest?.tags?.toList() ?: emptyList()).filterNot { it.size > 1 && it[0] == "e" && it[1] == eventId }
            val newTags = if (adding) tags + listOf(arrayOf("e", eventId)) else tags
            val event = publisher.signAndPublish(Kinds.BOOKMARKS, latest?.content.orEmpty(), newTags, notifyTagged = false)
            bookmarkEvent = null
            applyBookmarks(event)
            adding
        } catch (e: Exception) {
            _bookmarks.value = previous
            throw e
        }
    }
}
