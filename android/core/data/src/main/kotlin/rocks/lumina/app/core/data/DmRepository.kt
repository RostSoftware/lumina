package rocks.lumina.app.core.data

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip17Dm.NIP17Factory
import com.vitorpamplona.quartz.nip17Dm.messages.ChatMessageEvent
import com.vitorpamplona.quartz.nip59Giftwrap.seals.SealEvent
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import rocks.lumina.app.core.nostr.Kinds
import rocks.lumina.app.core.nostr.NostrNetwork
import rocks.lumina.app.core.nostr.di.ApplicationScope
import rocks.lumina.app.core.signer.AccountManager

public enum class SendState { SENT, SENDING, FAILED }

/** A decrypted direct message. Held in memory only — never written to disk in plaintext. */
public data class DmMessage(
    val id: String,
    val peer: String,
    val sender: String,
    val content: String,
    val createdAt: Long,
    val legacy: Boolean,
    val state: SendState = SendState.SENT,
)

public data class Conversation(val peer: String, val last: DmMessage, val unread: Boolean)

/**
 * NIP-17 private messages (kind 14 in a kind 13 seal in a kind 1059 gift wrap), plus reading legacy
 * NIP-04 kind 4. Decryption runs sequentially off the main thread (remote signers are slow and prompt).
 */
@Singleton
public class DmRepository @Inject constructor(
    private val network: NostrNetwork,
    private val cache: EventCache,
    private val relays: RelayRepository,
    private val accountManager: AccountManager,
    private val settings: SettingsRepository,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val messagesByPeer = MutableStateFlow<Map<String, List<DmMessage>>>(emptyMap())
    private val readMarkers = MutableStateFlow<Map<String, Long>>(emptyMap())
    private val processed = ConcurrentHashMap.newKeySet<String>()
    private val queue = Channel<Event>(Channel.UNLIMITED)
    private var jobs: List<Job> = emptyList()
    private var startedFor: String? = null
    private var watchJob: Job? = null
    private val wrapIds = ConcurrentHashMap.newKeySet<String>()

    private val _loading = MutableStateFlow(false)
    public val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _hasUnread = MutableStateFlow(false)
    /** Unread-DM dot for the Home top bar (compares gift-wrap ids, so nothing is decrypted on Home). */
    public val hasUnread: StateFlow<Boolean> = _hasUnread.asStateFlow()

    public val conversations: Flow<List<Conversation>> = kotlinx.coroutines.flow.combine(messagesByPeer, readMarkers) { map, markers ->
        val me = accountManager.activePubkey
        map.mapNotNull { (peer, list) ->
            val last = list.maxByOrNull { it.createdAt } ?: return@mapNotNull null
            Conversation(peer, last, last.sender != me && last.createdAt > (markers[peer] ?: 0))
        }.sortedByDescending { it.last.createdAt }
    }

    public fun messages(peer: String): Flow<List<DmMessage>> = messagesByPeer.map { it[peer].orEmpty().sortedBy { m -> m.createdAt } }

    public fun reset() {
        jobs.forEach { it.cancel() }
        jobs = emptyList()
        watchJob?.cancel()
        startedFor = null
        processed.clear()
        wrapIds.clear()
        messagesByPeer.value = emptyMap()
        readMarkers.value = emptyMap()
        _hasUnread.value = false
        _loading.value = false
    }

    private fun inboxRelays(): List<String> = (relays.ownDmRelays() + relays.readRelays()).distinct()

    /** Watches gift wraps addressed to the user to drive [hasUnread] without decrypting anything. */
    public fun startUnreadWatch(pubkey: String) {
        watchJob?.cancel()
        watchJob = scope.launch {
            val seen = settings.seenWrapIds(pubkey)
            network.stream(inboxRelays(), listOf(Filter(kinds = listOf(Kinds.GIFT_WRAP), tags = mapOf("p" to listOf(pubkey)), limit = 50)))
                .collect { wrap ->
                    wrapIds.add(wrap.id)
                    if (wrap.id !in seen && startedFor == null) _hasUnread.value = true
                }
        }
    }

    /** Starts loading + decrypting the inbox (idempotent per account). Called by the Messages screens. */
    public fun start() {
        val signer = accountManager.signerOrNull() ?: return
        val me = signer.pubKey
        if (startedFor == me) return
        startedFor = me
        _loading.value = true
        _hasUnread.value = false
        scope.launch { readMarkers.value = settings.dmReadMarkers(me) }
        val giftFilter = Filter(kinds = listOf(Kinds.GIFT_WRAP), tags = mapOf("p" to listOf(me)))
        val legacyIn = Filter(kinds = listOf(Kinds.LEGACY_DM), tags = mapOf("p" to listOf(me)), limit = 300)
        val legacyOut = Filter(kinds = listOf(Kinds.LEGACY_DM), authors = listOf(me), limit = 300)
        jobs = listOf(
            scope.launch(Dispatchers.Default) {
                for (event in queue) {
                    if (startedFor != me) continue
                    runCatching { process(event, me, signer) }
                }
            },
            scope.launch {
                cache.query(listOf(giftFilter, legacyIn, legacyOut)).forEach { queue.send(it) }
                merge(
                    network.stream(inboxRelays(), listOf(giftFilter)),
                    network.stream(relays.readRelays(), listOf(legacyIn, legacyOut)),
                ).collect { event ->
                    cache.insert(event)
                    queue.send(event)
                }
            },
            scope.launch {
                kotlinx.coroutines.delay(6_000)
                _loading.value = false
            },
        )
        scope.launch { settings.addSeenWrapIds(me, wrapIds.toList()) }
    }

    private suspend fun process(event: Event, me: String, signer: NostrSigner) {
        if (!processed.add(event.id)) return
        when (event.kind) {
            Kinds.GIFT_WRAP -> {
                settings.addSeenWrapIds(me, listOf(event.id))
                val wrap = event as? GiftWrapEvent ?: return
                val seal = runCatching { wrap.unwrapThrowing(signer) }.getOrNull() as? SealEvent ?: return
                val rumor = runCatching { seal.unsealRumorThrowing(signer) }.getOrNull() ?: return
                // NIP-59 anti-spoofing: the rumor author must be the seal signer.
                if (rumor.pubKey != null && rumor.pubKey != seal.pubKey) return
                val inner = seal.unsealed(rumor)
                if (inner.kind != Kinds.CHAT_MESSAGE) return
                val participants = inner.tags.filter { it.size > 1 && it[0] == "p" }.map { it[1] }.toSet() + inner.pubKey
                val others = participants - me
                if (others.size != 1) return // group DMs are not supported in the UI (same as web)
                val peer = others.first()
                add(DmMessage(inner.id, peer, inner.pubKey, inner.content, inner.createdAt, legacy = false))
            }
            Kinds.LEGACY_DM -> {
                val recipient = event.tags.firstOrNull { it.size > 1 && it[0] == "p" }?.get(1) ?: return
                val peer = if (event.pubKey == me) recipient else event.pubKey
                if (peer == me && recipient == me) return
                val text = runCatching { signer.nip04Decrypt(event.content, peer) }.getOrNull() ?: return
                add(DmMessage(event.id, peer, event.pubKey, text, event.createdAt, legacy = true))
            }
        }
    }

    private fun add(message: DmMessage) {
        messagesByPeer.update { map ->
            val list = map[message.peer].orEmpty()
            if (list.any { it.id == message.id && it.state == SendState.SENT }) return@update map
            val withoutLocal = list.filterNot { it.id == message.id }
            map + (message.peer to (withoutLocal + message))
        }
    }

    private fun setState(peer: String, id: String, state: SendState, newId: String? = null) {
        messagesByPeer.update { map ->
            val list = map[peer].orEmpty().map { if (it.id == id) it.copy(state = state, id = newId ?: it.id) else it }
            map + (peer to list.distinctBy { it.id })
        }
    }

    /** Sends a NIP-17 DM: one gift wrap to the recipient's 10050 relays and one to our own. */
    public suspend fun send(peer: String, text: String) {
        val localId = "local-" + UUID.randomUUID()
        val me = accountManager.requireSigner().pubKey
        add(DmMessage(localId, peer, me, text, TimeUtils.now(), legacy = false, state = SendState.SENDING))
        deliver(peer, text, localId)
    }

    public suspend fun retry(peer: String, localId: String) {
        val message = messagesByPeer.value[peer]?.firstOrNull { it.id == localId } ?: return
        setState(peer, localId, SendState.SENDING)
        deliver(peer, message.content, localId)
    }

    private suspend fun deliver(peer: String, text: String, localId: String) {
        try {
            val signer = accountManager.requireSigner()
            val template = ChatMessageEvent.build(text, listOf(PTag(peer)))
            val result = NIP17Factory().createMessageNIP17(template, signer)
            var deliveredToPeer = false
            result.wraps.forEach { wrap ->
                val recipient = wrap.recipientPubKey() ?: return@forEach
                val targets = if (recipient == signer.pubKey) relays.ownDmRelays() else relays.dmInboxOf(recipient)
                val accepted = network.publish(wrap, targets)
                if (recipient == peer && accepted.isNotEmpty()) deliveredToPeer = true
            }
            if (!deliveredToPeer) throw PublishFailedException()
            processed.add(result.msg.id)
            setState(peer, localId, SendState.SENT, newId = result.msg.id)
        } catch (e: Exception) {
            setState(peer, localId, SendState.FAILED)
            throw e
        }
    }

    public suspend fun markRead(peer: String) {
        val me = accountManager.activePubkey ?: return
        val newest = messagesByPeer.value[peer]?.maxOfOrNull { it.createdAt } ?: TimeUtils.now()
        readMarkers.update { it + (peer to newest) }
        settings.setDmRead(me, peer, newest)
    }
}
