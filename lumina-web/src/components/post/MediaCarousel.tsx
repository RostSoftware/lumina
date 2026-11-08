import { useRef, useState } from 'react';
import { ChevronLeft, ChevronRight, Heart } from 'lucide-react';
import { ProgressiveImage } from './ProgressiveImage';
import { SensitiveOverlay } from './SensitiveOverlay';
import { MediaViewer } from './MediaViewer';
import { useAppContext } from '@/hooks/useAppContext';
import { displayAspect, type PicturePost } from '@/lib/picture';
import { cn } from '@/lib/utils';

interface MediaCarouselProps {
  post: PicturePost;
  /** Called on double tap. */
  onDoubleTap?: () => void;
  priority?: boolean;
  altFallback: string;
  onIndexChange?: (index: number) => void;
}

/**
 * Swipeable carousel (scroll-snap) with dot indicators, double-tap to like
 * with a heart burst, single tap opens the full-screen viewer, and a
 * content-warning blur overlay.
 */
export function MediaCarousel({ post, onDoubleTap, priority, altFallback, onIndexChange }: MediaCarouselProps) {
  const { config } = useAppContext();
  const [index, setIndex] = useState(0);
  const [revealed, setRevealed] = useState(false);
  const [bursts, setBursts] = useState<number[]>([]);
  const [viewer, setViewer] = useState(false);
  const scroller = useRef<HTMLDivElement>(null);
  const lastTap = useRef(0);
  const tapTimer = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);

  const aspect = displayAspect(post.images[0]);
  const hidden = post.contentWarning !== undefined && !revealed && !config.showSensitive;
  const multiple = post.images.length > 1;

  const onTap = () => {
    if (hidden) return;
    const now = Date.now();
    if (now - lastTap.current < 300) {
      clearTimeout(tapTimer.current);
      lastTap.current = 0;
      const id = now;
      setBursts((b) => [...b, id]);
      setTimeout(() => setBursts((b) => b.filter((x) => x !== id)), 900);
      onDoubleTap?.();
      return;
    }
    lastTap.current = now;
    tapTimer.current = setTimeout(() => setViewer(true), 300);
  };

  const scrollTo = (i: number) => {
    const el = scroller.current;
    if (!el) return;
    el.scrollTo({ left: i * el.clientWidth, behavior: 'smooth' });
  };

  return (
    <div className="group relative w-full bg-surface-muted" style={{ aspectRatio: String(aspect) }}>
      <div
        ref={scroller}
        className="scrollbar-none flex size-full snap-x snap-mandatory overflow-x-auto overscroll-x-contain"
        onScroll={(e) => {
          const el = e.currentTarget;
          const next = Math.round(el.scrollLeft / Math.max(1, el.clientWidth));
          if (next !== index) {
            setIndex(next);
            onIndexChange?.(next);
          }
        }}
        onClick={onTap}
        role="group"
        aria-roledescription="carousel"
        aria-label={multiple ? `Photo ${index + 1} of ${post.images.length}` : 'Photo'}
      >
        {post.images.map((image, i) => (
          <div key={`${image.url}-${i}`} className="relative size-full shrink-0 snap-center">
            <ProgressiveImage image={image} alt={image.alt || altFallback} priority={priority && i === 0} className={cn(hidden && 'blur-2xl')} />
          </div>
        ))}
      </div>

      {hidden && <SensitiveOverlay reason={post.contentWarning} onReveal={() => setRevealed(true)} />}

      {bursts.map((id) => (
        <Heart key={id} aria-hidden className="pointer-events-none absolute inset-0 m-auto size-24 animate-heart-burst fill-white text-white drop-shadow-lg" />
      ))}

      {multiple && !hidden && (
        <>
          <span className="pointer-events-none absolute top-3 right-3 rounded-full bg-black/60 px-2 py-0.5 text-meta text-white">
            {index + 1}/{post.images.length}
          </span>
          {index > 0 && (
            <button type="button" aria-label="Previous photo" onClick={() => scrollTo(index - 1)} className="absolute top-1/2 left-2 hidden size-8 -translate-y-1/2 items-center justify-center rounded-full bg-white/80 text-black opacity-0 transition-opacity group-hover:opacity-100 focus-visible:opacity-100 sm:flex">
              <ChevronLeft className="size-5" />
            </button>
          )}
          {index < post.images.length - 1 && (
            <button type="button" aria-label="Next photo" onClick={() => scrollTo(index + 1)} className="absolute top-1/2 right-2 hidden size-8 -translate-y-1/2 items-center justify-center rounded-full bg-white/80 text-black opacity-0 transition-opacity group-hover:opacity-100 focus-visible:opacity-100 sm:flex">
              <ChevronRight className="size-5" />
            </button>
          )}
        </>
      )}

      {viewer && <MediaViewer images={post.images} start={index} onClose={() => setViewer(false)} altFallback={altFallback} />}
    </div>
  );
}

/** Carousel dot indicators, rendered in the action row like Instagram. */
export function CarouselDots({ count, index }: { count: number; index: number }) {
  if (count < 2) return null;
  return (
    <div className="flex items-center gap-1" aria-hidden>
      {Array.from({ length: count }, (_, i) => (
        <span key={i} className={cn('size-1.5 rounded-full transition-colors', i === index ? 'bg-accent' : 'bg-border')} />
      ))}
    </div>
  );
}
