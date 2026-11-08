import { useNostr } from '@nostrify/react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { NostrEvent } from '@nostrify/nostrify';

import { useCurrentUser } from './useCurrentUser';
import { signWithFeedback, toastError } from './useNostrPublish';
import { followsOf, KIND_CONTACTS, newest } from '@/lib/nostr';

/** Latest kind 3 contact list for a pubkey. */
export function useContacts(pubkey: string | undefined) {
  const { nostr } = useNostr();
  return useQuery({
    queryKey: ['nostr', 'contacts', pubkey ?? ''],
    enabled: !!pubkey,
    staleTime: 2 * 60_000,
    queryFn: async ({ signal }) => {
      const events = await nostr.query([{ kinds: [KIND_CONTACTS], authors: [pubkey!], limit: 1 }], {
        signal: AbortSignal.any([signal, AbortSignal.timeout(5000)]),
      });
      return newest(events) ?? null;
    },
  });
}

/** Pubkeys followed by a user. */
export function useFollowing(pubkey: string | undefined) {
  const query = useContacts(pubkey);
  return { ...query, follows: followsOf(query.data ?? undefined) };
}

/** Does the logged-in user follow `pubkey`? */
export function useIsFollowing(pubkey: string | undefined): boolean {
  const { user } = useCurrentUser();
  const { follows } = useFollowing(user?.pubkey);
  return !!pubkey && follows.includes(pubkey);
}

/**
 * Follow / unfollow. ALWAYS fetches the latest kind 3 from relays before
 * modifying it, so a stale cached list can never wipe the user's follows.
 * Updates optimistically and rolls back on failure.
 */
export function useFollow() {
  const { nostr } = useNostr();
  const { user } = useCurrentUser();
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async ({ pubkey, follow }: { pubkey: string; follow: boolean }) => {
      if (!user) throw new Error('You need to log in first.');
      const key = ['nostr', 'contacts', user.pubkey];
      const cached = queryClient.getQueryData<NostrEvent | null>(['nostr', 'contacts-fresh', user.pubkey]);

      const fresh = newest(
        await nostr.query([{ kinds: [KIND_CONTACTS], authors: [user.pubkey], limit: 1 }], {
          signal: AbortSignal.timeout(6000),
        }),
      );
      const latest = newest([fresh, cached].filter((e): e is NostrEvent => !!e));
      // Safety net: relays returned nothing although we previously saw a list.
      if (!latest && cached) throw new Error('Could not load your latest follow list. Try again.');

      const tags = (latest?.tags ?? []).filter(([n, v]) => !(n === 'p' && v === pubkey));
      if (follow) tags.push(['p', pubkey]);

      const event = await signWithFeedback(user, {
        kind: KIND_CONTACTS,
        content: latest?.content ?? '',
        tags,
      });
      await nostr.event(event, { signal: AbortSignal.timeout(8000) });
      queryClient.setQueryData(key, event);
      queryClient.setQueryData(['nostr', 'contacts-fresh', user.pubkey], event);
      return event;
    },
    onMutate: async ({ pubkey, follow }) => {
      if (!user) return;
      const key = ['nostr', 'contacts', user.pubkey];
      await queryClient.cancelQueries({ queryKey: key });
      const previous = queryClient.getQueryData<NostrEvent | null>(key);
      if (previous) queryClient.setQueryData(['nostr', 'contacts-fresh', user.pubkey], previous);
      const base = previous ?? { kind: 3, tags: [], content: '', created_at: 0, id: '', pubkey: user.pubkey, sig: '' };
      const tags = base.tags.filter(([n, v]) => !(n === 'p' && v === pubkey));
      if (follow) tags.push(['p', pubkey]);
      queryClient.setQueryData(key, { ...base, tags });
      return { previous };
    },
    onError: (error, _vars, context) => {
      if (user) queryClient.setQueryData(['nostr', 'contacts', user.pubkey], context?.previous ?? null);
      toastError(error, 'Could not update follows');
    },
    onSettled: (_data, _error, { pubkey }) => {
      queryClient.invalidateQueries({ queryKey: ['nostr', 'followers', pubkey] });
    },
  });
}
