/**
 * Per-account local storage.
 *
 * Small, non-secret per-account preferences (notification "last seen", default
 * zap amount, read markers) and the account's NWC connection live under the
 * prefix `lumina:<pubkey>:`. Logging out calls {@link wipeAccountStorage},
 * which removes every key for that account.
 */

const PREFIX = 'lumina:';

export function accountKey(pubkey: string, name: string): string {
  return `${PREFIX}${pubkey}:${name}`;
}

export function readAccountValue<T>(pubkey: string | undefined, name: string, fallback: T): T {
  if (!pubkey) return fallback;
  try {
    const raw = localStorage.getItem(accountKey(pubkey, name));
    return raw === null ? fallback : (JSON.parse(raw) as T);
  } catch {
    return fallback;
  }
}

export function writeAccountValue<T>(pubkey: string, name: string, value: T): void {
  try {
    if (value === undefined || value === null) localStorage.removeItem(accountKey(pubkey, name));
    else localStorage.setItem(accountKey(pubkey, name), JSON.stringify(value));
    window.dispatchEvent(new CustomEvent('lumina:storage', { detail: accountKey(pubkey, name) }));
  } catch {
    // Storage full or disabled — preferences are best-effort.
  }
}

/** Remove every stored key belonging to this account. */
export function wipeAccountStorage(pubkey: string): void {
  const prefix = `${PREFIX}${pubkey}:`;
  const keys: string[] = [];
  for (let i = 0; i < localStorage.length; i++) {
    const key = localStorage.key(i);
    if (key?.startsWith(prefix)) keys.push(key);
  }
  for (const key of keys) localStorage.removeItem(key);
}
