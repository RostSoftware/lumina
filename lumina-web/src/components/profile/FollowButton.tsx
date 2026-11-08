import { Button } from '@/components/ui/button';
import { useCurrentUser } from '@/hooks/useCurrentUser';
import { useFollow, useIsFollowing } from '@/hooks/useContacts';
import { useLoginDialog } from '@/hooks/useLoginDialog';
import { cn } from '@/lib/utils';

/** Follow / Following button with optimistic update. */
export function FollowButton({ pubkey, className, size = 'default' }: { pubkey: string; className?: string; size?: 'default' | 'sm' }) {
  const { user } = useCurrentUser();
  const { requireLogin } = useLoginDialog();
  const following = useIsFollowing(pubkey);
  const follow = useFollow();
  if (user?.pubkey === pubkey) return null;

  return (
    <Button
      variant={following ? 'secondary' : 'default'}
      className={cn(size === 'sm' ? 'h-8 px-4 text-meta font-semibold' : 'h-9 px-5 font-semibold', className)}
      disabled={follow.isPending}
      aria-pressed={following}
      onClick={() => {
        if (!requireLogin()) return;
        follow.mutate({ pubkey, follow: !following });
      }}
    >
      {following ? 'Following' : 'Follow'}
    </Button>
  );
}
