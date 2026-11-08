import { encode as encodeBlurhash } from 'blurhash';
import { sha256 } from '@noble/hashes/sha2.js';
import { bytesToHex } from '@noble/hashes/utils.js';

/** Crop rectangle in source-image pixel coordinates. */
export interface CropRect {
  x: number;
  y: number;
  width: number;
  height: number;
}

/** Result of preparing an image for upload. */
export interface ProcessedImage {
  blob: Blob;
  mime: string;
  width: number;
  height: number;
  blurhash: string;
  sha256: string;
}

/** Longest edge of uploaded images. */
const MAX_EDGE = 2048;

/** Decode a file into an ImageBitmap, applying EXIF orientation. */
export async function loadBitmap(file: Blob): Promise<ImageBitmap> {
  return createImageBitmap(file, { imageOrientation: 'from-image' });
}

/** Natural size of an image file (after EXIF orientation). */
export async function imageSize(file: Blob): Promise<{ width: number; height: number }> {
  const bitmap = await loadBitmap(file);
  const size = { width: bitmap.width, height: bitmap.height };
  bitmap.close();
  return size;
}

function canvasToBlob(canvas: HTMLCanvasElement, mime: string, quality: number): Promise<Blob> {
  return new Promise((resolve, reject) => {
    canvas.toBlob((blob) => (blob ? resolve(blob) : reject(new Error('Could not encode image'))), mime, quality);
  });
}

/**
 * Re-encode an image through a canvas. This strips ALL metadata (EXIF, GPS,
 * XMP, ICC comments) because only pixels survive the round trip. It also
 * applies the crop and caps the size at {@link MAX_EDGE}.
 */
export async function processImage(file: File, crop?: CropRect): Promise<ProcessedImage> {
  const bitmap = await loadBitmap(file);
  try {
    const src: CropRect = crop ?? { x: 0, y: 0, width: bitmap.width, height: bitmap.height };
    const scale = Math.min(1, MAX_EDGE / Math.max(src.width, src.height));
    const width = Math.max(1, Math.round(src.width * scale));
    const height = Math.max(1, Math.round(src.height * scale));

    const canvas = document.createElement('canvas');
    canvas.width = width;
    canvas.height = height;
    const ctx = canvas.getContext('2d');
    if (!ctx) throw new Error('Canvas is not supported');
    ctx.imageSmoothingQuality = 'high';
    ctx.drawImage(bitmap, src.x, src.y, src.width, src.height, 0, 0, width, height);

    // Keep PNG for images that may contain transparency, JPEG otherwise.
    const mime = file.type === 'image/png' || file.type === 'image/gif' || file.type === 'image/webp' ? 'image/webp' : 'image/jpeg';
    let blob = await canvasToBlob(canvas, mime, 0.9);
    let finalMime = mime;
    if (blob.type !== mime) {
      // Browser without WebP encoding support falls back to PNG.
      finalMime = blob.type || 'image/png';
    }
    if (!finalMime.startsWith('image/')) {
      blob = await canvasToBlob(canvas, 'image/jpeg', 0.9);
      finalMime = 'image/jpeg';
    }

    const blurhash = computeBlurhash(canvas);
    const hash = bytesToHex(sha256(new Uint8Array(await blob.arrayBuffer())));

    return { blob, mime: finalMime, width, height, blurhash, sha256: hash };
  } finally {
    bitmap.close();
  }
}

/** Compute a blurhash from a small downscaled copy of the canvas. */
function computeBlurhash(source: HTMLCanvasElement): string {
  const w = 32;
  const h = Math.max(1, Math.round((source.height / source.width) * w));
  const small = document.createElement('canvas');
  small.width = w;
  small.height = h;
  const ctx = small.getContext('2d');
  if (!ctx) return '';
  ctx.drawImage(source, 0, 0, w, h);
  const data = ctx.getImageData(0, 0, w, h);
  const xComp = w >= h ? 4 : 3;
  const yComp = w >= h ? 3 : 4;
  return encodeBlurhash(data.data, w, h, xComp, yComp);
}

/** sha256 hex of a blob. */
export async function sha256Hex(blob: Blob): Promise<string> {
  return bytesToHex(sha256(new Uint8Array(await blob.arrayBuffer())));
}

/** Compute a centered crop rect for an aspect ratio, with zoom and pan (−1..1). */
export function computeCrop(
  width: number,
  height: number,
  aspect: number | null,
  zoom: number,
  panX: number,
  panY: number,
): CropRect {
  const targetAspect = aspect ?? width / height;
  let cw = width;
  let ch = width / targetAspect;
  if (ch > height) {
    ch = height;
    cw = height * targetAspect;
  }
  cw /= zoom;
  ch /= zoom;
  const maxX = width - cw;
  const maxY = height - ch;
  const x = (maxX / 2) * (1 + Math.max(-1, Math.min(1, panX)));
  const y = (maxY / 2) * (1 + Math.max(-1, Math.min(1, panY)));
  return { x: Math.round(x), y: Math.round(y), width: Math.round(cw), height: Math.round(ch) };
}
