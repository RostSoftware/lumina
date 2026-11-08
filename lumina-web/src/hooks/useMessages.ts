import { useNostr } from '@nostrify/react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { NostrEvent } from '@nostrify/nostrify';
import { useMemo } from 'react';

import { useCurrentUser } from './useCurrentUser';
import { useAppContext } from './useAppContext';
import { useAccountValue } from './useAccountValue';
import { useMuteList } from './useMutes';
import { createRumor, decryptLegacyDM, unwrapGiftWrap, wrapRumor, type DirectMessage } from '@/lib/dm';
import { KIND_DM_RELAYS, KIND_GIFT_WRAP, KIND_LEGACY_DM, KIND_RELAY_LIST, newest, parseDMRelays, parseRelayList } from '@/lib/nostr';
import { SignerError } from '@/lib/signer';

/**
 * In-memory cache of decrypted messages, keyed by account then by source
 * event id. Decrypted DMs are NEVER written to disk; logging out clears this.
 */
const decryptCache = new Map<string, Map<string, DirectMessage | null>>();

export function clearDMCache(pubkey?: string) {
  if (pubkey) decryptCache.delete(pubkey);
  else decryptCache.clear();
}

function cacheFor(pubkey: string) {
  let map = decryptCache.get(pubkey);
  if (!map) {
    map = new Map();
    decryptCache.set(pubkey, map);
  }
  return map;
}

/** A user's NIP-17 inbox relays (kind 10050). */
export function useDMRelays(pubkey: string | undefined) {
  const { nostr } = useNostr();
  return useQuery({
    queryKey: ['nostr', 'dm-relays', pubkey ?? ''],
    enabled: !!pubkey,
    staleTime: 10 * 60_000,
    queryFn: async ({ signal }) => {
      const event = newest(
        await nostr.query([{ kinds: [KIND_DM_RELAYS], authors: [pubkey!], limit: 1 }], { signal: AbortSignal.any([signal, AbortSignal.timeout(5000)]) }),
      );
      return { event: event ?? null, relays: parseDMRelays(event) };
    },
  });
}

/** Run async tasks with limited concurrency. */
async function mapLimit<T, R>(items: T[], limit: number, fn: (item: T) => Promise<R>): Promise<R[]> {
  const results: R[] = new Array(items.length);
  let index = 0;
  const workers = Array.from({ length: Math.min(limit, items.length) }, async () => {
    while (index < items.length) {
      const i = index++;
      results[i] = await fn(items[i]);
    }
  });
  await Promise.all(workers);
  return results;
}

export interface Conversation {
  peer: string;
  messages: DirectMessage[];
  last: DirectMessage;
  unread: boolean;
}

/** All decrypted DMs (NIP-17 + legacy NIP-04) of the logged-in user. */
export function useMessages() {
  const { nostr } = useNostr();
  const { user } = useCurrentUser();
  const { config } = useAppContext();
  const { data: myDMRelays } = useDMRelays(user?.pubkey);
  const { mutes } = useMuteList();
  const [readMarks] = useAccountValue<Record<string, number>>(user?.pubkey, 'dm-read', {});

  const query = useQuery({
    queryKey: ['nostr', 'messages', user?.pubkey ?? '', (myDMRelays?.relays ?? []).join(',')],
    enabled: !!user,
    refetchInterval: 20_000,
    staleTime: 10_000,
    queryFn: async ({ signal }) => {
      const me = user!.pubkey;
      const sig = AbortSignal.any([signal, AbortSignal.timeout(10_000)]);
      const filters = [
        { kinds: [KIND_GIFT_WRAP], '#p': [me], limit: 1000 },
        { kinds: [KIND_LEGACY_DM], '#p': [me], limit: 500 },
        { kinds: [KIND_LEGACY_DM], authors: [me], limit: 500 },
      ];
      const sources = [nostr.query(filters, { signal: sig })];
      const inbox = (myDMRelays?.relays ?? []).filter((r) => !config.relayMetadata.relays.some((x) => x.url === r));
      if (inbox.length) sources.push(nostr.group(inbox).query(filters, { signal: sig }).catch(() => []));
      const events = new Map<string, NostrEvent>();
      for (const e of (await Promise.all(sources)).flat()) events.set(e.id, e);

      const cache = cacheFor(me);
      const todo = [...events.values()].filter((e) => !cache.has(e.id));
      await mapLimit(todo, 4, async (event) => {
        try {
          const msg = event.kind === KIND_GIFT_WRAP
            ? await unwrapGiftWrap(user!.signer, event, me)
            : await decryptLegacyDM(user!.signer, event, me);
          cache.set(event.id, msg ?? null);
        } catch {
          // Undecryptable (or signer declined) — skip permanently for this session.
          cache.set(event.id, null);
        }
      });

      const byId = new Map<string, DirectMessage>();
      for (const msg of cache.values()) if (msg) byId.set(`${msg.protocol}:${msg.id}`, msg);
      return {
        messages: [...byId.values()].sort((a, b) => a.created_at - b.created_at),
        unsupported: !user!.signer.nip44,
        sourceIds: [...events.keys()],
      };
    },
  });

  const conversations = useMemo<Conversation[]>(() => {
    const map = new Map<string, DirectMessage[]>();
    for (const m of query.data?.messages ?? []) {
      if (mutes.pubkeys.has(m.peer)) continue;
      const list = map.get(m.peer) ?? [];
      list.push(m);
      map.set(m.peer, list);
    }
    return [...map.entries()]
      .map(([peer, messages]) => {
        const last = messages[messages.length - 1];
        const unread = last.pubkey !== user?.pubkey && last.created_at > (readMarks[peer] ?? 0);
        return { peer, messages, last, unread };
      })
      .sort((a, b) => b.last.created_at - a.last.created_at);
  }, [query.data, mutes, readMarks, user?.pubkey]);

  return { ...query, conversations };
}

/** Mark a conversation as read up to now. */
export function useMarkConversationRead() {
  const { user } = useCurrentUser();
  const [marks, setMarks] = useAccountValue<Record<string, number>>(user?.pubkey, 'dm-read', {});
  return (peer: string, at: number) => {
    if ((marks[peer] ?? 0) >= at) return;
    setMarks({ ...marks, [peer]: at });
  };
}

/** Where to deliver a gift wrap for `pubkey`: kind 10050, else NIP-65 read relays. */
async function inboxRelaysFor(nostr: ReturnType<typeof useNostr>['nostr'], pubkey: string): Promise<string[]> {
  const events = await nostr.query([{ kinds: [KIND_DM_RELAYS, KIND_RELAY_LIST], authors: [pubkey], limit: 4 }], {
    signal: AbortSignal.timeout(5000),
  });
  const dm = parseDMRelays(newest(events.filter((e) => e.kind === KIND_DM_RELAYS)));
  if (dm.length) return dm;
  return parseRelayList(newest(events.filter((e) => e.kind === KIND_RELAY_LIST))).filter((r) => r.read).map((r) => r.url);
}

/** Send a NIP-17 DM: wrap for the recipient and for ourselves. */
export function useSendMessage() {
  const { nostr } = useNostr();
  const { user } = useCurrentUser();
  const { config } = useAppContext();
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async ({ peer, content }: { peer: string; content: string }) => {
      if (!user) throw new Error('You need to log in first.');
      if (!user.signer.nip44) throw new SignerError('unsupported', 'Your signer does not support NIP-44 encryption, which private messages require.');
      const rumor = createRumor(user.pubkey, peer, content);
      const [peerWrap, selfWrap] = [await wrapRumor(user.signer, rumor, peer), await wrapRumor(user.signer, rumor, user.pubkey)];

      const writeRelays = config.relayMetadata.relays.filter((r) => r.write).map((r) => r.url);
      const [peerInbox, myInbox] = await Promise.all([inboxRelaysFor(nostr, peer), inboxRelaysFor(nostr, user.pubkey)]);
      const peerTargets = peerInbox.length ? peerInbox : writeRelays;
      const myTargets = myInbox.length ? myInbox : writeRelays;

      await nostr.group(peerTargets).event(peerWrap, { signal: AbortSignal.timeout(10_000) });
      await nostr.group(myTargets).event(selfWrap, { signal: AbortSignal.timeout(10_000) }).catch(() => undefined);

      const message: DirectMessage = {
        id: rumor.id,
        sourceId: selfWrap.id,
        pubkey: user.pubkey,
        peer,
        content,
        created_at: rumor.created_at,
        protocol: 'nip17',
      };
      cacheFor(user.pubkey).set(selfWrap.id, message);
      return message;
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['nostr', 'messages'] }),
  });
}

/**
 * Cheap unread indicator for the Home top bar: are there gift wraps / DMs we
 * haven't seen in the Messages screen yet? Does NOT decrypt anything, so it
 * never triggers signer prompts.
 */
export function useHasNewMessages(): boolean {
  const { nostr } = useNostr();
  const { user } = useCurrentUser();
  const [seenIds] = useAccountValue<string[]>(user?.pubkey, 'dm-seen-ids', []);
  const [seenAt] = useAccountValue<number>(user?.pubkey, 'dm-seen-at', 0);

  const { data } = useQuery({
    queryKey: ['nostr', 'dm-unread', user?.pubkey ?? '', seenAt],
    enabled: !!user,
    refetchInterval: 60_000,
    queryFn: async ({ signal }) => {
      // Gift wraps carry randomized timestamps up to two days in the past.
      const since = Math.max(0, (seenAt || Math.floor(Date.now() / 1000)) - 2 * 86400);
      const events = await nostr.query([{ kinds: [KIND_GIFT_WRAP, KIND_LEGACY_DM], '#p': [user!.pubkey], since, limit: 50 }], {
        signal: AbortSignal.any([signal, AbortSignal.timeout(6000)]),
      });
      return events.filter((e) => e.pubkey !== user!.pubkey).map((e) => e.id);
    },
  });
  if (!data || !seenAt) return false;
  const seen = new Set(seenIds);
  return data.some((id) => !seen.has(id));
}

/** Remember which DM source events exist (called by the Messages screen). */
export function useMarkMessagesSeen() {
  const { user } = useCurrentUser();
  const [, setIds] = useAccountValue<string[]>(user?.pubkey, 'dm-seen-ids', []);
  const [, setAt] = useAccountValue<number>(user?.pubkey, 'dm-seen-at', 0);
  return (ids: string[]) => {
    setIds(ids.slice(-1000));
    setAt(Math.floor(Date.now() / 1000));
  };
}
