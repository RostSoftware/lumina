import { useState } from 'react';
import { Link } from 'react-router-dom';
import { Ellipsis } from 'lucide-react';
import type { NostrEvent } from '@nostrify/nostrify';
import { UserAvatar } from '@/components/common/UserAvatar';
import { UserName } from '@/components/common/UserName';
import { ICON_STROKE } from '@/components/common/Icons';
import { FollowButton } from '@/components/profile/FollowButton';
import { useCurrentUser } from '@/hooks/useCurrentUser';
import { useIsFollowing } from '@/hooks/useContacts';
import { shortAge, fullDate } from '@/lib/format';
import { profilePath } from '@/lib/nostr';
import { PostMenuSheet } from './PostMenuSheet';

export function PostHeader({ event, location, showFollow }: { event: NostrEvent; location?: string; showFollow?: boolean }) {
  const [menu, setMenu] = useState(false);
  const { user } = useCurrentUser();
  const following = useIsFollowing(event.pubkey);

  return (
    <div className="flex items-center gap-3 px-3 py-2">
      <Link to={profilePath(event.pubkey)} aria-hidden tabIndex={-1}>
        <UserAvatar pubkey={event.pubkey} size={32} />
      </Link>
      <div className="min-w-0 flex-1 leading-tight">
        <div className="flex min-w-0 items-center gap-1">
          <UserName pubkey={event.pubkey} showBadge className="text-body" />
          <span className="text-muted-foreground" aria-hidden>•</span>
          <time className="shrink-0 text-body text-muted-foreground" dateTime={new Date(event.created_at * 1000).toISOString()} title={fullDate(event.created_at)}>
            {shortAge(event.created_at)}
          </time>
        </div>
        {location && <p className="truncate text-meta">{location}</p>}
      </div>
      {showFollow && user && !following && user.pubkey !== event.pubkey && <FollowButton pubkey={event.pubkey} size="sm" />}
      <button
        type="button"
        aria-label="More options"
        onClick={() => setMenu(true)}
        className="inline-flex size-11 items-center justify-center rounded-full -mr-2 hover:opacity-60"
      >
        <Ellipsis strokeWidth={ICON_STROKE} className="size-6" />
      </button>
      <PostMenuSheet event={event} open={menu} onOpenChange={setMenu} />
    </div>
  );
}
