import { useNostr } from '@nostrify/react';
import { useInfiniteQuery, useQuery, useQueryClient } from '@tanstack/react-query';
import type { NostrEvent, NostrFilter } from '@nostrify/nostrify';
import { useCallback, useMemo } from 'react';
import { verifyEvent } from 'nostr-tools';

import { useMuteList } from './useMutes';
import { isMuted } from '@/lib/mute';
import { chunkAuthors, dedupeSort, isHex64, KIND_GENERIC_REPOST, KIND_PICTURE } from '@/lib/nostr';
import { parsePicturePost, type PicturePost } from '@/lib/picture';

export type FeedSpec =
  | { type: 'following'; authors: string[] }
  | { type: 'discover'; hashtag?: string }
  | { type: 'author'; pubkey: string }
  | { type: 'tagged'; pubkey: string };

const PAGE = 24;

function feedFilters(spec: FeedSpec, extra: Pick<NostrFilter, 'until' | 'since' | 'limit'>): NostrFilter[] {
  switch (spec.type) {
    case 'following':
      return chunkAuthors(spec.authors, { kinds: [KIND_PICTURE], ...extra });
    case 'discover':
      return [{ kinds: [KIND_PICTURE], ...(spec.hashtag ? { '#t': [spec.hashtag.toLowerCase()] } : {}), ...extra }];
    case 'author':
      return [{ kinds: [KIND_PICTURE], authors: [spec.pubkey], ...extra }];
    case 'tagged':
      return [{ kinds: [KIND_PICTURE], '#p': [spec.pubkey], ...extra }];
  }
}

function feedKey(spec: FeedSpec): unknown[] {
  switch (spec.type) {
    case 'following':
      return ['following', spec.authors.length, spec.authors.slice(0, 5).join(',')];
    case 'discover':
      return ['discover', spec.hashtag ?? ''];
    case 'author':
    case 'tagged':
      return [spec.type, spec.pubkey];
  }
}

interface FeedPage {
  posts: PicturePost[];
  /** created_at - 1 of the oldest raw event, or undefined at the end. */
  nextUntil?: number;
}

/** Infinite feed of validated kind 20 picture posts, newest first. */
export function usePictureFeed(spec: FeedSpec | undefined) {
  const { nostr } = useNostr();
  const { mutes } = useMuteList();
  const { data: deleted } = useQuery<string[]>({
    queryKey: ['lumina', 'deleted'],
    queryFn: () => [],
    initialData: [],
    staleTime: Infinity,
  });

  const query = useInfiniteQuery<FeedPage>({
    queryKey: ['nostr', 'feed', ...(spec ? feedKey(spec) : ['none'])],
    enabled: !!spec && (spec.type !== 'following' || spec.authors.length > 0),
    initialPageParam: undefined as number | undefined,
    staleTime: 2 * 60_000,
    queryFn: async ({ pageParam, signal }) => {
      const until = pageParam as number | undefined;
      const events = await nostr.query(feedFilters(spec!, { limit: PAGE, ...(until ? { until } : {}) }), {
        signal: AbortSignal.any([signal, AbortSignal.timeout(8000)]),
      });
      const sorted = dedupeSort(events);
      const posts = sorted.map(parsePicturePost).filter((p): p is PicturePost => !!p);
      const oldest = sorted[sorted.length - 1];
      return { posts, nextUntil: oldest ? oldest.created_at - 1 : undefined };
    },
    getNextPageParam: (last) => last.nextUntil,
  });

  const posts = useMemo(() => {
    const seen = new Set<string>();
    const hidden = new Set(deleted ?? []);
    const out: PicturePost[] = [];
    for (const page of query.data?.pages ?? []) {
      for (const post of page.posts) {
        if (seen.has(post.event.id) || hidden.has(post.event.id)) continue;
        seen.add(post.event.id);
        // Authors are not muted on their own profile grid.
        if (spec?.type !== 'author' && isMuted(post.event, mutes)) continue;
        out.push(post);
      }
    }
    return out;
  }, [query.data, mutes, spec?.type, deleted]);

  return { ...query, posts };
}

/**
 * Polls for events newer than the newest post in a feed. Returns the number
 * of fresh posts so the UI can show a "new posts" pill.
 */
export function useNewPostsCount(spec: FeedSpec | undefined, newestAt: number | undefined) {
  const { nostr } = useNostr();
  const { mutes } = useMuteList();
  const query = useQuery({
    queryKey: ['nostr', 'feed-new', ...(spec ? feedKey(spec) : ['none']), newestAt ?? 0],
    enabled: !!spec && !!newestAt && (spec.type !== 'following' || spec.authors.length > 0),
    refetchInterval: 30_000,
    refetchIntervalInBackground: false,
    queryFn: async ({ signal }) => {
      const events = await nostr.query(feedFilters(spec!, { since: newestAt! + 1, limit: 50 }), {
        signal: AbortSignal.any([signal, AbortSignal.timeout(6000)]),
      });
      return dedupeSort(events).filter((e) => parsePicturePost(e) && !isMuted(e, mutes));
    },
  });
  return { count: query.data?.length ?? 0, authors: [...new Set((query.data ?? []).map((e) => e.pubkey))].slice(0, 3) };
}

/** Reset a feed to its first page (pull-to-refresh / new posts pill). */
export function useRefreshFeed(spec: FeedSpec | undefined) {
  const queryClient = useQueryClient();
  return useCallback(async () => {
    if (!spec) return;
    const key = ['nostr', 'feed', ...feedKey(spec)];
    queryClient.setQueryData(key, (data: { pages: FeedPage[]; pageParams: unknown[] } | undefined) =>
      data ? { pages: data.pages.slice(0, 1), pageParams: data.pageParams.slice(0, 1) } : data,
    );
    await queryClient.invalidateQueries({ queryKey: key });
    await queryClient.invalidateQueries({ queryKey: ['nostr', 'feed-new'] });
  }, [queryClient, spec]);
}

/** Reposts (kind 16, k=20) by a user, resolved to the original picture posts. */
export function useRepostFeed(pubkey: string | undefined) {
  const { nostr } = useNostr();
  return useInfiniteQuery<FeedPage>({
    queryKey: ['nostr', 'profile-posts', 'reposts', pubkey ?? ''],
    enabled: !!pubkey,
    initialPageParam: undefined as number | undefined,
    queryFn: async ({ pageParam, signal }) => {
      const until = pageParam as number | undefined;
      const sig = AbortSignal.any([signal, AbortSignal.timeout(8000)]);
      const reposts = dedupeSort(
        await nostr.query([{ kinds: [KIND_GENERIC_REPOST], authors: [pubkey!], '#k': [String(KIND_PICTURE)], limit: PAGE, ...(until ? { until } : {}) }], { signal: sig }),
      );
      const resolved = new Map<string, NostrEvent>();
      const missing: string[] = [];
      for (const r of reposts) {
        const id = r.tags.find(([n]) => n === 'e')?.[1];
        if (!isHex64(id)) continue;
        try {
          const embedded = JSON.parse(r.content) as NostrEvent;
          if (embedded?.id === id && embedded.kind === KIND_PICTURE && verifyEvent(embedded)) {
            resolved.set(id, embedded);
            continue;
          }
        } catch {
          // empty or invalid content: fetch by id
        }
        missing.push(id);
      }
      if (missing.length) {
        const fetched = await nostr.query([{ ids: missing, limit: missing.length }], { signal: sig });
        for (const e of fetched) if (e.kind === KIND_PICTURE) resolved.set(e.id, e);
      }
      const posts: PicturePost[] = [];
      for (const r of reposts) {
        const id = r.tags.find(([n]) => n === 'e')?.[1];
        const original = id ? resolved.get(id) : undefined;
        const post = original && parsePicturePost(original);
        if (post && !posts.some((p) => p.event.id === post.event.id)) posts.push(post);
      }
      const oldest = reposts[reposts.length - 1];
      return { posts, nextUntil: oldest ? oldest.created_at - 1 : undefined };
    },
    getNextPageParam: (last) => last.nextUntil,
  });
}

/** Load a single event by id (with optional relay hints and author). */
export function useEventById(id: string | undefined, opts: { relays?: string[]; author?: string } = {}) {
  const { nostr } = useNostr();
  const { relays, author } = opts;
  return useQuery({
    queryKey: ['nostr', 'event', id ?? ''],
    enabled: !!id,
    staleTime: Infinity,
    queryFn: async ({ signal }) => {
      const sig = AbortSignal.any([signal, AbortSignal.timeout(8000)]);
      const filter: NostrFilter = { ids: [id!], limit: 1, ...(author ? { authors: [author] } : {}) };
      const lookups = [nostr.query([filter], { signal: sig })];
      const hints = (relays ?? []).filter((r) => /^wss:\/\//.test(r)).slice(0, 3);
      if (hints.length) lookups.push(nostr.group(hints).query([filter], { signal: sig }).catch(() => []));
      const results = (await Promise.all(lookups)).flat();
      const event = results.find((e) => e.id === id);
      if (!event) throw new Error('Post not found');
      return event;
    },
    retry: 1,
  });
}

/** Load an addressable event (naddr). */
export function useAddressableEvent(pointer: { kind: number; pubkey: string; identifier: string; relays?: string[] } | undefined) {
  const { nostr } = useNostr();
  return useQuery({
    queryKey: ['nostr', 'addr', pointer?.kind, pointer?.pubkey, pointer?.identifier],
    enabled: !!pointer,
    queryFn: async ({ signal }) => {
      const filter: NostrFilter = { kinds: [pointer!.kind], authors: [pointer!.pubkey], '#d': [pointer!.identifier], limit: 1 };
      const events = await nostr.query([filter], { signal: AbortSignal.any([signal, AbortSignal.timeout(8000)]) });
      const event = events.sort((a, b) => b.created_at - a.created_at)[0];
      if (!event) throw new Error('Not found');
      return event;
    },
  });
}
