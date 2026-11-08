import { useCallback, useMemo, useSyncExternalStore } from 'react';
import { accountKey, writeAccountValue } from '@/lib/accountStorage';

function subscribe(callback: () => void): () => void {
  window.addEventListener('lumina:storage', callback);
  window.addEventListener('storage', callback);
  return () => {
    window.removeEventListener('lumina:storage', callback);
    window.removeEventListener('storage', callback);
  };
}

/** React state bound to a per-account localStorage value. */
export function useAccountValue<T>(pubkey: string | undefined, name: string, fallback: T): [T, (value: T) => void] {
  const key = pubkey ? accountKey(pubkey, name) : '';
  const raw = useSyncExternalStore(subscribe, () => {
    if (!key) return null;
    try {
      return localStorage.getItem(key);
    } catch {
      return null;
    }
  });

  const value = useMemo<T>(() => {
    if (raw === null) return fallback;
    try {
      return JSON.parse(raw) as T;
    } catch {
      return fallback;
    }
    // fallback is a literal at call sites; only re-parse when the stored string changes.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [raw]);

  const set = useCallback(
    (next: T) => {
      if (pubkey) writeAccountValue(pubkey, name, next);
    },
    [pubkey, name],
  );

  return [value, set];
}
