import { useMutation, useQuery } from '@tanstack/react-query';
import { NWCClient } from '@getalby/sdk';
import type { WebLNProvider } from '@webbtc/webln-types';

import { useCurrentUser } from './useCurrentUser';
import { useAccountValue } from './useAccountValue';
import { assertInvoiceAmount } from '@/lib/bolt11';
import { withTimeout } from '@/lib/signer';

/**
 * Nostr Wallet Connect (NIP-47) wallet for the current account.
 *
 * The NWC URI contains a secret, so it is stored only under this account's
 * local storage namespace and is wiped on logout. It is never logged or sent
 * anywhere except to the wallet's own relay by the NWC client.
 */
export interface StoredWallet {
  uri: string;
  alias?: string;
}

export function isNwcUri(value: string): boolean {
  return /^nostr\+walletconnect:\/\/[0-9a-f]{64}\?/i.test(value.trim()) || /^nostrwalletconnect:\/\/[0-9a-f]{64}\?/i.test(value.trim());
}

export function useWallet() {
  const { user } = useCurrentUser();
  const [wallet, setWallet] = useAccountValue<StoredWallet | null>(user?.pubkey, 'nwc', null);
  const [defaultZap, setDefaultZap] = useAccountValue<number>(user?.pubkey, 'default-zap', 21);
  const webln = (globalThis as { webln?: WebLNProvider }).webln ?? null;

  const info = useQuery({
    queryKey: ['wallet', 'info', user?.pubkey ?? '', wallet?.uri ? 'connected' : 'none'],
    enabled: !!wallet?.uri,
    staleTime: 60_000,
    retry: 1,
    queryFn: async () => {
      const client = new NWCClient({ nostrWalletConnectUrl: wallet!.uri });
      try {
        const [details, balance] = await Promise.all([
          withTimeout(client.getInfo(), 10_000, 'Wallet').catch(() => undefined),
          withTimeout(client.getBalance(), 10_000, 'Wallet').catch(() => undefined),
        ]);
        if (!details && !balance) throw new Error('Wallet did not respond');
        return { alias: details?.alias, balanceSats: balance ? Math.floor(balance.balance / 1000) : undefined, methods: details?.methods ?? [] };
      } finally {
        client.close();
      }
    },
  });

  const connect = useMutation({
    mutationFn: async (uri: string) => {
      const trimmed = uri.trim();
      if (!isNwcUri(trimmed)) throw new Error('That is not a valid nostr+walletconnect:// URI.');
      const client = new NWCClient({ nostrWalletConnectUrl: trimmed });
      try {
        const details = await withTimeout(client.getInfo(), 15_000, 'Wallet');
        if (details.methods && !details.methods.includes('pay_invoice')) {
          throw new Error('This wallet connection is not allowed to pay invoices.');
        }
        setWallet({ uri: trimmed, alias: details.alias || 'Lightning wallet' });
        return details;
      } finally {
        client.close();
      }
    },
  });

  const disconnect = () => setWallet(null);

  /** Pay a BOLT11 invoice with the connected NWC wallet (amount re-verified). */
  const payWithNwc = async (invoice: string, expectedMsat: number) => {
    if (!wallet) throw new Error('No wallet connected');
    assertInvoiceAmount(invoice, expectedMsat);
    const client = new NWCClient({ nostrWalletConnectUrl: wallet.uri });
    try {
      return await withTimeout(client.payInvoice({ invoice }), 30_000, 'Wallet payment');
    } finally {
      client.close();
    }
  };

  return { wallet, info, connect, disconnect, payWithNwc, webln, defaultZap, setDefaultZap };
}
