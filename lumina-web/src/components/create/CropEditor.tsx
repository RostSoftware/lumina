import { useRef } from 'react';
import { computeCrop } from '@/lib/image';
import { cn } from '@/lib/utils';
import type { CropState } from '@/lib/crop';

interface CropEditorProps {
  src: string;
  width: number;
  height: number;
  crop: CropState;
  onChange: (crop: CropState) => void;
  alt: string;
}

/** Drag-to-pan crop preview. The actual crop is applied at upload time. */
export function CropEditor({ src, width, height, crop, onChange, alt }: CropEditorProps) {
  const rect = computeCrop(width, height, crop.aspect, crop.zoom, crop.panX, crop.panY);
  const drag = useRef<{ x: number; y: number; panX: number; panY: number } | null>(null);
  const box = useRef<HTMLDivElement>(null);
  const aspect = rect.width / rect.height;

  const onPointerDown = (e: React.PointerEvent) => {
    drag.current = { x: e.clientX, y: e.clientY, panX: crop.panX, panY: crop.panY };
    (e.target as HTMLElement).setPointerCapture(e.pointerId);
  };
  const onPointerMove = (e: React.PointerEvent) => {
    if (!drag.current || !box.current) return;
    const scale = box.current.clientWidth / rect.width; // screen px per image px
    const movableX = (width - rect.width) / 2;
    const movableY = (height - rect.height) / 2;
    const dx = (e.clientX - drag.current.x) / scale;
    const dy = (e.clientY - drag.current.y) / scale;
    onChange({
      ...crop,
      panX: movableX > 0 ? Math.max(-1, Math.min(1, drag.current.panX - dx / movableX)) : 0,
      panY: movableY > 0 ? Math.max(-1, Math.min(1, drag.current.panY - dy / movableY)) : 0,
    });
  };
  const end = () => {
    drag.current = null;
  };

  return (
    <div className="flex w-full items-center justify-center bg-surface-muted" style={{ aspectRatio: '1' }}>
      <div
        ref={box}
        className={cn('relative max-h-full max-w-full touch-none overflow-hidden', 'cursor-grab active:cursor-grabbing')}
        style={aspect >= 1 ? { width: '100%', aspectRatio: String(aspect) } : { height: '100%', aspectRatio: String(aspect) }}
        onPointerDown={onPointerDown}
        onPointerMove={onPointerMove}
        onPointerUp={end}
        onPointerCancel={end}
      >
        <img
          src={src}
          alt={alt}
          draggable={false}
          className="pointer-events-none absolute max-w-none select-none"
          style={{
            width: `${(width / rect.width) * 100}%`,
            height: `${(height / rect.height) * 100}%`,
            left: `${(-rect.x / rect.width) * 100}%`,
            top: `${(-rect.y / rect.height) * 100}%`,
          }}
        />
        <div className="pointer-events-none absolute inset-0 grid grid-cols-3 grid-rows-3" aria-hidden>
          {Array.from({ length: 9 }, (_, i) => (
            <span key={i} className="border border-white/20" />
          ))}
        </div>
      </div>
    </div>
  );
}
