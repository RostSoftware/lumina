/**
 * Signer error classification.
 *
 * Remote signers (NIP-46 bunkers, Amber via nostrconnect) are asynchronous
 * and can reject or time out. Every publish path converts raw errors into one
 * of these states so the UI can show a clear message instead of failing
 * silently or freezing.
 */

export type SignerFailure = 'rejected' | 'unreachable' | 'unsupported' | 'unknown';

export class SignerError extends Error {
  constructor(public readonly reason: SignerFailure, message: string, options?: { cause?: unknown }) {
    super(message, options);
    this.name = 'SignerError';
  }
}

export function classifySignerError(error: unknown): SignerError {
  if (error instanceof SignerError) return error;
  const message = error instanceof Error ? error.message : String(error);
  const lower = message.toLowerCase();
  if (/reject|denied|declin|cancel|not allowed|unauthori[sz]ed|user refused/.test(lower)) {
    return new SignerError('rejected', 'The request was rejected by your signer.', { cause: error });
  }
  if (/timeout|timed out|abort|unreachable|network|websocket|connection/.test(lower)) {
    return new SignerError('unreachable', 'Your signer did not respond. Check that it is online and try again.', { cause: error });
  }
  if (/not support|nip44|nip04|undefined is not|is not a function/.test(lower)) {
    return new SignerError('unsupported', 'Your signer does not support this action.', { cause: error });
  }
  return new SignerError('unknown', message || 'Signing failed.', { cause: error });
}

/** Run a signer call with a timeout. */
export async function withTimeout<T>(promise: Promise<T>, ms: number, label = 'Signer'): Promise<T> {
  let timer: ReturnType<typeof setTimeout> | undefined;
  try {
    return await Promise.race([
      promise,
      new Promise<never>((_, reject) => {
        timer = setTimeout(() => reject(new Error(`${label} timed out`)), ms);
      }),
    ]);
  } finally {
    if (timer) clearTimeout(timer);
  }
}

/** User-facing title for a failure. */
export function signerFailureTitle(reason: SignerFailure): string {
  switch (reason) {
    case 'rejected':
      return 'Request rejected';
    case 'unreachable':
      return 'Signer unreachable';
    case 'unsupported':
      return 'Not supported by your signer';
    default:
      return 'Something went wrong';
  }
}
