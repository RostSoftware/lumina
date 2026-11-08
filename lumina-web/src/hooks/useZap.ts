import { useMutation, useQueryClient } from '@tanstack/react-query';
import type { NostrEvent, NostrMetadata } from '@nostrify/nostrify';
import { nip57 } from 'nostr-tools';
import type { WebLNProvider } from '@webbtc/webln-types';

import { useCurrentUser } from './useCurrentUser';
import { useAppContext } from './useAppContext';
import { useWallet } from './useWallet';
import { signWithFeedback } from './useNostrPublish';
import { assertInvoiceAmount, invoiceCommitsTo } from '@/lib/bolt11';
import { resolveLnurlPay } from '@/lib/lnurlPay';

export interface ZapParams {
  recipient: string;
  metadata: NostrMetadata | undefined;
  /** Post being zapped; omit for a profile zap. */
  event?: NostrEvent;
  sats: number;
  comment: string;
}

export type ZapResult = { status: 'paid'; via: 'nwc' | 'webln' } | { status: 'invoice'; invoice: string };

/**
 * NIP-57 zap. Resolves the recipient's LNURL, signs a kind 9734 zap request,
 * fetches an invoice, VERIFIES it charges exactly the approved amount and
 * commits to our request, then pays via NWC → WebLN → returns the invoice
 * for manual payment (QR).
 */
export function useZap() {
  const { user } = useCurrentUser();
  const { config } = useAppContext();
  const { wallet, payWithNwc, webln } = useWallet();
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async ({ recipient, metadata, event, sats, comment }: ZapParams): Promise<ZapResult> => {
      if (!user) throw new Error('You need to log in first.');
      if (!Number.isFinite(sats) || sats < 1) throw new Error('Enter an amount of at least 1 sat.');
      if (!metadata?.lud16 && !metadata?.lud06) throw new Error('This account has no lightning address.');

      const params = await resolveLnurlPay({ lud06: metadata.lud06, lud16: metadata.lud16 }, AbortSignal.timeout(10_000));
      if (!params.allowsNostr || !params.nostrPubkey) throw new Error("This lightning address doesn't support zaps.");
      const msat = sats * 1000;
      if (msat < params.minSendable || msat > params.maxSendable) {
        throw new Error(`Amount must be between ${Math.ceil(params.minSendable / 1000)} and ${Math.floor(params.maxSendable / 1000)} sats.`);
      }

      const relays = config.relayMetadata.relays.filter((r) => r.read).map((r) => r.url).slice(0, 5);
      const template = event
        ? nip57.makeZapRequest({ event, amount: msat, comment, relays })
        : nip57.makeZapRequest({ pubkey: recipient, amount: msat, comment, relays });
      const zapRequest = await signWithFeedback(user, { kind: template.kind, content: template.content, tags: template.tags });
      const zapRequestJson = JSON.stringify(zapRequest);

      const url = new URL(params.callback);
      url.searchParams.set('amount', String(msat));
      url.searchParams.set('nostr', zapRequestJson);
      if (metadata.lud06) url.searchParams.set('lnurl', metadata.lud06);
      const res = await fetch(url, { signal: AbortSignal.timeout(15_000) });
      const body = (await res.json()) as { pr?: unknown; reason?: unknown };
      if (!res.ok || typeof body.pr !== 'string') {
        throw new Error(typeof body.reason === 'string' ? body.reason : 'Lightning service did not return an invoice.');
      }
      const invoice = body.pr;
      const decoded = assertInvoiceAmount(invoice, msat);
      if (!invoiceCommitsTo(decoded, [zapRequestJson, params.metadata])) {
        throw new Error('The lightning service returned an invoice for a different request. Payment cancelled.');
      }

      if (wallet) {
        await payWithNwc(invoice, msat);
        return { status: 'paid', via: 'nwc' };
      }
      if (webln) {
        try {
          await webln.enable();
          await (webln as WebLNProvider).sendPayment(invoice);
          return { status: 'paid', via: 'webln' };
        } catch {
          // Fall back to manual payment.
        }
      }
      return { status: 'invoice', invoice };
    },
    onSuccess: (result, { event }) => {
      if (result.status === 'paid' && event) {
        setTimeout(() => queryClient.invalidateQueries({ queryKey: ['nostr', 'post-stats', event.id] }), 2500);
      }
    },
  });
}
