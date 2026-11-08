import { useEffect, useMemo, useRef, useState } from 'react';
import { decode } from 'blurhash';
import { ImageOff } from 'lucide-react';
import { useAppContext } from '@/hooks/useAppContext';
import { getEffectiveBlossomServers } from '@/lib/appBlossom';
import { candidateUrls, type PictureImage } from '@/lib/picture';
import { cn } from '@/lib/utils';

/** Paints a blurhash onto a tiny canvas that is stretched to fill its parent. */
export function BlurhashCanvas({ hash, className }: { hash?: string; className?: string }) {
  const ref = useRef<HTMLCanvasElement>(null);
  useEffect(() => {
    const canvas = ref.current;
    if (!canvas || !hash) return;
    try {
      const pixels = decode(hash, 32, 32);
      const ctx = canvas.getContext('2d');
      if (!ctx) return;
      const data = ctx.createImageData(32, 32);
      data.data.set(pixels);
      ctx.putImageData(data, 0, 0);
    } catch {
      // invalid blurhash — the skeleton background shows instead
    }
  }, [hash]);
  if (!hash) return null;
  return <canvas ref={ref} width={32} height={32} aria-hidden className={cn('absolute inset-0 size-full', className)} />;
}

interface ProgressiveImageProps {
  image: PictureImage;
  alt: string;
  fit?: 'cover' | 'contain';
  className?: string;
  /** Eager-load (first image in view). */
  priority?: boolean;
  /** Draw the blurhash under the image while loading. */
  placeholder?: boolean;
  /** Small tiles: show only an icon when the image fails. */
  compact?: boolean;
}

/**
 * Image with blurhash/skeleton while loading, and automatic fallback to the
 * `fallback` URLs and then to other Blossom servers by `x` hash.
 */
export function ProgressiveImage({ image, alt, fit = 'cover', className, priority, placeholder = true, compact }: ProgressiveImageProps) {
  const { config } = useAppContext();
  const servers = useMemo(
    () => getEffectiveBlossomServers(config.blossomServerMetadata, config.useAppBlossomServers),
    [config.blossomServerMetadata, config.useAppBlossomServers],
  );
  const urls = useMemo(() => candidateUrls(image, servers), [image, servers]);
  const [attempt, setAttempt] = useState(0);
  const [loaded, setLoaded] = useState(false);
  const failed = attempt >= urls.length;

  return (
    <div className={cn('relative size-full overflow-hidden bg-surface-muted', !loaded && !image.blurhash && 'animate-pulse', className)}>
      {placeholder && !loaded && <BlurhashCanvas hash={image.blurhash} />}
      {!failed ? (
        <img
          key={urls[attempt]}
          src={urls[attempt]}
          alt={alt}
          loading={priority ? 'eager' : 'lazy'}
          decoding="async"
          referrerPolicy="no-referrer"
          draggable={false}
          onLoad={() => setLoaded(true)}
          onError={() => {
            setLoaded(false);
            setAttempt((a) => a + 1);
          }}
          className={cn(
            'absolute inset-0 size-full transition-opacity duration-200 ease-out select-none',
            fit === 'cover' ? 'object-cover' : 'object-contain',
            loaded ? 'opacity-100' : 'opacity-0',
          )}
        />
      ) : (
        <div className="absolute inset-0 flex flex-col items-center justify-center gap-2 text-muted-foreground">
          <ImageOff className={compact ? 'size-6' : 'size-8'} aria-label={compact ? 'Image unavailable' : undefined} aria-hidden={!compact} />
          {!compact && <span className="text-meta">Image unavailable</span>}
        </div>
      )}
    </div>
  );
}
