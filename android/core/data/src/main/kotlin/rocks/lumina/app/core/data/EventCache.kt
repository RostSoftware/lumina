package rocks.lumina.app.core.data

import android.content.Context
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.store.sqlite.EventStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rocks.lumina.app.core.nostr.Kinds
import rocks.lumina.app.core.nostr.di.ApplicationScope
import rocks.lumina.app.core.signer.AccountManager

/**
 * Local event cache backed by Quartz's `SQLiteEventStore` — one database per account (plus one for
 * logged-out browsing). Only signature-verified events are inserted (verification happens in
 * `NostrNetwork`). Gift wraps are stored encrypted as received; decrypted DMs never touch disk.
 */
@Singleton
public class EventCache @Inject constructor(
    @ApplicationContext private val context: Context,
    accountManager: AccountManager,
    @ApplicationScope scope: CoroutineScope,
) {
    @Volatile private var owner: String = ANON
    @Volatile private var store: EventStore = open(ANON)

    init {
        scope.launch {
            accountManager.active.map { it?.pubkey ?: ANON }.distinctUntilChanged().collect { pubkey -> switchTo(pubkey) }
        }
        scope.launch {
            accountManager.loggedOut.collect { pubkey -> wipe(pubkey) }
        }
    }

    @Synchronized
    private fun switchTo(pubkey: String) {
        if (pubkey == owner) return
        val old = store
        store = open(pubkey)
        owner = pubkey
        runCatching { old.close() }
    }

    private fun dbFile(pubkey: String): File = context.getDatabasePath("events_${pubkey.take(16)}.db")

    private fun open(pubkey: String): EventStore {
        val file = dbFile(pubkey)
        file.parentFile?.mkdirs()
        return EventStore(dbName = file.absolutePath)
    }

    private fun wipe(pubkey: String) {
        synchronized(this) {
            if (owner == pubkey) {
                runCatching { store.close() }
                store = open(ANON)
                owner = ANON
            }
        }
        val base = dbFile(pubkey)
        listOf(base, File(base.path + "-wal"), File(base.path + "-shm"), File(base.path + "-journal")).forEach { runCatching { it.delete() } }
    }

    public suspend fun insert(events: Collection<Event>) {
        if (events.isEmpty()) return
        withContext(Dispatchers.IO) {
            val storable = events.filter { it.kind !in EPHEMERAL_SKIP }
            runCatching { store.batchInsert(storable) }
        }
    }

    public suspend fun insert(event: Event): Unit = insert(listOf(event))

    public suspend fun query(filters: List<Filter>): List<Event> = withContext(Dispatchers.IO) {
        runCatching { store.query<Event>(filters) }.getOrDefault(emptyList())
    }

    public suspend fun query(filter: Filter): List<Event> = query(listOf(filter))

    public suspend fun delete(filter: Filter) {
        withContext(Dispatchers.IO) { runCatching { store.delete(filter) } }
    }

    private companion object {
        const val ANON = "anonymous"
        val EPHEMERAL_SKIP = setOf(Kinds.BLOSSOM_AUTH, 24133, 23194, 23195, 22242)
    }
}
