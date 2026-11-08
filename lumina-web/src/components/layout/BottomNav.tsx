import { NavLink, useLocation, useNavigate } from 'react-router-dom';
import { CreateIcon, DiscoverIcon, HeartIcon, HomeIcon } from '@/components/common/Icons';
import { UserAvatar } from '@/components/common/UserAvatar';
import { useCurrentUser } from '@/hooks/useCurrentUser';
import { useHasUnreadNotifications } from '@/hooks/useNotifications';
import { useLoginDialog } from '@/hooks/useLoginDialog';
import { npubOf } from '@/lib/nostr';
import { cn } from '@/lib/utils';
import { User } from 'lucide-react';

/**
 * The bottom navigation bar — on EVERY screen size (no sidebar, no rail).
 * Home · Discover · Create · Notifications · Profile. No labels.
 */
export function BottomNav() {
  const { user } = useCurrentUser();
  const { requireLogin } = useLoginDialog();
  const navigate = useNavigate();
  const location = useLocation();
  const unread = useHasUnreadNotifications();
  const myProfile = user ? `/${npubOf(user.pubkey)}` : undefined;
  const profileActive = !!myProfile && location.pathname === myProfile;

  const guarded = (path: string) => (e: React.MouseEvent) => {
    if (!user) {
      e.preventDefault();
      requireLogin();
      return;
    }
    if (path === location.pathname) {
      e.preventDefault();
      window.scrollTo({ top: 0, behavior: 'smooth' });
    }
  };

  const item = 'flex h-full flex-1 items-center justify-center text-foreground transition-opacity hover:opacity-70 [&_svg]:size-6';

  return (
    <nav aria-label="Main" className="fixed inset-x-0 bottom-0 z-40 border-t bg-surface pb-[var(--safe-bottom)]">
      <div className="column flex h-(--bar-height) items-stretch">
        <NavLink to="/" end aria-label="Home" className={item} onClick={(e) => location.pathname === '/' && (e.preventDefault(), window.scrollTo({ top: 0, behavior: 'smooth' }))}>
          {({ isActive }) => <HomeIcon active={isActive} />}
        </NavLink>
        <NavLink to="/discover" aria-label="Discover" className={item}>
          {({ isActive }) => <DiscoverIcon active={isActive} />}
        </NavLink>
        <NavLink to="/create" aria-label="Create post" className={item} onClick={guarded('/create')}>
          {({ isActive }) => <CreateIcon active={isActive} />}
        </NavLink>
        <NavLink to="/notifications" aria-label={unread ? 'Notifications (unread)' : 'Notifications'} className={item} onClick={guarded('/notifications')}>
          {({ isActive }) => (
            <span className="relative inline-flex">
              <HeartIcon active={isActive} />
              {unread && <span className="absolute -right-0.5 -bottom-1.5 left-0 mx-auto size-1.5 rounded-full bg-like" aria-hidden />}
            </span>
          )}
        </NavLink>
        <button
          type="button"
          aria-label="Profile"
          aria-current={profileActive ? 'page' : undefined}
          className={item}
          onClick={() => {
            if (!user) requireLogin();
            else if (profileActive) window.scrollTo({ top: 0, behavior: 'smooth' });
            else navigate(myProfile!);
          }}
        >
          {user ? (
            <span className={cn('rounded-full p-[1.5px]', profileActive ? 'ring-[1.5px] ring-foreground' : 'ring-0')}>
              <UserAvatar pubkey={user.pubkey} size={26} />
            </span>
          ) : (
            <User strokeWidth={1.75} />
          )}
        </button>
      </div>
    </nav>
  );
}
