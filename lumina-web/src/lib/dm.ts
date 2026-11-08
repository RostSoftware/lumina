/**
 * NIP-17 private direct messages (NIP-59 gift wraps) using the user's signer.
 *
 * Structure: rumor (unsigned kind 14) → seal (kind 13, signed by the sender,
 * content NIP-44 encrypted to the recipient) → gift wrap (kind 1059, signed by
 * a throwaway key, content NIP-44 encrypted to the recipient).
 *
 * The sender's private key never touches this code: sealing and unwrapping go
 * through `signer.nip44`. Only the ephemeral wrap key is generated locally.
 */
import type { NostrEvent, NostrSigner } from '@nostrify/nostrify';
import { finalizeEvent, generateSecretKey, getEventHash, nip44, verifyEvent } from 'nostr-tools';
import { isHex64, KIND_CHAT, KIND_GIFT_WRAP, KIND_LEGACY_DM, KIND_SEAL } from './nostr';
import { classifySignerError, SignerError, withTimeout } from './signer';

/** A decrypted message, from either NIP-17 or legacy NIP-04. */
export interface DirectMessage {
  /** Rumor id (NIP-17) or event id (NIP-04). */
  id: string;
  /** Gift wrap / event id this message came from (for dedupe). */
  sourceId: string;
  pubkey: string;
  /** Conversation partner. */
  peer: string;
  content: string;
  created_at: number;
  protocol: 'nip17' | 'nip04';
}

const TWO_DAYS = 2 * 24 * 3600;

function randomPastTimestamp(): number {
  return Math.floor(Date.now() / 1000) - Math.floor(Math.random() * TWO_DAYS);
}

function requireNip44(signer: NostrSigner) {
  if (!signer.nip44) {
    throw new SignerError('unsupported', 'Your signer does not support NIP-44 encryption, which private messages require.');
  }
  return signer.nip44;
}

/** Build the unsigned kind 14 rumor. */
export function createRumor(senderPubkey: string, recipient: string, content: string): NostrEvent {
  const rumor = {
    kind: KIND_CHAT,
    pubkey: senderPubkey,
    created_at: Math.floor(Date.now() / 1000),
    tags: [['p', recipient]],
    content,
  };
  return { ...rumor, id: getEventHash(rumor), sig: '' };
}

/**
 * Create a gift wrap of `rumor` addressed to `recipient`. Call once per
 * recipient (the peer AND yourself, so you can read your own sent messages).
 */
export async function wrapRumor(signer: NostrSigner, rumor: NostrEvent, recipient: string): Promise<NostrEvent> {
  const nip44Signer = requireNip44(signer);
  try {
    const { sig: _sig, ...unsignedRumor } = rumor;
    const sealContent = await withTimeout(nip44Signer.encrypt(recipient, JSON.stringify(unsignedRumor)), 90_000);
    const seal = await withTimeout(
      signer.signEvent({ kind: KIND_SEAL, content: sealContent, created_at: randomPastTimestamp(), tags: [] }),
      90_000,
    );

    const wrapKey = generateSecretKey();
    const conversationKey = nip44.getConversationKey(wrapKey, recipient);
    const wrapContent = nip44.encrypt(JSON.stringify(seal), conversationKey);
    return finalizeEvent(
      {
        kind: KIND_GIFT_WRAP,
        content: wrapContent,
        created_at: randomPastTimestamp(),
        tags: [['p', recipient]],
      },
      wrapKey,
    ) as NostrEvent;
  } catch (error) {
    throw classifySignerError(error);
  }
}

/** Unwrap a kind 1059 gift wrap addressed to `me`. Returns `undefined` if invalid. */
export async function unwrapGiftWrap(signer: NostrSigner, wrap: NostrEvent, me: string): Promise<DirectMessage | undefined> {
  if (wrap.kind !== KIND_GIFT_WRAP || !signer.nip44) return undefined;
  const sealJson = await signer.nip44.decrypt(wrap.pubkey, wrap.content);
  const seal = JSON.parse(sealJson) as NostrEvent;
  if (seal.kind !== KIND_SEAL || !isHex64(seal.pubkey) || !verifyEvent(seal)) return undefined;

  const rumorJson = await signer.nip44.decrypt(seal.pubkey, seal.content);
  const rumor = JSON.parse(rumorJson) as Omit<NostrEvent, 'sig'>;
  // The seal author must be the rumor author, otherwise the message is forged.
  if (rumor.pubkey !== seal.pubkey || rumor.kind !== KIND_CHAT || typeof rumor.content !== 'string') return undefined;
  if (!Array.isArray(rumor.tags) || typeof rumor.created_at !== 'number') return undefined;

  const recipients = rumor.tags.filter(([n, v]) => n === 'p' && isHex64(v)).map(([, v]) => v);
  // Only 1:1 conversations are supported in the UI.
  const peer = rumor.pubkey === me ? recipients.find((p) => p !== me) ?? me : rumor.pubkey;

  return {
    id: rumor.id ?? getEventHash(rumor),
    sourceId: wrap.id,
    pubkey: rumor.pubkey,
    peer,
    content: rumor.content,
    created_at: rumor.created_at,
    protocol: 'nip17',
  };
}

/** Decrypt a legacy NIP-04 kind 4 DM. */
export async function decryptLegacyDM(signer: NostrSigner, event: NostrEvent, me: string): Promise<DirectMessage | undefined> {
  if (event.kind !== KIND_LEGACY_DM || !signer.nip04) return undefined;
  const recipient = event.tags.find(([n]) => n === 'p')?.[1];
  if (!isHex64(recipient)) return undefined;
  const peer = event.pubkey === me ? recipient : event.pubkey;
  const content = await signer.nip04.decrypt(peer, event.content);
  return {
    id: event.id,
    sourceId: event.id,
    pubkey: event.pubkey,
    peer,
    content,
    created_at: event.created_at,
    protocol: 'nip04',
  };
}
