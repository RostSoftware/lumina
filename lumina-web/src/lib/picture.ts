import type { NostrEvent } from '@nostrify/nostrify';
import { getTag, getTagValues, isHex64, KIND_PICTURE } from './nostr';
import { sanitizeUrl } from './sanitize';

/** A single image inside a kind 20 picture post (one `imeta` tag). */
export interface PictureImage {
  url: string;
  mime: string;
  width?: number;
  height?: number;
  /** sha256 of the file — used to fall back to other Blossom servers. */
  hash?: string;
  blurhash?: string;
  alt?: string;
  fallbacks: string[];
}

/** A validated, parsed kind 20 picture post (NIP-68). */
export interface PicturePost {
  event: NostrEvent;
  title?: string;
  caption: string;
  images: PictureImage[];
  hashtags: string[];
  tagged: string[];
  location?: string;
  geohash?: string;
  contentWarning?: string;
}

const IMAGE_MIME = /^image\/(jpeg|jpg|png|gif|webp|avif|heic|heif|apng|svg\+xml|bmp)$/i;
const IMAGE_EXT = /\.(jpe?g|png|gif|webp|avif|heic|heif|apng|bmp)(\?.*)?$/i;

/** Parse a single `imeta` tag into a key → values map. */
export function parseImetaTag(tag: string[]): Map<string, string[]> {
  const map = new Map<string, string[]>();
  for (const entry of tag.slice(1)) {
    if (typeof entry !== 'string') continue;
    const space = entry.indexOf(' ');
    if (space <= 0) continue;
    const key = entry.slice(0, space);
    const value = entry.slice(space + 1).trim();
    if (!value) continue;
    const list = map.get(key) ?? [];
    list.push(value);
    map.set(key, list);
  }
  return map;
}

function parseImage(tag: string[]): PictureImage | undefined {
  const fields = parseImetaTag(tag);
  const url = sanitizeUrl(fields.get('url')?.[0]);
  if (!url) return undefined;

  let mime = fields.get('m')?.[0]?.toLowerCase() ?? '';
  if (!mime && IMAGE_EXT.test(new URL(url).pathname)) {
    // Tolerate missing `m` when the URL clearly points to an image.
    mime = 'image/*';
  }
  if (mime !== 'image/*' && !IMAGE_MIME.test(mime)) return undefined;

  let width: number | undefined;
  let height: number | undefined;
  const dim = fields.get('dim')?.[0];
  const dimMatch = dim?.match(/^(\d{1,5})x(\d{1,5})$/);
  if (dimMatch) {
    const w = Number(dimMatch[1]);
    const h = Number(dimMatch[2]);
    if (w > 0 && h > 0) {
      width = w;
      height = h;
    }
  }

  const hash = fields.get('x')?.[0]?.toLowerCase();
  const blurhash = fields.get('blurhash')?.[0];

  return {
    url,
    mime,
    width,
    height,
    hash: isHex64(hash) ? hash : undefined,
    blurhash: blurhash && /^[0-9A-Za-z#$%*+,-.:;=?@[\]^_{|}~]{6,}$/.test(blurhash) ? blurhash : undefined,
    alt: fields.get('alt')?.[0],
    fallbacks: (fields.get('fallback') ?? []).map((f) => sanitizeUrl(f)).filter((f): f is string => !!f),
  };
}

/**
 * Validate and parse a kind 20 event. Returns `undefined` for invalid events,
 * which callers drop silently: at least one `imeta` with an https `url` and an
 * image mime type is required.
 */
export function parsePicturePost(event: NostrEvent): PicturePost | undefined {
  if (event.kind !== KIND_PICTURE) return undefined;
  const images = event.tags
    .filter(([name]) => name === 'imeta')
    .map(parseImage)
    .filter((img): img is PictureImage => !!img);
  if (images.length === 0) return undefined;

  const cw = event.tags.find(([n]) => n === 'content-warning');

  return {
    event,
    title: getTag(event, 'title'),
    caption: event.content,
    images,
    hashtags: [...new Set(getTagValues(event, 't').map((t) => t.toLowerCase()))],
    tagged: [...new Set(getTagValues(event, 'p').filter(isHex64))],
    location: getTag(event, 'location'),
    geohash: getTag(event, 'g'),
    contentWarning: cw ? (cw[1] ?? '') : undefined,
  };
}

export function isValidPicture(event: NostrEvent): boolean {
  return parsePicturePost(event) !== undefined;
}

/** Aspect ratio (w/h) clamped like Instagram: between 4:5 portrait and 1.91:1 landscape. */
export function displayAspect(image: PictureImage | undefined): number {
  if (!image?.width || !image.height) return 1;
  const ratio = image.width / image.height;
  return Math.min(1.91, Math.max(0.8, ratio));
}

/** Build the list of URLs to try for an image: primary, explicit fallbacks, then Blossom servers by hash. */
export function candidateUrls(image: PictureImage, blossomServers: string[]): string[] {
  const urls = [image.url, ...image.fallbacks];
  if (image.hash) {
    let ext = '';
    try {
      const match = new URL(image.url).pathname.match(/\.([a-z0-9]{2,5})$/i);
      if (match) ext = `.${match[1]}`;
    } catch {
      // ignore
    }
    for (const server of blossomServers) {
      const base = sanitizeUrl(server);
      if (!base) continue;
      urls.push(`${base.replace(/\/+$/, '')}/${image.hash}${ext}`);
    }
  }
  return [...new Set(urls)];
}
