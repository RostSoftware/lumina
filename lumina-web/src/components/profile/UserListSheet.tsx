import { Link } from 'react-router-dom';
import { BottomSheet } from '@/components/common/BottomSheet';
import { UserAvatar } from '@/components/common/UserAvatar';
import { UserName } from '@/components/common/UserName';
import { FollowButton } from './FollowButton';
import { profilePath } from '@/lib/nostr';
import type { ReactNode } from 'react';

/** A sheet listing users (likers, zappers, followers…). */
export function UserListSheet({
  title,
  users,
  open,
  onOpenChange,
  trailing,
}: {
  title: string;
  users: string[];
  open: boolean;
  onOpenChange: (o: boolean) => void;
  trailing?: (pubkey: string) => ReactNode;
}) {
  return (
    <BottomSheet open={open} onOpenChange={onOpenChange} title={title}>
      {users.length === 0 ? (
        <p className="p-8 text-center text-body text-muted-foreground">Nobody yet.</p>
      ) : (
        <ul className="pb-4">
          {users.slice(0, 300).map((pk) => (
            <li key={pk} className="flex items-center gap-3 px-4 py-2">
              <Link to={profilePath(pk)} onClick={() => onOpenChange(false)} aria-hidden tabIndex={-1}>
                <UserAvatar pubkey={pk} size={44} />
              </Link>
              <div className="min-w-0 flex-1" onClick={() => onOpenChange(false)}>
                <UserName pubkey={pk} showBadge />
              </div>
              {trailing ? trailing(pk) : <FollowButton pubkey={pk} size="sm" />}
            </li>
          ))}
        </ul>
      )}
    </BottomSheet>
  );
}
