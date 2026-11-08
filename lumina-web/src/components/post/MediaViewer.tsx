import { useEffect, useState } from 'react';
import { createPortal } from 'react-dom';
import { ChevronLeft, ChevronRight, X } from 'lucide-react';
import { ProgressiveImage } from './ProgressiveImage';
import type { PictureImage } from '@/lib/picture';

/** Full-screen media viewer (hides all chrome). Escape / ← → supported. */
export function MediaViewer({ images, start, onClose, altFallback }: { images: PictureImage[]; start: number; onClose: () => void; altFallback: string }) {
  const [index, setIndex] = useState(start);
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
      if (e.key === 'ArrowRight') setIndex((i) => Math.min(images.length - 1, i + 1));
      if (e.key === 'ArrowLeft') setIndex((i) => Math.max(0, i - 1));
    };
    document.addEventListener('keydown', onKey);
    const overflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => {
      document.removeEventListener('keydown', onKey);
      document.body.style.overflow = overflow;
    };
  }, [images.length, onClose]);

  const image = images[index];
  return createPortal(
    <div role="dialog" aria-modal="true" aria-label="Photo viewer" className="fixed inset-0 z-[60] flex items-center justify-center bg-black" onClick={onClose}>
      <div className="relative size-full" onClick={(e) => e.stopPropagation()}>
        <ProgressiveImage key={image.url} image={image} alt={image.alt || altFallback} fit="contain" priority className="bg-black" />
      </div>
      <button type="button" aria-label="Close" onClick={onClose} className="absolute top-[calc(var(--safe-top)+8px)] right-2 flex size-11 items-center justify-center rounded-full bg-black/50 text-white">
        <X className="size-6" />
      </button>
      {images.length > 1 && (
        <>
          {index > 0 && (
            <button type="button" aria-label="Previous photo" onClick={(e) => (e.stopPropagation(), setIndex(index - 1))} className="absolute left-2 flex size-11 items-center justify-center rounded-full bg-black/50 text-white">
              <ChevronLeft className="size-6" />
            </button>
          )}
          {index < images.length - 1 && (
            <button type="button" aria-label="Next photo" onClick={(e) => (e.stopPropagation(), setIndex(index + 1))} className="absolute right-2 flex size-11 items-center justify-center rounded-full bg-black/50 text-white">
              <ChevronRight className="size-6" />
            </button>
          )}
          <div className="absolute bottom-[calc(var(--safe-bottom)+16px)] rounded-full bg-black/50 px-3 py-1 text-meta text-white">
            {index + 1} / {images.length}
          </div>
        </>
      )}
    </div>,
    document.body,
  );
}
