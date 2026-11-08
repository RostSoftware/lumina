import { EyeOff } from 'lucide-react';
import { Button } from '@/components/ui/button';

/** Blur cover for posts with a `content-warning` tag. Tap to reveal. */
export function SensitiveOverlay({ reason, onReveal }: { reason?: string; onReveal: () => void }) {
  return (
    <div className="absolute inset-0 z-10 flex flex-col items-center justify-center gap-2 bg-black/30 p-6 text-center text-white backdrop-blur-2xl">
      <EyeOff className="size-8" aria-hidden />
      <p className="text-title">Sensitive content</p>
      <p className="max-w-xs text-meta opacity-90">{reason || 'This photo may contain sensitive content.'}</p>
      <Button
        variant="outline"
        className="mt-2 h-11 border-white/60 bg-transparent text-white hover:bg-white/10 hover:text-white"
        onClick={(e) => {
          e.stopPropagation();
          onReveal();
        }}
      >
        See photo
      </Button>
    </div>
  );
}
