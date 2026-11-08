import type { NostrEvent } from '@nostrify/nostrify';
import { decodeBolt11 } from './bolt11';
import { isHex64 } from './nostr';

/** Preset zap amounts (sats) shown in the zap sheet and wallet settings. */
export const ZAP_PRESETS = [21, 100, 500, 1000, 5000, 10000];

export interface ZapInfo {
  receipt: NostrEvent;
  /** Pubkey of the person who sent the zap (from the embedded zap request). */
  sender?: string;
  sats: number;
  comment: string;
  /** Event id the zap targets. */
  target?: string;
  created_at: number;
}

/** Parse a NIP-57 kind 9735 zap receipt. */
export function parseZapReceipt(receipt: NostrEvent): ZapInfo | undefined {
  if (receipt.kind !== 9735) return undefined;
  let sender: string | undefined;
  let comment = '';
  let requestAmountMsat: number | undefined;

  const description = receipt.tags.find(([n]) => n === 'description')?.[1];
  if (description) {
    try {
      const request = JSON.parse(description) as Partial<NostrEvent>;
      if (isHex64(request.pubkey)) sender = request.pubkey;
      if (typeof request.content === 'string') comment = request.content;
      const amount = request.tags?.find(([n]) => n === 'amount')?.[1];
      if (amount && /^\d+$/.test(amount)) requestAmountMsat = Number(amount);
    } catch {
      // malformed description
    }
  }
  const senderTag = receipt.tags.find(([n]) => n === 'P')?.[1];
  if (!sender && isHex64(senderTag)) sender = senderTag;

  let msat: number | undefined;
  const bolt11 = receipt.tags.find(([n]) => n === 'bolt11')?.[1];
  if (bolt11) {
    try {
      msat = decodeBolt11(bolt11).amountMsat ?? undefined;
    } catch {
      // ignore invalid invoice
    }
  }
  msat ??= requestAmountMsat;
  if (!msat) return undefined;

  return {
    receipt,
    sender,
    sats: Math.floor(msat / 1000),
    comment,
    target: receipt.tags.find(([n]) => n === 'e')?.[1],
    created_at: receipt.created_at,
  };
}
