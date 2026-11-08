import { useNostr } from '@nostrify/react';
import { useMutation, useQueryClient } from '@tanstack/react-query';

import { useAppContext } from './useAppContext';
import { useCurrentUser } from './useCurrentUser';
import { signWithFeedback, toastError } from './useNostrPublish';
import { toast } from './useToast';
import type { RelayMetadata } from '@/contexts/AppContext';
import { KIND_BLOSSOM_SERVERS, KIND_DM_RELAYS, KIND_RELAY_LIST } from '@/lib/nostr';

/** Save the NIP-65 relay list locally and publish kind 10002 when logged in. */
export function useSaveRelays() {
  const { nostr } = useNostr();
  const { user } = useCurrentUser();
  const { updateConfig } = useAppContext();

  return useMutation({
    mutationFn: async (relays: RelayMetadata['relays']) => {
      const now = Math.floor(Date.now() / 1000);
      if (user) {
        const tags = relays
          .filter((r) => r.read || r.write)
          .map((r) => (r.read && r.write ? ['r', r.url] : ['r', r.url, r.read ? 'read' : 'write']));
        const event = await signWithFeedback(user, { kind: KIND_RELAY_LIST, content: '', tags });
        // Publish to the union of old and new write relays so every relay learns the change.
        await nostr.event(event, { signal: AbortSignal.timeout(8000) });
        const newWrite = relays.filter((r) => r.write).map((r) => r.url);
        await nostr.group(newWrite).event(event, { signal: AbortSignal.timeout(8000) }).catch(() => undefined);
      }
      updateConfig((c) => ({ ...c, relayMetadata: { relays, updatedAt: now } }));
    },
    onSuccess: () => toast({ title: 'Relays saved' }),
    onError: (e) => toastError(e, 'Could not save relays'),
  });
}

/** Save the BUD-03 Blossom server list and publish kind 10063. */
export function useSaveBlossomServers() {
  const { nostr } = useNostr();
  const { user } = useCurrentUser();
  const { updateConfig } = useAppContext();

  return useMutation({
    mutationFn: async ({ servers, useAppServers }: { servers: string[]; useAppServers: boolean }) => {
      const now = Math.floor(Date.now() / 1000);
      if (user) {
        const event = await signWithFeedback(user, {
          kind: KIND_BLOSSOM_SERVERS,
          content: '',
          tags: servers.map((s) => ['server', s]),
        });
        await nostr.event(event, { signal: AbortSignal.timeout(8000) });
      }
      updateConfig((c) => ({ ...c, blossomServerMetadata: { servers, updatedAt: now }, useAppBlossomServers: useAppServers }));
    },
    onSuccess: () => toast({ title: 'Media servers saved' }),
    onError: (e) => toastError(e, 'Could not save media servers'),
  });
}

/** Publish the NIP-17 DM inbox relay list (kind 10050). */
export function useSaveDMRelays() {
  const { nostr } = useNostr();
  const { user } = useCurrentUser();
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async (relays: string[]) => {
      if (!user) throw new Error('You need to log in first.');
      const event = await signWithFeedback(user, { kind: KIND_DM_RELAYS, content: '', tags: relays.map((r) => ['relay', r]) });
      await nostr.event(event, { signal: AbortSignal.timeout(8000) });
      if (relays.length) await nostr.group(relays).event(event, { signal: AbortSignal.timeout(8000) }).catch(() => undefined);
      return event;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['nostr', 'dm-relays'] });
      toast({ title: 'DM relays saved' });
    },
    onError: (e) => toastError(e, 'Could not save DM relays'),
  });
}
