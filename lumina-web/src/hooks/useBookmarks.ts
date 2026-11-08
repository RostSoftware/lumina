import { useNostr } from '@nostrify/react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { NostrEvent } from '@nostrify/nostrify';

import { useCurrentUser } from './useCurrentUser';
import { signWithFeedback, toastError } from './useNostrPublish';
import { KIND_BOOKMARKS, newest } from '@/lib/nostr';

/** NIP-51 bookmark list (kind 10003). */
export function useBookmarks() {
  const { nostr } = useNostr();
  const { user } = useCurrentUser();
  const query = useQuery({
    queryKey: ['nostr', 'bookmarks', user?.pubkey ?? ''],
    enabled: !!user,
    staleTime: 5 * 60_000,
    queryFn: async ({ signal }) =>
      newest(
        await nostr.query([{ kinds: [KIND_BOOKMARKS], authors: [user!.pubkey], limit: 1 }], {
          signal: AbortSignal.any([signal, AbortSignal.timeout(5000)]),
        }),
      ) ?? null,
  });
  const ids = new Set((query.data?.tags ?? []).filter(([n]) => n === 'e').map(([, v]) => v));
  return { ...query, ids };
}

/** Toggle a bookmark. Fetches the latest list first and keeps unknown tags/content. */
export function useToggleBookmark() {
  const { nostr } = useNostr();
  const { user } = useCurrentUser();
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async ({ event, bookmark }: { event: NostrEvent; bookmark: boolean }) => {
      if (!user) throw new Error('You need to log in first.');
      const latest = newest(
        await nostr.query([{ kinds: [KIND_BOOKMARKS], authors: [user.pubkey], limit: 1 }], { signal: AbortSignal.timeout(6000) }),
      );
      const tags = (latest?.tags ?? []).filter(([n, v]) => !(n === 'e' && v === event.id));
      if (bookmark) tags.push(['e', event.id]);
      const signed = await signWithFeedback(user, { kind: KIND_BOOKMARKS, content: latest?.content ?? '', tags });
      await nostr.event(signed, { signal: AbortSignal.timeout(8000) });
      return signed;
    },
    onMutate: async ({ event, bookmark }) => {
      if (!user) return;
      const key = ['nostr', 'bookmarks', user.pubkey];
      const previous = queryClient.getQueryData<NostrEvent | null>(key);
      const base = previous ?? { kind: KIND_BOOKMARKS, tags: [], content: '', created_at: 0, id: '', pubkey: user.pubkey, sig: '' };
      const tags = base.tags.filter(([n, v]) => !(n === 'e' && v === event.id));
      if (bookmark) tags.push(['e', event.id]);
      queryClient.setQueryData(key, { ...base, tags });
      return { previous };
    },
    onError: (error, _v, context) => {
      if (user) queryClient.setQueryData(['nostr', 'bookmarks', user.pubkey], context?.previous ?? null);
      toastError(error, 'Could not update bookmarks');
    },
    onSuccess: (signed) => {
      if (user) queryClient.setQueryData(['nostr', 'bookmarks', user.pubkey], signed);
    },
  });
}
