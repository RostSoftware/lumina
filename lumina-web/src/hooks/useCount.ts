import { useNostr } from '@nostrify/react';
import { useQuery } from '@tanstack/react-query';
import type { NostrFilter } from '@nostrify/nostrify';
import { useAppContext } from './useAppContext';

/**
 * Count events matching a filter. Uses NIP-45 COUNT on the first read relay
 * that supports it and falls back to fetching (capped) when none does.
 */
export function useEventCount(key: unknown[], filter: NostrFilter | undefined, opts: { distinctAuthors?: boolean; cap?: number } = {}) {
  const { nostr } = useNostr();
  const { config } = useAppContext();
  const cap = opts.cap ?? 1000;

  return useQuery({
    queryKey: ['nostr', 'count', ...key],
    enabled: !!filter,
    staleTime: 5 * 60_000,
    queryFn: async ({ signal }) => {
      const readRelays = config.relayMetadata.relays.filter((r) => r.read).map((r) => r.url);
      if (!opts.distinctAuthors) {
        for (const url of readRelays.slice(0, 2)) {
          try {
            const relay = nostr.relay(url) as { count?: (f: NostrFilter[], o: { signal: AbortSignal }) => Promise<{ count: number }> };
            if (!relay.count) continue;
            const result = await relay.count([filter!], { signal: AbortSignal.any([signal, AbortSignal.timeout(2500)]) });
            if (typeof result.count === 'number') return { count: result.count, capped: false };
          } catch {
            // Relay doesn't support COUNT — try the next one / fall back.
          }
        }
      }
      const events = await nostr.query([{ ...filter!, limit: cap }], { signal: AbortSignal.any([signal, AbortSignal.timeout(6000)]) });
      const count = opts.distinctAuthors ? new Set(events.map((e) => e.pubkey)).size : new Set(events.map((e) => e.id)).size;
      return { count, capped: events.length >= cap };
    },
  });
}
