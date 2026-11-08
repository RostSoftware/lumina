import type { NostrEvent } from '@nostrify/nostrify';
import { isHex64 } from './nostr';

/** Parsed NIP-51 mute list (kind 10000). */
export interface MuteList {
  pubkeys: Set<string>;
  words: string[];
  hashtags: Set<string>;
  threads: Set<string>;
}

export const EMPTY_MUTES: MuteList = { pubkeys: new Set(), words: [], hashtags: new Set(), threads: new Set() };

/** Merge public tags and decrypted private tags into a mute list. */
export function parseMuteTags(tags: string[][]): MuteList {
  const list: MuteList = { pubkeys: new Set(), words: [], hashtags: new Set(), threads: new Set() };
  for (const [name, value] of tags) {
    if (typeof value !== 'string' || !value) continue;
    if (name === 'p' && isHex64(value)) list.pubkeys.add(value);
    else if (name === 'word') list.words.push(value.toLowerCase());
    else if (name === 't') list.hashtags.add(value.toLowerCase());
    else if (name === 'e' && isHex64(value)) list.threads.add(value);
  }
  return list;
}

/** Is this event hidden by the mute list? Checks author, words, hashtags and thread. */
export function isMuted(event: NostrEvent, mutes: MuteList): boolean {
  if (mutes.pubkeys.has(event.pubkey)) return true;
  if (mutes.threads.has(event.id)) return true;
  if (mutes.words.length > 0) {
    const text = event.content.toLowerCase();
    if (mutes.words.some((w) => text.includes(w))) return true;
  }
  if (mutes.hashtags.size > 0) {
    for (const [n, v] of event.tags) {
      if (n === 't' && typeof v === 'string' && mutes.hashtags.has(v.toLowerCase())) return true;
    }
  }
  return false;
}

export function parseMuteListPublic(event: NostrEvent | undefined): MuteList {
  return event ? parseMuteTags(event.tags) : EMPTY_MUTES;
}
