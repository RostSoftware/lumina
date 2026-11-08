import { Navigate } from 'react-router-dom';
import { useEffect } from 'react';
import { useCurrentUser } from '@/hooks/useCurrentUser';
import { useLoginDialog } from '@/hooks/useLoginDialog';
import { npubOf } from '@/lib/nostr';

export default function MyProfileRedirect() {
  const { user } = useCurrentUser();
  const { openLogin } = useLoginDialog();
  useEffect(() => {
    if (!user) openLogin();
  }, [user, openLogin]);
  return <Navigate to={user ? `/${npubOf(user.pubkey)}` : '/'} replace />;
}
