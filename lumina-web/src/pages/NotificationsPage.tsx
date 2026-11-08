import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { useSeoMeta } from '@unhead/react';
import { Heart, Zap } from 'lucide-react';
import type { NostrEvent } from '@nostrify/nostrify';
import { TopBar } from '@/components/layout/TopBar';
import { EmptyState, ErrorState } from '@/components/common/States';
import { InfiniteTrigger } from '@/components/common/InfiniteTrigger';
import { UserAvatar } from '@/components/common/UserAvatar';
import { UserName } from '@/components/common/UserName';
import { FollowButton } from '@/components/profile/FollowButton';
import { ProgressiveImage } from '@/components/post/ProgressiveImage';
import { Skeleton } from '@/components/ui/skeleton';
import { useCurrentUser } from '@/hooks/useCurrentUser';
import { useEventsByIds, useNotifications, useNotificationsSeen } from '@/hooks/useNotifications';
import { formatSats, shortAge } from '@/lib/format';
import { postPath, profilePath } from '@/lib/nostr';
import type { NotificationGroup } from '@/lib/notifications';
import { parsePicturePost } from '@/lib/picture';
import { cn } from '@/lib/utils';
import MyProfileRedirect from './MyProfileRedirect';

function verb(group: NotificationGroup): string {
  switch (group.type) {
    case 'like':
      return group.items.some((i) => i.text) && group.items.length === 1 ? `reacted ${group.items[0].text} to your post.` : 'liked your post.';
    case 'comment':
      return 'commented:';
    case 'reply':
      return 'replied to your comment:';
    case 'mention':
      return 'mentioned you in a comment:';
    case 'repost':
      return 'reposted your post.';
    case 'zap':
      return `zapped ${formatSats(group.totalSats)} sats.`;
    case 'tag':
      return 'tagged you in a post.';
    case 'follow':
      return 'started following you.';
  }
}

function section(ts: number): string {
  const age = Date.now() / 1000 - ts;
  if (age < 86400) return 'Today';
  if (age < 7 * 86400) return 'This week';
  if (age < 30 * 86400) return 'This month';
  return 'Earlier';
}

export default function NotificationsPage() {
  useSeoMeta({ title: 'Notifications · LUMINA' });
  const { user } = useCurrentUser();
  const notifications = useNotifications();
  const [seen, setSeen] = useNotificationsSeen();
  const [initialSeen] = useState(seen);

  // Mark everything as seen once the list has loaded.
  const newest = notifications.groups[0]?.latest;
  useEffect(() => {
    if (newest && newest > seen) setSeen(newest);
  }, [newest, seen, setSeen]);

  const targetIds = useMemo(() => notifications.groups.map((g) => g.targetId).filter((id): id is string => !!id), [notifications.groups]);
  const targets = useEventsByIds(targetIds);

  if (!user) return <MyProfileRedirect />;

  const sections: { title: string; groups: NotificationGroup[] }[] = [];
  for (const g of notifications.groups) {
    // Hide groups about posts that turned out not to be picture posts.
    const target = g.targetId ? targets.data?.get(g.targetId) : undefined;
    if (target && target.kind !== 20 && g.type !== 'follow') continue;
    const title = section(g.latest);
    const last = sections[sections.length - 1];
    if (last?.title === title) last.groups.push(g);
    else sections.push({ title, groups: [g] });
  }

  return (
    <>
      <TopBar title="Notifications" />
      <main className="column">
        {notifications.isLoading ? (
          <div className="space-y-4 p-4">
            {Array.from({ length: 8 }, (_, i) => (
              <div key={i} className="flex items-center gap-3">
                <Skeleton className="size-11 rounded-full" />
                <Skeleton className="h-4 flex-1" />
                <Skeleton className="size-11 rounded-none" />
              </div>
            ))}
          </div>
        ) : notifications.isError && notifications.groups.length === 0 ? (
          <ErrorState error={notifications.error} onRetry={() => notifications.refetch()} />
        ) : sections.length === 0 ? (
          <EmptyState icon={<Heart strokeWidth={1.5} />} title="Activity on your posts" description="When someone likes, comments, zaps or follows you, you'll see it here." />
        ) : (
          <>
            {sections.map((s) => (
              <section key={s.title} className="border-b py-2">
                <h2 className="px-4 py-2 text-title">{s.title}</h2>
                <ul>
                  {s.groups.map((g) => (
                    <NotificationRow key={g.key} group={g} target={g.targetId ? targets.data?.get(g.targetId) : undefined} unread={g.latest > initialSeen} />
                  ))}
                </ul>
              </section>
            ))}
            <InfiniteTrigger onLoadMore={() => notifications.fetchNextPage()} hasMore={!!notifications.hasNextPage} loading={notifications.isFetchingNextPage} />
          </>
        )}
      </main>
    </>
  );
}

function NotificationRow({ group, target, unread }: { group: NotificationGroup; target?: NostrEvent; unread: boolean }) {
  const [first, second] = group.actors;
  const others = group.actors.length - (second ? 2 : 1);
  const preview = group.items[0].text;
  const post = target ? parsePicturePost(target) : undefined;
  const link = target ? postPath(target) : group.type === 'follow' ? profilePath(first) : undefined;

  return (
    <li className={cn('flex items-center gap-3 px-4 py-2', unread && 'bg-accent/5')}>
      <Link to={profilePath(first)} className="relative shrink-0" aria-hidden tabIndex={-1}>
        {second ? (
          <span className="relative block size-11">
            <span className="absolute top-0 left-0"><UserAvatar pubkey={second} size={32} /></span>
            <span className="absolute right-0 bottom-0 rounded-full ring-2 ring-background"><UserAvatar pubkey={first} size={32} /></span>
          </span>
        ) : (
          <UserAvatar pubkey={first} size={44} />
        )}
        {group.type === 'zap' && <Zap className="absolute -right-1 -bottom-1 size-4 rounded-full bg-background fill-zap text-zap" aria-hidden />}
      </Link>
      <div className="min-w-0 flex-1 text-body">
        <UserName pubkey={first} className="align-top" />
        {second && (
          <>
            {others > 0 ? ', ' : ' and '}
            <UserName pubkey={second} className="align-top" />
          </>
        )}
        {others > 0 && <span> and {others} {others === 1 ? 'other' : 'others'}</span>}{' '}
        <span>{verb(group)}</span>
        {preview && group.type !== 'like' && group.type !== 'tag' && <span className="text-muted-foreground"> {preview.slice(0, 100)}</span>}{' '}
        <span className="text-muted-foreground">{shortAge(group.latest)}</span>
      </div>
      {group.type === 'follow' ? (
        <FollowButton pubkey={first} size="sm" />
      ) : post ? (
        <Link to={postPath(post.event)} className="relative block size-11 shrink-0 overflow-hidden" aria-label="Open post">
          <ProgressiveImage image={post.images[0]} alt={post.images[0].alt || 'Your post'} compact />
        </Link>
      ) : link ? (
        <Link to={link} className="block size-11 shrink-0 bg-surface-muted" aria-label="Open post" />
      ) : null}
    </li>
  );
}
