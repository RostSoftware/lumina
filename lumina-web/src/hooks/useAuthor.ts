import { type NostrEvent, type NostrMetadata, NSchema as n } from '@nostrify/nostrify';
import { useNostr } from '@nostrify/react';
import { useQuery } from '@tanstack/react-query';
import { sanitizeUrl, sanitizeWebsite } from '@/lib/sanitize';

/** Parse kind 0 content and sanitize every URL field at the parse layer. */
export function parseMetadata(event: NostrEvent): NostrMetadata | undefined {
  try {
    const metadata = n.json().pipe(n.metadata()).parse(event.content);
    return {
      ...metadata,
      picture: sanitizeUrl(metadata.picture),
      banner: sanitizeUrl(metadata.banner),
      website: sanitizeWebsite(metadata.website),
    };
  } catch {
    return undefined;
  }
}

export function useAuthor(pubkey: string | undefined) {
  const { nostr } = useNostr();

  return useQuery<{ event?: NostrEvent; metadata?: NostrMetadata }>({
    queryKey: ['nostr', 'author', pubkey ?? ''],
    queryFn: async ({ signal }) => {
      if (!pubkey) return {};
      const events = await nostr.query([{ kinds: [0], authors: [pubkey], limit: 1 }], {
        signal: AbortSignal.any([signal, AbortSignal.timeout(3000)]),
      });
      const event = events.sort((a, b) => b.created_at - a.created_at)[0];
      if (!event) return {};
      return { event, metadata: parseMetadata(event) };
    },
    enabled: !!pubkey,
    staleTime: 5 * 60 * 1000,
    retry: 2,
  });
}
