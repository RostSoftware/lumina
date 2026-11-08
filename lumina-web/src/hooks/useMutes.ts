import { useNostr } from '@nostrify/react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { NostrEvent } from '@nostrify/nostrify';
import { useMemo } from 'react';

import { useCurrentUser } from './useCurrentUser';
import { signWithFeedback, toastError } from './useNostrPublish';
import { toast } from './useToast';
import { EMPTY_MUTES, isMuted, parseMuteTags, type MuteList } from '@/lib/mute';
import { KIND_MUTE_LIST, newest } from '@/lib/nostr';

interface MuteData {
  event: NostrEvent | null;
  /** Tags decrypted from the private part of the list (never persisted). */
  privateTags: string[][];
}

/** The logged-in user's NIP-51 mute list (public + private items). */
export function useMuteList() {
  const { nostr } = useNostr();
  const { user } = useCurrentUser();

  const query = useQuery<MuteData>({
    queryKey: ['nostr', 'mutes', user?.pubkey ?? ''],
    enabled: !!user,
    staleTime: 5 * 60_000,
    queryFn: async ({ signal }) => {
      const event = newest(
        await nostr.query([{ kinds: [KIND_MUTE_LIST], authors: [user!.pubkey], limit: 1 }], {
          signal: AbortSignal.any([signal, AbortSignal.timeout(5000)]),
        }),
      ) ?? null;
      let privateTags: string[][] = [];
      if (event?.content && user) {
        try {
          const isNip04 = event.content.includes('?iv=');
          const plaintext = isNip04
            ? await user.signer.nip04?.decrypt(user.pubkey, event.content)
            : await user.signer.nip44?.decrypt(user.pubkey, event.content);
          const parsed: unknown = plaintext ? JSON.parse(plaintext) : [];
          if (Array.isArray(parsed)) {
            privateTags = parsed.filter((t): t is string[] => Array.isArray(t) && t.every((x) => typeof x === 'string'));
          }
        } catch {
          // Private part unreadable (signer declined) — use public items only.
        }
      }
      return { event, privateTags };
    },
  });

  const mutes: MuteList = useMemo(() => {
    if (!query.data) return EMPTY_MUTES;
    return parseMuteTags([...(query.data.event?.tags ?? []), ...query.data.privateTags]);
  }, [query.data]);

  return { ...query, mutes };
}

/** Filter helper bound to the current mute list. */
export function useMuteFilter() {
  const { mutes } = useMuteList();
  return useMemo(() => (event: NostrEvent) => !isMuted(event, mutes), [mutes]);
}

type MuteItem = { type: 'pubkey' | 'word' | 'hashtag'; value: string };

function tagFor(item: MuteItem): string[] {
  switch (item.type) {
    case 'pubkey':
      return ['p', item.value];
    case 'word':
      return ['word', item.value.toLowerCase()];
    case 'hashtag':
      return ['t', item.value.toLowerCase()];
  }
}

/** Add or remove an item on the public mute list. Fetches the latest list first. */
export function useMuteMutation() {
  const { nostr } = useNostr();
  const { user } = useCurrentUser();
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async ({ item, mute }: { item: MuteItem; mute: boolean }) => {
      if (!user) throw new Error('You need to log in first.');
      const latest = newest(
        await nostr.query([{ kinds: [KIND_MUTE_LIST], authors: [user.pubkey], limit: 1 }], { signal: AbortSignal.timeout(6000) }),
      );
      const [name, value] = tagFor(item);
      let tags = (latest?.tags ?? []).filter(([n, v]) => !(n === name && v === value));
      let content = latest?.content ?? '';

      if (!mute && latest?.content) {
        // The item may live in the private part — remove it there too.
        const cached = queryClient.getQueryData<MuteData>(['nostr', 'mutes', user.pubkey]);
        const inPrivate = cached?.privateTags.some(([n, v]) => n === name && v === value);
        if (inPrivate && cached && user.signer.nip44) {
          const remaining = cached.privateTags.filter(([n, v]) => !(n === name && v === value));
          content = await user.signer.nip44.encrypt(user.pubkey, JSON.stringify(remaining));
        }
      }
      if (mute) tags = [...tags, [name, value]];

      const event = await signWithFeedback(user, { kind: KIND_MUTE_LIST, content, tags });
      await nostr.event(event, { signal: AbortSignal.timeout(8000) });
      return event;
    },
    onSuccess: (_e, { item, mute }) => {
      queryClient.invalidateQueries({ queryKey: ['nostr', 'mutes'] });
      if (item.type === 'pubkey') toast({ title: mute ? 'Account muted' : 'Account unmuted' });
    },
    onError: (error) => toastError(error, 'Could not update mute list'),
  });
}
