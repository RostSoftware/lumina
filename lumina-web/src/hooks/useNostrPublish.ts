import { useNostr } from '@nostrify/react';
import { useMutation, type UseMutationResult } from '@tanstack/react-query';
import type { NostrEvent } from '@nostrify/nostrify';
import type { NUser } from '@nostrify/react/login';

import { useCurrentUser } from './useCurrentUser';
import { toast } from './useToast';
import { classifySignerError, SignerError, signerFailureTitle, withTimeout } from '@/lib/signer';

export type EventTemplate = Pick<NostrEvent, 'kind' | 'content'> &
  Partial<Pick<NostrEvent, 'tags' | 'created_at'>>;

export interface PublishOptions {
  /** Extra relays to publish to in addition to the user's write relays. */
  relays?: string[];
  /** Don't show error toasts (the caller handles them). */
  silent?: boolean;
}

/**
 * Sign an event with the user's signer, with feedback for remote signers:
 * a "waiting for approval" toast appears if signing takes longer than a
 * moment, and failures are classified (rejected / unreachable / unsupported).
 */
export async function signWithFeedback(user: NUser, template: EventTemplate): Promise<NostrEvent> {
  const tags = [...(template.tags ?? [])];
  const { hostname, protocol } = location;
  const isLocal = hostname === 'localhost' || hostname.endsWith('.localhost') || /^[\d.]+$/.test(hostname) || hostname.startsWith('[');
  if (protocol === 'https:' && !isLocal && !tags.some(([n]) => n === 'client')) {
    tags.push(['client', 'LUMINA']);
  }

  let pending: ReturnType<typeof toast> | undefined;
  const timer = setTimeout(() => {
    if (user.method !== 'nsec') {
      pending = toast({
        title: 'Waiting for your signer…',
        description: 'Approve the request in your signer app or extension.',
        duration: 120_000,
      });
    }
  }, 800);

  try {
    return await withTimeout(
      user.signer.signEvent({
        kind: template.kind,
        content: template.content ?? '',
        tags,
        created_at: template.created_at ?? Math.floor(Date.now() / 1000),
      }),
      user.method === 'nsec' ? 10_000 : 120_000,
    );
  } catch (error) {
    throw classifySignerError(error);
  } finally {
    clearTimeout(timer);
    pending?.dismiss();
  }
}

/** Show a toast for a failed signer/publish action. */
export function toastError(error: unknown, fallbackTitle = 'Something went wrong') {
  if (error instanceof SignerError) {
    toast({ title: signerFailureTitle(error.reason), description: error.message, variant: 'destructive' });
  } else {
    toast({
      title: fallbackTitle,
      description: error instanceof Error ? error.message : String(error),
      variant: 'destructive',
    });
  }
}

export function useNostrPublish(): UseMutationResult<NostrEvent, Error, EventTemplate & { options?: PublishOptions }> {
  const { nostr } = useNostr();
  const { user } = useCurrentUser();

  return useMutation({
    mutationFn: async ({ options, ...template }) => {
      if (!user) throw new Error('You need to log in first.');
      const event = await signWithFeedback(user, template);
      try {
        await nostr.event(event, { signal: AbortSignal.timeout(8000) });
        if (options?.relays?.length) {
          await nostr.group(options.relays).event(event, { signal: AbortSignal.timeout(8000) }).catch(() => undefined);
        }
      } catch (error) {
        throw new Error(`Could not reach your relays: ${error instanceof Error ? error.message : String(error)}`, { cause: error });
      }
      return event;
    },
    onError: (error, variables) => {
      if (!variables.options?.silent) toastError(error, 'Publish failed');
    },
  });
}
