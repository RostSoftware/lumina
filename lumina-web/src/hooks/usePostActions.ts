import { useNostr } from '@nostrify/react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import type { NostrEvent } from '@nostrify/nostrify';
import { nip19 } from 'nostr-tools';

import { useCurrentUser } from './useCurrentUser';
import { signWithFeedback, toastError } from './useNostrPublish';
import { toast } from './useToast';
import type { PostInteractions } from './usePostStats';
import {
  KIND_COMMENT,
  KIND_DELETE,
  KIND_GENERIC_REPOST,
  KIND_REACTION,
  KIND_REPORT,
} from '@/lib/nostr';

function statsKey(id: string) {
  return ['nostr', 'post-stats', id];
}

const EMPTY: PostInteractions = { reactions: [], reposts: [], comments: [], zaps: [] };

/** Placeholder event used for optimistic updates until the signed one returns. */
function optimisticEvent(pubkey: string, kind: number, content: string, tags: string[][]): NostrEvent {
  return { id: `optimistic-${Math.random().toString(36).slice(2)}`, pubkey, kind, content, tags, created_at: Math.floor(Date.now() / 1000), sig: '' };
}

/** Like (NIP-25 "+") or unlike (NIP-09 deletion of the reaction). Optimistic. */
export function useLike() {
  const { nostr } = useNostr();
  const { user } = useCurrentUser();
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async ({ post, existing }: { post: NostrEvent; existing?: NostrEvent }) => {
      if (!user) throw new Error('You need to log in first.');
      if (existing && !existing.id.startsWith('optimistic-')) {
        const del = await signWithFeedback(user, {
          kind: KIND_DELETE,
          content: '',
          tags: [['e', existing.id], ['k', String(KIND_REACTION)]],
        });
        await nostr.event(del, { signal: AbortSignal.timeout(8000) });
        return { removed: existing.id };
      }
      const reaction = await signWithFeedback(user, {
        kind: KIND_REACTION,
        content: '+',
        tags: [['e', post.id], ['p', post.pubkey], ['k', String(post.kind)]],
      });
      await nostr.event(reaction, { signal: AbortSignal.timeout(8000) });
      return { added: reaction };
    },
    onMutate: async ({ post, existing }) => {
      if (!user) return;
      await queryClient.cancelQueries({ queryKey: statsKey(post.id) });
      const previous = queryClient.getQueryData<PostInteractions>(statsKey(post.id));
      const base = previous ?? EMPTY;
      const reactions = existing
        ? base.reactions.filter((r) => r.pubkey !== user.pubkey)
        : [...base.reactions, optimisticEvent(user.pubkey, KIND_REACTION, '+', [['e', post.id]])];
      queryClient.setQueryData(statsKey(post.id), { ...base, reactions });
      return { previous };
    },
    onError: (error, { post }, context) => {
      queryClient.setQueryData(statsKey(post.id), context?.previous ?? EMPTY);
      toastError(error, 'Could not update like');
    },
    onSuccess: (result, { post }) => {
      if (!user || !result.added) return;
      const current = queryClient.getQueryData<PostInteractions>(statsKey(post.id)) ?? EMPTY;
      queryClient.setQueryData(statsKey(post.id), {
        ...current,
        reactions: [...current.reactions.filter((r) => !r.id.startsWith('optimistic-')), result.added],
      });
    },
  });
}

/** NIP-18 generic repost (kind 16, k=20). Optimistic. */
export function useRepost() {
  const { nostr } = useNostr();
  const { user } = useCurrentUser();
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async ({ post, existing }: { post: NostrEvent; existing?: NostrEvent }) => {
      if (!user) throw new Error('You need to log in first.');
      if (existing && !existing.id.startsWith('optimistic-')) {
        const del = await signWithFeedback(user, {
          kind: KIND_DELETE,
          content: '',
          tags: [['e', existing.id], ['k', String(KIND_GENERIC_REPOST)]],
        });
        await nostr.event(del, { signal: AbortSignal.timeout(8000) });
        return { removed: existing.id };
      }
      const repost = await signWithFeedback(user, {
        kind: KIND_GENERIC_REPOST,
        content: JSON.stringify(post),
        tags: [['e', post.id, ''], ['p', post.pubkey], ['k', String(post.kind)]],
      });
      await nostr.event(repost, { signal: AbortSignal.timeout(8000) });
      return { added: repost };
    },
    onMutate: async ({ post, existing }) => {
      if (!user) return;
      await queryClient.cancelQueries({ queryKey: statsKey(post.id) });
      const previous = queryClient.getQueryData<PostInteractions>(statsKey(post.id));
      const base = previous ?? EMPTY;
      const reposts = existing
        ? base.reposts.filter((r) => r.pubkey !== user.pubkey)
        : [...base.reposts, optimisticEvent(user.pubkey, KIND_GENERIC_REPOST, '', [['e', post.id]])];
      queryClient.setQueryData(statsKey(post.id), { ...base, reposts });
      return { previous };
    },
    onError: (error, { post }, context) => {
      queryClient.setQueryData(statsKey(post.id), context?.previous ?? EMPTY);
      toastError(error, 'Could not repost');
    },
    onSuccess: (result, { post }) => {
      if (result.added) {
        const current = queryClient.getQueryData<PostInteractions>(statsKey(post.id)) ?? EMPTY;
        queryClient.setQueryData(statsKey(post.id), {
          ...current,
          reposts: [...current.reposts.filter((r) => !r.id.startsWith('optimistic-')), result.added],
        });
        toast({ title: 'Reposted' });
      }
      queryClient.invalidateQueries({ queryKey: ['nostr', 'profile-posts'] });
    },
  });
}

/** NIP-22 comment on a post, or reply to another comment. */
export function useComment() {
  const { nostr } = useNostr();
  const { user } = useCurrentUser();
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async ({ root, parent, content }: { root: NostrEvent; parent?: NostrEvent; content: string }) => {
      if (!user) throw new Error('You need to log in first.');
      const target = parent ?? root;
      const tags: string[][] = [
        ['E', root.id, '', root.pubkey],
        ['K', String(root.kind)],
        ['P', root.pubkey],
        ['e', target.id, '', target.pubkey],
        ['k', String(target.kind)],
        ['p', target.pubkey],
      ];
      // Tag mentioned npubs so they get notified.
      for (const match of content.matchAll(/nostr:(npub1[0-9a-z]+)/g)) {
        try {
          const decoded = nip19.decode(match[1]);
          if (decoded.type === 'npub' && !tags.some(([n, v]) => n === 'p' && v === decoded.data)) tags.push(['p', decoded.data]);
        } catch {
          // ignore malformed mention
        }
      }
      const event = await signWithFeedback(user, { kind: KIND_COMMENT, content, tags });
      await nostr.event(event, { signal: AbortSignal.timeout(8000) });
      return event;
    },
    onSuccess: (event, { root }) => {
      const current = queryClient.getQueryData<PostInteractions>(statsKey(root.id)) ?? EMPTY;
      queryClient.setQueryData(statsKey(root.id), { ...current, comments: [...current.comments, event] });
    },
    onError: (error) => toastError(error, 'Comment failed'),
  });
}

/** NIP-09 deletion of the user's own event. */
export function useDeleteEvent() {
  const { nostr } = useNostr();
  const { user } = useCurrentUser();
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async (event: NostrEvent) => {
      if (!user || user.pubkey !== event.pubkey) throw new Error('You can only delete your own posts.');
      const del = await signWithFeedback(user, {
        kind: KIND_DELETE,
        content: '',
        tags: [['e', event.id], ['k', String(event.kind)]],
      });
      await nostr.event(del, { signal: AbortSignal.timeout(8000) });
      return del;
    },
    onSuccess: (_d, event) => {
      queryClient.setQueryData(['lumina', 'deleted'], (prev: string[] | undefined) => [...(prev ?? []), event.id]);
      queryClient.invalidateQueries({ queryKey: ['nostr', 'feed'] });
      queryClient.invalidateQueries({ queryKey: ['nostr', 'profile-posts'] });
      toast({ title: 'Post deleted' });
    },
    onError: (error) => toastError(error, 'Delete failed'),
  });
}

export type ReportType = 'nudity' | 'malware' | 'profanity' | 'illegal' | 'spam' | 'impersonation' | 'other';

/** NIP-56 report (kind 1984). */
export function useReport() {
  const { nostr } = useNostr();
  const { user } = useCurrentUser();

  return useMutation({
    mutationFn: async ({ event, pubkey, type, reason }: { event?: NostrEvent; pubkey: string; type: ReportType; reason: string }) => {
      if (!user) throw new Error('You need to log in first.');
      const tags: string[][] = [['p', pubkey, type]];
      if (event) tags.unshift(['e', event.id, type]);
      const report = await signWithFeedback(user, { kind: KIND_REPORT, content: reason, tags });
      await nostr.event(report, { signal: AbortSignal.timeout(8000) });
      return report;
    },
    onSuccess: () => toast({ title: 'Report sent', description: 'Thanks for helping keep LUMINA safe.' }),
    onError: (error) => toastError(error, 'Report failed'),
  });
}
