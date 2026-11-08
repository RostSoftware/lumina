import type { NostrEvent } from '@nostrify/nostrify';
import { getTag, isHex64, KIND_COMMENT, KIND_CONTACTS, KIND_GENERIC_REPOST, KIND_PICTURE, KIND_REACTION, KIND_REPOST, KIND_ZAP_RECEIPT } from './nostr';
import { parseZapReceipt } from './zapReceipt';

export type NotificationType = 'like' | 'comment' | 'reply' | 'repost' | 'zap' | 'mention' | 'tag' | 'follow';

export interface NotificationItem {
  type: NotificationType;
  event: NostrEvent;
  /** Who did it (zap sender for zaps). */
  actor: string;
  /** Post the notification is about (for thumbnails). */
  targetId?: string;
  sats?: number;
  /** Text preview (comment / zap comment). */
  text?: string;
  created_at: number;
}

export interface NotificationGroup {
  key: string;
  type: NotificationType;
  actors: string[];
  items: NotificationItem[];
  targetId?: string;
  totalSats: number;
  latest: number;
}

/** The last `e` tag (NIP-25 / NIP-18 target), or root `E` for comments. */
function targetOf(event: NostrEvent): string | undefined {
  const eTags = event.tags.filter(([n, v]) => n === 'e' && isHex64(v)).map(([, v]) => v);
  return eTags[eTags.length - 1];
}

/** Classify an event that `#p`-tags the user. Returns undefined to ignore it. */
export function classifyNotification(event: NostrEvent, me: string): NotificationItem | undefined {
  const base = { event, created_at: event.created_at };
  switch (event.kind) {
    case KIND_REACTION: {
      if (event.pubkey === me || event.content.trim() === '-') return undefined;
      const k = getTag(event, 'k');
      if (k && k !== String(KIND_PICTURE)) return undefined;
      return { ...base, type: 'like', actor: event.pubkey, targetId: targetOf(event), text: event.content.trim() === '+' ? undefined : event.content.trim() };
    }
    case KIND_REPOST:
    case KIND_GENERIC_REPOST: {
      if (event.pubkey === me) return undefined;
      const k = getTag(event, 'k');
      if (event.kind === KIND_GENERIC_REPOST && k && k !== String(KIND_PICTURE)) return undefined;
      return { ...base, type: 'repost', actor: event.pubkey, targetId: targetOf(event) };
    }
    case KIND_COMMENT: {
      if (event.pubkey === me) return undefined;
      const rootKind = getTag(event, 'K');
      const rootAuthor = getTag(event, 'P');
      const parentKind = getTag(event, 'k');
      const parentAuthor = event.tags.find(([n]) => n === 'p')?.[1];
      const rootId = getTag(event, 'E');
      if (rootKind && rootKind !== String(KIND_PICTURE)) return undefined;
      let type: NotificationType = 'mention';
      if (parentKind === String(KIND_COMMENT) && parentAuthor === me) type = 'reply';
      else if (rootAuthor === me) type = 'comment';
      return { ...base, type, actor: event.pubkey, targetId: rootId, text: event.content };
    }
    case KIND_ZAP_RECEIPT: {
      const zap = parseZapReceipt(event);
      if (!zap || zap.sender === me) return undefined;
      return { ...base, type: 'zap', actor: zap.sender ?? event.pubkey, targetId: zap.target, sats: zap.sats, text: zap.comment || undefined };
    }
    case KIND_CONTACTS:
      if (event.pubkey === me) return undefined;
      return { ...base, type: 'follow', actor: event.pubkey };
    case KIND_PICTURE:
      if (event.pubkey === me) return undefined;
      return { ...base, type: 'tag', actor: event.pubkey, targetId: event.id, text: event.content };
    default:
      return undefined;
  }
}

/** Group like Instagram: same type on the same post collapse into one row. */
export function groupNotifications(items: NotificationItem[]): NotificationGroup[] {
  const groups = new Map<string, NotificationGroup>();
  // Followers: keep only each person's newest contact list.
  const seenFollowers = new Set<string>();

  for (const item of [...items].sort((a, b) => b.created_at - a.created_at)) {
    if (item.type === 'follow') {
      if (seenFollowers.has(item.actor)) continue;
      seenFollowers.add(item.actor);
    }
    const day = Math.floor(item.created_at / 86400);
    const groupable = item.type === 'like' || item.type === 'repost' || item.type === 'zap' || item.type === 'follow';
    const key = groupable
      ? `${item.type}:${item.type === 'follow' ? day : item.targetId ?? ''}`
      : `${item.type}:${item.event.id}`;
    const group = groups.get(key);
    if (group) {
      if (!group.actors.includes(item.actor)) group.actors.push(item.actor);
      group.items.push(item);
      group.totalSats += item.sats ?? 0;
    } else {
      groups.set(key, {
        key,
        type: item.type,
        actors: [item.actor],
        items: [item],
        targetId: item.targetId,
        totalSats: item.sats ?? 0,
        latest: item.created_at,
      });
    }
  }
  return [...groups.values()].sort((a, b) => b.latest - a.latest);
}

export const NOTIFICATION_KINDS = [KIND_REACTION, KIND_REPOST, KIND_GENERIC_REPOST, KIND_COMMENT, KIND_ZAP_RECEIPT, KIND_CONTACTS, KIND_PICTURE];
