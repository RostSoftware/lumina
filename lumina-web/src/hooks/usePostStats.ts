import { useNostr } from '@nostrify/react';
import { useQuery } from '@tanstack/react-query';
import type { NostrEvent, NPool } from '@nostrify/nostrify';
import { useMemo } from 'react';

import { useCurrentUser } from './useCurrentUser';
import { useMuteList } from './useMutes';
import { isMuted } from '@/lib/mute';
import {
  KIND_COMMENT,
  KIND_DELETE,
  KIND_GENERIC_REPOST,
  KIND_REACTION,
  KIND_REPOST,
  KIND_ZAP_RECEIPT,
} from '@/lib/nostr';
import { parseZapReceipt, type ZapInfo } from '@/lib/zapReceipt';

/** Raw interaction events for one post, split by kind. */
export interface PostInteractions {
  reactions: NostrEvent[];
  reposts: NostrEvent[];
  comments: NostrEvent[];
  zaps: NostrEvent[];
}

const EMPTY: PostInteractions = { reactions: [], reposts: [], comments: [], zaps: [] };

/**
 * Batches interaction lookups from many posts (e.g. a feed page) into ONE
 * combined relay query: kinds 7/6/16/9735 by `#e` and kind 1111 by `#E`,
 * split client-side per post.
 */
class StatsBatcher {
  private pending = new Map<string, { resolve: (v: PostInteractions) => void; reject: (e: unknown) => void }[]>();
  private timer: ReturnType<typeof setTimeout> | undefined;

  constructor(private nostr: NPool) {}

  load(id: string): Promise<PostInteractions> {
    return new Promise((resolve, reject) => {
      const list = this.pending.get(id) ?? [];
      list.push({ resolve, reject });
      this.pending.set(id, list);
      if (!this.timer) this.timer = setTimeout(() => this.flush(), 60);
    });
  }

  private async flush() {
    const batch = this.pending;
    this.pending = new Map();
    this.timer = undefined;
    const ids = [...batch.keys()];
    try {
      const limit = Math.min(5000, ids.length * 300);
      const events = await this.nostr.query(
        [
          { kinds: [KIND_REACTION, KIND_REPOST, KIND_GENERIC_REPOST, KIND_ZAP_RECEIPT, KIND_DELETE], '#e': ids, limit },
          { kinds: [KIND_COMMENT], '#E': ids, limit },
        ],
        { signal: AbortSignal.timeout(8000) },
      );
      // Deletions requested by the authors of interactions (e.g. un-likes).
      const deleted = new Set<string>();
      for (const e of events) {
        if (e.kind === KIND_DELETE) for (const [n, v] of e.tags) if (n === 'e') deleted.add(`${e.pubkey}:${v}`);
      }

      const byId = new Map<string, PostInteractions>(ids.map((id) => [id, { reactions: [], reposts: [], comments: [], zaps: [] }]));
      const seen = new Set<string>();
      for (const e of events) {
        if (seen.has(e.id) || e.kind === KIND_DELETE || deleted.has(`${e.pubkey}:${e.id}`)) continue;
        seen.add(e.id);
        if (e.kind === KIND_COMMENT) {
          const root = e.tags.find(([n]) => n === 'E')?.[1];
          if (root) byId.get(root)?.comments.push(e);
          continue;
        }
        // For reactions / reposts / zaps the target is the last `e` tag (NIP-25).
        const eTags = e.tags.filter(([n]) => n === 'e').map(([, v]) => v);
        const target = [...eTags].reverse().find((v) => byId.has(v));
        if (!target) continue;
        const bucket = byId.get(target)!;
        if (e.kind === KIND_REACTION) bucket.reactions.push(e);
        else if (e.kind === KIND_REPOST || e.kind === KIND_GENERIC_REPOST) bucket.reposts.push(e);
        else if (e.kind === KIND_ZAP_RECEIPT) bucket.zaps.push(e);
      }
      for (const [id, waiters] of batch) for (const w of waiters) w.resolve(byId.get(id) ?? EMPTY);
    } catch (error) {
      for (const waiters of batch.values()) for (const w of waiters) w.reject(error);
    }
  }
}

const batchers = new WeakMap<NPool, StatsBatcher>();
function getBatcher(nostr: NPool): StatsBatcher {
  let b = batchers.get(nostr);
  if (!b) {
    b = new StatsBatcher(nostr);
    batchers.set(nostr, b);
  }
  return b;
}

export interface PostStats {
  likeCount: number;
  repostCount: number;
  commentCount: number;
  zapCount: number;
  zapTotal: number;
  topZappers: { pubkey: string; sats: number }[];
  zaps: ZapInfo[];
  /** Reaction content → count (e.g. "+" / "❤️" / ":custom:"). */
  emojis: { content: string; count: number; url?: string }[];
  likedByMe?: NostrEvent;
  repostedByMe?: NostrEvent;
  likers: string[];
}

function isLike(e: NostrEvent) {
  return e.content.trim() !== '-';
}

export function usePostInteractions(eventId: string | undefined) {
  const { nostr } = useNostr();
  return useQuery({
    queryKey: ['nostr', 'post-stats', eventId ?? ''],
    enabled: !!eventId,
    staleTime: 60_000,
    queryFn: () => getBatcher(nostr).load(eventId!),
  });
}

/** Counts and derived data for a post. */
export function usePostStats(eventId: string | undefined) {
  const query = usePostInteractions(eventId);
  const { user } = useCurrentUser();
  const { mutes } = useMuteList();

  const stats = useMemo<PostStats>(() => {
    const data = query.data ?? EMPTY;
    const reactions = data.reactions.filter((e) => isLike(e) && !mutes.pubkeys.has(e.pubkey));
    // One reaction per person counts.
    const byAuthor = new Map<string, NostrEvent>();
    for (const r of reactions) {
      const prev = byAuthor.get(r.pubkey);
      if (!prev || r.created_at > prev.created_at) byAuthor.set(r.pubkey, r);
    }
    const emojiMap = new Map<string, { count: number; url?: string }>();
    for (const r of byAuthor.values()) {
      const content = r.content.trim() || '+';
      const custom = content.match(/^:([\w-]+):$/);
      const url = custom ? r.tags.find(([n, name]) => n === 'emoji' && name === custom[1])?.[2] : undefined;
      const entry = emojiMap.get(content) ?? { count: 0, url };
      entry.count++;
      emojiMap.set(content, entry);
    }

    const zaps = data.zaps.map(parseZapReceipt).filter((z): z is ZapInfo => !!z);
    const zapperTotals = new Map<string, number>();
    for (const z of zaps) if (z.sender) zapperTotals.set(z.sender, (zapperTotals.get(z.sender) ?? 0) + z.sats);

    const reposters = new Set(data.reposts.map((r) => r.pubkey));
    const comments = data.comments.filter((c) => !isMuted(c, mutes));

    return {
      likeCount: byAuthor.size,
      repostCount: reposters.size,
      commentCount: comments.length,
      zapCount: zaps.length,
      zapTotal: zaps.reduce((s, z) => s + z.sats, 0),
      topZappers: [...zapperTotals.entries()].map(([pubkey, sats]) => ({ pubkey, sats })).sort((a, b) => b.sats - a.sats).slice(0, 5),
      zaps: zaps.sort((a, b) => b.sats - a.sats),
      emojis: [...emojiMap.entries()].map(([content, v]) => ({ content, ...v })).sort((a, b) => b.count - a.count),
      likedByMe: user ? byAuthor.get(user.pubkey) : undefined,
      repostedByMe: user ? data.reposts.find((r) => r.pubkey === user.pubkey) : undefined,
      likers: [...byAuthor.values()].sort((a, b) => b.created_at - a.created_at).map((r) => r.pubkey),
    };
  }, [query.data, user, mutes]);

  return { ...query, stats, comments: query.data?.comments ?? [] };
}
