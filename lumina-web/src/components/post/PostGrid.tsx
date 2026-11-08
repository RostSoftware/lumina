import { Link } from 'react-router-dom';
import { Copy, EyeOff } from 'lucide-react';
import { Skeleton } from '@/components/ui/skeleton';
import { useAppContext } from '@/hooks/useAppContext';
import { postPath } from '@/lib/nostr';
import type { PicturePost } from '@/lib/picture';
import { cn } from '@/lib/utils';
import { ProgressiveImage } from './ProgressiveImage';

/** 3-column square-crop grid with 2px gaps (Profile and Discover). */
export function PostGrid({ posts }: { posts: PicturePost[] }) {
  const { config } = useAppContext();
  return (
    <div className="grid grid-cols-3 gap-0.5">
      {posts.map((post, i) => {
        const sensitive = post.contentWarning !== undefined && !config.showSensitive;
        const first = post.images[0];
        return (
          <Link
            key={post.event.id}
            to={postPath(post.event)}
            className="relative block aspect-square overflow-hidden bg-surface-muted focus-visible:z-10"
            aria-label={post.title || first.alt || 'Open post'}
          >
            <ProgressiveImage image={first} alt={first.alt || post.title || 'Photo'} priority={i < 9} compact className={cn(sensitive && 'blur-xl scale-110')} />
            {post.images.length > 1 && <Copy className="absolute top-2 right-2 size-4 text-white drop-shadow" aria-label="Multiple photos" />}
            {sensitive && (
              <span className="absolute inset-0 flex items-center justify-center text-white">
                <EyeOff className="size-6 drop-shadow" aria-label="Sensitive content" />
              </span>
            )}
          </Link>
        );
      })}
    </div>
  );
}

export function PostGridSkeleton({ count = 12 }: { count?: number }) {
  return (
    <div className="grid grid-cols-3 gap-0.5" aria-hidden>
      {Array.from({ length: count }, (_, i) => (
        <Skeleton key={i} className="aspect-square rounded-none" />
      ))}
    </div>
  );
}
