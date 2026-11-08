import { useCallback, useMemo, useState, type ReactNode } from 'react';
import { LoginDialogContext } from '@/contexts/LoginDialogContext';
import { useCurrentUser } from '@/hooks/useCurrentUser';
import { LoginDialog } from './LoginDialog';

export function LoginDialogProvider({ children }: { children: ReactNode }) {
  const [open, setOpen] = useState(false);
  const { user } = useCurrentUser();

  const openLogin = useCallback(() => setOpen(true), []);
  const requireLogin = useCallback(() => {
    if (user) return true;
    setOpen(true);
    return false;
  }, [user]);

  const value = useMemo(() => ({ openLogin, requireLogin }), [openLogin, requireLogin]);

  return (
    <LoginDialogContext.Provider value={value}>
      {children}
      <LoginDialog open={open} onOpenChange={setOpen} />
    </LoginDialogContext.Provider>
  );
}
