import { useNostrLogin } from '@nostrify/react/login';
import { useQueryClient } from '@tanstack/react-query';
import { wipeAccountStorage } from '@/lib/accountStorage';
import { clearDMCache } from './useMessages';

/**
 * Log out one account: removes its login (keys / bunker secret), wipes its
 * per-account storage (NWC URI, read markers, preferences) and clears every
 * cached query including decrypted DMs.
 */
export function useLogout() {
  const { logins, removeLogin } = useNostrLogin();
  const queryClient = useQueryClient();

  return (loginId?: string) => {
    const login = loginId ? logins.find((l) => l.id === loginId) : logins[0];
    if (!login) return;
    removeLogin(login.id);
    // Only wipe the account's data if no other login uses the same pubkey.
    if (!logins.some((l) => l.id !== login.id && l.pubkey === login.pubkey)) {
      wipeAccountStorage(login.pubkey);
      clearDMCache(login.pubkey);
    }
    queryClient.clear();
  };
}
