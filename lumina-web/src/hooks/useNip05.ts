import { useQuery } from '@tanstack/react-query';

/** Verify a NIP-05 identifier against a pubkey. */
export function useNip05(nip05: string | undefined, pubkey: string | undefined) {
  return useQuery({
    queryKey: ['nip05', nip05 ?? '', pubkey ?? ''],
    enabled: !!nip05 && !!pubkey && nip05.includes('@'),
    staleTime: 60 * 60_000,
    retry: false,
    queryFn: async ({ signal }) => {
      const [name, domain] = nip05!.trim().toLowerCase().split('@');
      if (!name || !domain || !/^[a-z0-9.-]+\.[a-z]{2,}$/.test(domain)) return false;
      const res = await fetch(`https://${domain}/.well-known/nostr.json?name=${encodeURIComponent(name)}`, {
        signal: AbortSignal.any([signal, AbortSignal.timeout(5000)]),
      });
      if (!res.ok) return false;
      const body = (await res.json()) as { names?: Record<string, string> };
      return body.names?.[name] === pubkey;
    },
  });
}

/** Resolve name@domain → pubkey. */
export async function resolveNip05(identifier: string): Promise<string | undefined> {
  const value = identifier.trim().toLowerCase().replace(/^@/, '');
  const [name, domain] = value.includes('@') ? value.split('@') : ['_', value];
  if (!domain || !/^[a-z0-9.-]+\.[a-z]{2,}$/.test(domain)) return undefined;
  const res = await fetch(`https://${domain}/.well-known/nostr.json?name=${encodeURIComponent(name)}`, { signal: AbortSignal.timeout(5000) });
  if (!res.ok) return undefined;
  const body = (await res.json()) as { names?: Record<string, string> };
  const pubkey = body.names?.[name];
  return typeof pubkey === 'string' && /^[0-9a-f]{64}$/.test(pubkey) ? pubkey : undefined;
}
