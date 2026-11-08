import { Outlet, useLocation } from 'react-router-dom';
import { BottomNav } from './BottomNav';
import { cn } from '@/lib/utils';

/** Full-screen flows hide the bottom bar. */
function isFullScreen(pathname: string): boolean {
  return pathname === '/create' || /^\/messages\/[^/]+/.test(pathname);
}

export function AppShell() {
  const { pathname } = useLocation();
  const fullScreen = isFullScreen(pathname);
  return (
    <div className={cn('min-h-dvh bg-background', !fullScreen && 'pb-bottom-bar')}>
      <Outlet />
      {!fullScreen && <BottomNav />}
    </div>
  );
}
