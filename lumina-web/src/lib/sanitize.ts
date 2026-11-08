/**
 * Input sanitizers for untrusted, event-sourced strings.
 *
 * Every URL that comes from a Nostr event (image, avatar, banner, website,
 * link) MUST pass through {@link sanitizeUrl} before it is used as `src`,
 * `href` or inside CSS. Only `https:` is allowed.
 */

/** Returns the canonical href for a valid https:// URL, otherwise `undefined`. */
export function sanitizeUrl(value: string | undefined | null): string | undefined {
  if (!value || typeof value !== 'string') return undefined;
  try {
    const url = new URL(value.trim());
    return url.protocol === 'https:' ? url.href : undefined;
  } catch {
    return undefined;
  }
}

/** Allowlist of characters that are safe inside a CSS string. */
export function sanitizeCssString(value: string): string {
  return value.replace(/[^\p{L}\p{N} _\-'.]/gu, '');
}

/** Lenient website parser: accepts "example.com" and upgrades it to https. */
export function sanitizeWebsite(value: string | undefined | null): string | undefined {
  if (!value) return undefined;
  const trimmed = value.trim();
  if (!trimmed) return undefined;
  if (/^[a-z][a-z0-9+.-]*:/i.test(trimmed)) return sanitizeUrl(trimmed);
  return sanitizeUrl(`https://${trimmed}`);
}

/** Validate a relay URL (wss:// or ws:// for localhost only). Returns the normalized URL. */
export function normalizeRelayUrl(value: string): string | undefined {
  let input = value.trim();
  if (!input) return undefined;
  if (!/^wss?:\/\//i.test(input)) input = `wss://${input}`;
  try {
    const url = new URL(input);
    if (url.protocol !== 'wss:' && !(url.protocol === 'ws:' && /^(localhost|127\.0\.0\.1)$/.test(url.hostname))) {
      return undefined;
    }
    if (!url.hostname.includes('.') && url.hostname !== 'localhost') return undefined;
    return url.href;
  } catch {
    return undefined;
  }
}

/** Validate a Blossom server URL (https only). Ensures a trailing slash. */
export function normalizeServerUrl(value: string): string | undefined {
  let input = value.trim();
  if (!input) return undefined;
  if (!/^https?:\/\//i.test(input)) input = `https://${input}`;
  const url = sanitizeUrl(input);
  if (!url) return undefined;
  return url.endsWith('/') ? url : `${url}/`;
}
