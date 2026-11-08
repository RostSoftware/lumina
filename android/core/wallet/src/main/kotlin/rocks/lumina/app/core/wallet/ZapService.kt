package rocks.lumina.app.core.wallet

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip57Zaps.ZapReceiptEvent
import com.vitorpamplona.quartz.nip57Zaps.ZapRequestEvent
import javax.inject.Inject
import javax.inject.Singleton
import rocks.lumina.app.core.data.ProfileRepository
import rocks.lumina.app.core.data.RelayRepository
import rocks.lumina.app.core.signer.AccountManager

/** Outcome of a zap attempt. */
public sealed interface ZapResult {
    /** Paid through the connected NWC wallet. */
    public data object Paid : ZapResult

    /** No wallet connected: open this invoice in an external Lightning wallet (`lightning:` intent). */
    public data class ExternalInvoice(val bolt11: String) : ZapResult
}

/**
 * NIP-57 zaps: resolve the recipient's LNURL, build + sign a kind 9734 zap request with Quartz
 * (not published), fetch the invoice from the callback and pay it via NWC — or hand it to an external
 * wallet when no NWC wallet is connected.
 */
@Singleton
public class ZapService @Inject constructor(
    private val lnurl: LnurlClient,
    private val nwc: NwcRepository,
    private val profiles: ProfileRepository,
    private val relays: RelayRepository,
    private val accountManager: AccountManager,
) {
    /** Zaps [recipient] (optionally for [event], e.g. a kind 20 post) with [sats]. */
    public suspend fun zap(recipient: String, event: Event?, sats: Long, comment: String = ""): ZapResult {
        val signer = accountManager.requireSigner()
        val profile = profiles.peek(recipient) ?: profiles.refresh(recipient)
        val address = profile?.lightningAddress ?: throw LnurlException("This user has no lightning address")
        val info = lnurl.resolve(address)
        val amountMsat = sats * 1000
        val zapRelays = (relays.readRelays() + relays.inboxRelaysOf(listOf(recipient))).distinct().take(6)
            .mapNotNull { RelayUrlNormalizer.normalizeOrNull(it) }.toSet()
        val zapRequestJson = if (info.allowsNostr && info.nostrPubkey != null) {
            val template = if (event != null) {
                ZapRequestEvent.build(event, zapRelays, comment, ZapReceiptEvent.ZapType.PUBLIC, amountMillisats = amountMsat, lnurl = info.lnurl.takeIf { it.startsWith("lnurl", true) })
            } else {
                ZapRequestEvent.build(
                    com.vitorpamplona.quartz.nip01Core.tags.people.PTag(recipient),
                    zapRelays,
                    comment,
                    ZapReceiptEvent.ZapType.PUBLIC,
                    amountMillisats = amountMsat,
                    lnurl = info.lnurl.takeIf { it.startsWith("lnurl", true) },
                )
            }
            ZapRequestEvent.sign(template, signer).toJson()
        } else {
            null
        }
        val invoice = lnurl.fetchInvoice(info, amountMsat, zapRequestJson, comment)
        return if (nwc.wallet.value != null) {
            nwc.payInvoice(invoice)
            ZapResult.Paid
        } else {
            ZapResult.ExternalInvoice(invoice)
        }
    }
}
