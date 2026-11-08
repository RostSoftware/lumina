import type { NostrSigner } from '@nostrify/nostrify';
import { classifySignerError, withTimeout } from './signer';

/** BUD-02 blob descriptor returned by a Blossom server. */
export interface BlobDescriptor {
  url: string;
  sha256: string;
  size: number;
  type?: string;
}

function base64Utf8(value: string): string {
  const bytes = new TextEncoder().encode(value);
  let binary = '';
  for (const b of bytes) binary += String.fromCharCode(b);
  return btoa(binary);
}

/** Sign a BUD-01 kind 24242 upload authorization for a blob hash. */
export async function signUploadAuth(signer: NostrSigner, hash: string): Promise<string> {
  const now = Math.floor(Date.now() / 1000);
  try {
    const event = await withTimeout(
      signer.signEvent({
        kind: 24242,
        content: 'Upload picture',
        created_at: now,
        tags: [
          ['t', 'upload'],
          ['x', hash],
          ['expiration', String(now + 600)],
        ],
      }),
      90_000,
    );
    return `Nostr ${base64Utf8(JSON.stringify(event))}`;
  } catch (error) {
    throw classifySignerError(error);
  }
}

/** PUT a blob to one Blossom server, reporting progress 0..1. */
function putBlob(
  server: string,
  blob: Blob,
  mime: string,
  hash: string,
  auth: string,
  onProgress: (fraction: number) => void,
  signal?: AbortSignal,
): Promise<BlobDescriptor> {
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    const url = new URL('upload', server.endsWith('/') ? server : `${server}/`).toString();
    xhr.open('PUT', url);
    xhr.setRequestHeader('Authorization', auth);
    xhr.setRequestHeader('Content-Type', mime);
    xhr.setRequestHeader('X-SHA-256', hash);
    xhr.upload.onprogress = (e) => {
      if (e.lengthComputable) onProgress(e.loaded / e.total);
    };
    xhr.onload = () => {
      if (xhr.status >= 200 && xhr.status < 300) {
        try {
          const body = JSON.parse(xhr.responseText) as Partial<BlobDescriptor>;
          if (typeof body.url !== 'string' || !body.url.startsWith('https://')) {
            reject(new Error(`${new URL(server).host} returned an invalid URL`));
            return;
          }
          resolve({
            url: body.url,
            sha256: typeof body.sha256 === 'string' ? body.sha256 : hash,
            size: typeof body.size === 'number' ? body.size : blob.size,
            type: typeof body.type === 'string' ? body.type : mime,
          });
        } catch {
          reject(new Error(`${new URL(server).host} returned an invalid response`));
        }
      } else {
        const reason = xhr.getResponseHeader('X-Reason');
        reject(new Error(`${new URL(server).host}: ${reason || `HTTP ${xhr.status}`}`));
      }
    };
    xhr.onerror = () => reject(new Error(`${new URL(server).host} is unreachable`));
    xhr.onabort = () => reject(new DOMException('Upload aborted', 'AbortError'));
    signal?.addEventListener('abort', () => xhr.abort(), { once: true });
    xhr.send(blob);
  });
}

/**
 * Upload a blob to the first Blossom server that accepts it, trying servers in
 * order. Returns the descriptor plus the URLs of other servers that also hold
 * the blob (mirrors, used as `fallback` in imeta).
 */
export async function uploadToBlossom(opts: {
  servers: string[];
  signer: NostrSigner;
  blob: Blob;
  mime: string;
  hash: string;
  onProgress?: (fraction: number) => void;
  signal?: AbortSignal;
}): Promise<{ descriptor: BlobDescriptor; server: string }> {
  const { servers, signer, blob, mime, hash, onProgress, signal } = opts;
  if (servers.length === 0) throw new Error('No media servers configured. Add one in Settings → Media servers.');

  const auth = await signUploadAuth(signer, hash);
  const errors: string[] = [];
  for (const server of servers) {
    if (signal?.aborted) throw new DOMException('Upload aborted', 'AbortError');
    try {
      onProgress?.(0);
      const descriptor = await putBlob(server, blob, mime, hash, auth, (f) => onProgress?.(f), signal);
      onProgress?.(1);
      return { descriptor, server };
    } catch (error) {
      if (error instanceof DOMException && error.name === 'AbortError') throw error;
      errors.push(error instanceof Error ? error.message : String(error));
    }
  }
  throw new Error(`Upload failed on all servers. ${errors.join(' · ')}`);
}

/** Check that a Blossom server responds. Returns latency in ms. */
export async function testBlossomServer(server: string): Promise<number> {
  const start = performance.now();
  const res = await fetch(server, { method: 'GET', signal: AbortSignal.timeout(8000) });
  if (res.status >= 500) throw new Error(`HTTP ${res.status}`);
  return Math.round(performance.now() - start);
}
