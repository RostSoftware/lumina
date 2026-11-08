import { useNostr } from '@nostrify/react';
import { useInfiniteQuery, useQuery } from '@tanstack/react-query';
import type { NostrEvent } from '@nostrify/nostrify';
import { useMemo } from 'react';

import { useCurrentUser } from './useCurrentUser';
import { useMuteList } from './useMutes';
import { useAccountValue } from './useAccountValue';
import { isMuted } from '@/lib/mute';
import { classifyNotification, groupNotifications, NOTIFICATION_KINDS, type NotificationItem } from '@/lib/notifications';
import { dedupeSort } from '@/lib/nostr';

const PAGE = 100;

/** Notification feed for the logged-in user, grouped Instagram-style. */
export function useNotifications() {
  const { nostr } = useNostr();
  const { user } = useCurrentUser();
  const { mutes } = useMuteList();

  const query = useInfiniteQuery<{ events: NostrEvent[]; nextUntil?: number }>({
    queryKey: ['nostr', 'notifications', user?.pubkey ?? ''],
    enabled: !!user,
    initialPageParam: undefined as number | undefined,
    staleTime: 30_000,
    refetchInterval: 60_000,
    queryFn: async ({ pageParam, signal }) => {
      const until = pageParam as number | undefined;
      const events = dedupeSort(
        await nostr.query([{ kinds: NOTIFICATION_KINDS, '#p': [user!.pubkey], limit: PAGE, ...(until ? { until } : {}) }], {
          signal: AbortSignal.any([signal, AbortSignal.timeout(8000)]),
        }),
      );
      const oldest = events[events.length - 1];
      return { events, nextUntil: oldest ? oldest.created_at - 1 : undefined };
    },
    getNextPageParam: (last) => last.nextUntil,
  });

  const groups = useMemo(() => {
    if (!user) return [];
    const items: NotificationItem[] = [];
    const seen = new Set<string>();
    for (const page of query.data?.pages ?? []) {
      for (const event of page.events) {
        if (seen.has(event.id)) continue;
        seen.add(event.id);
        const item = classifyNotification(event, user.pubkey);
        if (!item) continue;
        if (mutes.pubkeys.has(item.actor) || (item.type !== 'zap' && isMuted(event, mutes))) continue;
        items.push(item);
      }
    }
    return groupNotifications(items);
  }, [query.data, user, mutes]);

  return { ...query, groups };
}

/** "Last seen" timestamp for notifications, per account. */
export function useNotificationsSeen() {
  const { user } = useCurrentUser();
  return useAccountValue<number>(user?.pubkey, 'notifications-seen', 0);
}

/** Is there anything newer than the last-seen timestamp? Drives the red dot. */
export function useHasUnreadNotifications(): boolean {
  const { nostr } = useNostr();
  const { user } = useCurrentUser();
  const { mutes } = useMuteList();
  const [seen] = useNotificationsSeen();

  const { data } = useQuery({
    queryKey: ['nostr', 'notifications-unread', user?.pubkey ?? '', seen],
    enabled: !!user,
    refetchInterval: 60_000,
    queryFn: async ({ signal }) => {
      const since = seen || Math.floor(Date.now() / 1000) - 7 * 86400;
      return nostr.query([{ kinds: NOTIFICATION_KINDS, '#p': [user!.pubkey], since: since + 1, limit: 30 }], {
        signal: AbortSignal.any([signal, AbortSignal.timeout(6000)]),
      });
    },
  });

  if (!user || !data) return false;
  return data.some((event) => {
    const item = classifyNotification(event, user.pubkey);
    return !!item && item.created_at > seen && !mutes.pubkeys.has(item.actor);
  });
}

/** Batch-load the posts referenced by notifications (for thumbnails). */
export function useEventsByIds(ids: string[]) {
  const { nostr } = useNostr();
  const unique = [...new Set(ids)].sort();
  return useQuery({
    queryKey: ['nostr', 'events-by-id', unique.join(',')],
    enabled: unique.length > 0,
    staleTime: Infinity,
    queryFn: async ({ signal }) => {
      const events = await nostr.query([{ ids: unique, limit: unique.length }], { signal: AbortSignal.any([signal, AbortSignal.timeout(8000)]) });
      return new Map(events.map((e) => [e.id, e]));
    },
  });
}
