import type { NostrEvent, NostrFilter, NostrMetadata } from '@nostrify/nostrify';
import { nip19 } from 'nostr-tools';

/** Picture post kind (NIP-68). */
export const KIND_PICTURE = 20;
export const KIND_METADATA = 0;
export const KIND_CONTACTS = 3;
export const KIND_LEGACY_DM = 4;
export const KIND_DELETE = 5;
export const KIND_REPOST = 6;
export const KIND_REACTION = 7;
export const KIND_SEAL = 13;
export const KIND_CHAT = 14;
export const KIND_GENERIC_REPOST = 16;
export const KIND_GIFT_WRAP = 1059;
export const KIND_COMMENT = 1111;
export const KIND_REPORT = 1984;
export const KIND_ZAP_REQUEST = 9734;
export const KIND_ZAP_RECEIPT = 9735;
export const KIND_MUTE_LIST = 10000;
export const KIND_RELAY_LIST = 10002;
export const KIND_BOOKMARKS = 10003;
export const KIND_DM_RELAYS = 10050;
export const KIND_BLOSSOM_SERVERS = 10063;

/** First value of the first tag with this name. */
export function getTag(event: Pick<NostrEvent, 'tags'>, name: string): string | undefined {
  return event.tags.find(([n]) => n === name)?.[1];
}

/** All values of tags with this name. */
export function getTagValues(event: Pick<NostrEvent, 'tags'>, name: string): string[] {
  return event.tags.filter(([n, v]) => n === name && typeof v === 'string').map(([, v]) => v);
}

/** Is this a 64-char lowercase hex string (pubkey / event id)? */
export function isHex64(value: unknown): value is string {
  return typeof value === 'string' && /^[0-9a-f]{64}$/.test(value);
}

export function npubOf(pubkey: string): string {
  try {
    return nip19.npubEncode(pubkey);
  } catch {
    return pubkey;
  }
}

/** Shortened npub for display: npub1abcd…wxyz */
export function shortNpub(pubkey: string): string {
  const npub = npubOf(pubkey);
  return `${npub.slice(0, 10)}…${npub.slice(-4)}`;
}

/** Best display name for a profile. */
export function displayName(metadata: NostrMetadata | undefined, pubkey: string): string {
  const name = metadata?.display_name?.trim() || metadata?.name?.trim();
  return name || shortNpub(pubkey);
}

/** Username shown in post headers (Instagram uses the handle). */
export function handleName(metadata: NostrMetadata | undefined, pubkey: string): string {
  const name = metadata?.name?.trim() || metadata?.display_name?.trim();
  return name || shortNpub(pubkey);
}

/** Link path for a profile. */
export function profilePath(pubkey: string): string {
  return `/${npubOf(pubkey)}`;
}

/** NIP-19 nevent for an event, with author + kind hints. */
export function neventOf(event: Pick<NostrEvent, 'id' | 'pubkey' | 'kind'>, relays: string[] = []): string {
  return nip19.neventEncode({ id: event.id, author: event.pubkey, kind: event.kind, relays: relays.slice(0, 2) });
}

/** Link path for a post. */
export function postPath(event: Pick<NostrEvent, 'id' | 'pubkey' | 'kind'>): string {
  return `/${neventOf(event)}`;
}

/** Public, shareable URL for a post. */
export function postShareUrl(event: Pick<NostrEvent, 'id' | 'pubkey' | 'kind'>): string {
  return `${window.location.origin}${postPath(event)}`;
}

/** Split a large `authors` list into several filters (relays cap filter sizes). */
export function chunkAuthors(authors: string[], base: Omit<NostrFilter, 'authors'>, size = 250): NostrFilter[] {
  const filters: NostrFilter[] = [];
  for (let i = 0; i < authors.length; i += size) {
    filters.push({ ...base, authors: authors.slice(i, i + size) });
  }
  return filters;
}

/** Deduplicate by id and sort newest first. */
export function dedupeSort(events: NostrEvent[]): NostrEvent[] {
  const map = new Map<string, NostrEvent>();
  for (const e of events) map.set(e.id, e);
  return [...map.values()].sort((a, b) => b.created_at - a.created_at);
}

/** Pick the newest replaceable event. */
export function newest(events: NostrEvent[]): NostrEvent | undefined {
  return events.reduce<NostrEvent | undefined>((acc, e) => (!acc || e.created_at > acc.created_at ? e : acc), undefined);
}

/** Pubkeys followed in a kind 3 event. */
export function followsOf(contactList: NostrEvent | undefined): string[] {
  if (!contactList) return [];
  return [...new Set(contactList.tags.filter(([n, v]) => n === 'p' && isHex64(v)).map(([, v]) => v))];
}

/** Extract hashtags (#tag) from text. Lowercased, deduped. */
export function extractHashtags(text: string): string[] {
  const tags = new Set<string>();
  for (const match of text.matchAll(/(?:^|\s)#([\p{L}\p{N}_]{1,64})/gu)) {
    tags.add(match[1].toLowerCase());
  }
  return [...tags];
}

/**
 * Decode a NIP-19 string (optionally prefixed with `nostr:`) into a pubkey or
 * event pointer. Never decodes nsec.
 */
export type DecodedPointer =
  | { type: 'profile'; pubkey: string; relays?: string[] }
  | { type: 'event'; id: string; author?: string; kind?: number; relays?: string[] }
  | { type: 'address'; kind: number; pubkey: string; identifier: string; relays?: string[] };

export function decodePointer(input: string): DecodedPointer | undefined {
  const value = input.trim().replace(/^nostr:/i, '');
  if (/^nsec1/i.test(value)) return undefined;
  try {
    const decoded = nip19.decode(value);
    switch (decoded.type) {
      case 'npub':
        return { type: 'profile', pubkey: decoded.data };
      case 'nprofile':
        return { type: 'profile', pubkey: decoded.data.pubkey, relays: decoded.data.relays };
      case 'note':
        return { type: 'event', id: decoded.data };
      case 'nevent':
        return {
          type: 'event',
          id: decoded.data.id,
          author: decoded.data.author,
          kind: decoded.data.kind,
          relays: decoded.data.relays,
        };
      case 'naddr':
        return {
          type: 'address',
          kind: decoded.data.kind,
          pubkey: decoded.data.pubkey,
          identifier: decoded.data.identifier,
          relays: decoded.data.relays,
        };
      default:
        return undefined;
    }
  } catch {
    return undefined;
  }
}

/** Parse a kind 10002 relay list. */
export function parseRelayList(event: NostrEvent | undefined): { url: string; read: boolean; write: boolean }[] {
  if (!event) return [];
  return event.tags
    .filter(([n, url]) => n === 'r' && typeof url === 'string' && /^wss?:\/\//.test(url))
    .map(([, url, marker]) => ({
      url,
      read: !marker || marker === 'read',
      write: !marker || marker === 'write',
    }));
}

/** Parse a kind 10050 DM relay list. */
export function parseDMRelays(event: NostrEvent | undefined): string[] {
  if (!event) return [];
  return event.tags.filter(([n, url]) => n === 'relay' && typeof url === 'string' && /^wss?:\/\//.test(url)).map(([, url]) => url);
}
