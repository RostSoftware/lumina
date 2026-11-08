import { useState } from 'react';
import { Link } from 'react-router-dom';
import { Bookmark, MessageCircle, Repeat2, Send, Zap } from 'lucide-react';
import type { NostrEvent } from '@nostrify/nostrify';
import { HeartIcon, ICON_STROKE } from '@/components/common/Icons';
import { useLoginDialog } from '@/hooks/useLoginDialog';
import { useLike, useRepost } from '@/hooks/usePostActions';
import { useBookmarks, useToggleBookmark } from '@/hooks/useBookmarks';
import type { PostStats } from '@/hooks/usePostStats';
import { postPath } from '@/lib/nostr';
import { cn } from '@/lib/utils';
import { ShareSheet } from './ShareSheet';
import { ZapSheet } from './ZapSheet';
import { CarouselDots } from './MediaCarousel';

interface PostActionsProps {
  event: NostrEvent;
  stats: PostStats;
  imageCount: number;
  imageIndex: number;
  /** Called when the comment icon is pressed (focus composer on detail). */
  onComment?: () => void;
}

const btn = 'relative inline-flex size-11 items-center justify-center rounded-full transition-opacity hover:opacity-60 [&_svg]:size-6';

/** Like · Comment · Repost · Zap · Share ……… Bookmark */
export function PostActions({ event, stats, imageCount, imageIndex, onComment }: PostActionsProps) {
  const { requireLogin } = useLoginDialog();
  const like = useLike();
  const repost = useRepost();
  const { ids: bookmarks } = useBookmarks();
  const toggleBookmark = useToggleBookmark();
  const [sheet, setSheet] = useState<'share' | 'zap' | null>(null);
  const [pop, setPop] = useState(0);

  const liked = !!stats.likedByMe;
  const reposted = !!stats.repostedByMe;
  const bookmarked = bookmarks.has(event.id);

  return (
    <div className="relative flex items-center px-1">
      <button
        type="button"
        className={btn}
        aria-label={liked ? 'Unlike' : 'Like'}
        aria-pressed={liked}
        onClick={() => {
          if (!requireLogin()) return;
          if (!liked) setPop((p) => p + 1);
          like.mutate({ post: event, existing: stats.likedByMe });
        }}
      >
        <HeartIcon key={pop} active={liked} className={cn(liked && 'text-like', pop > 0 && liked && 'animate-heart-pop')} />
      </button>
      {onComment ? (
        <button type="button" className={btn} aria-label="Comment" onClick={onComment}>
          <MessageCircle strokeWidth={ICON_STROKE} className="-scale-x-100" />
        </button>
      ) : (
        <Link to={postPath(event)} state={{ focusComment: true }} className={btn} aria-label="Comment">
          <MessageCircle strokeWidth={ICON_STROKE} className="-scale-x-100" />
        </Link>
      )}
      <button
        type="button"
        className={btn}
        aria-label={reposted ? 'Undo repost' : 'Repost'}
        aria-pressed={reposted}
        onClick={() => {
          if (!requireLogin()) return;
          repost.mutate({ post: event, existing: stats.repostedByMe });
        }}
      >
        <Repeat2 strokeWidth={ICON_STROKE} className={cn(reposted && 'text-accent')} />
      </button>
      <button
        type="button"
        className={btn}
        aria-label="Zap"
        onClick={() => requireLogin() && setSheet('zap')}
      >
        <Zap strokeWidth={ICON_STROKE} className={cn(stats.zapTotal > 0 && 'text-zap')} />
      </button>
      <button type="button" className={btn} aria-label="Share" onClick={() => setSheet('share')}>
        <Send strokeWidth={ICON_STROKE} />
      </button>

      <div className="pointer-events-none absolute inset-x-0 flex justify-center">
        <CarouselDots count={imageCount} index={imageIndex} />
      </div>

      <button
        type="button"
        className={cn(btn, 'ml-auto')}
        aria-label={bookmarked ? 'Remove from saved' : 'Save'}
        aria-pressed={bookmarked}
        onClick={() => requireLogin() && toggleBookmark.mutate({ event, bookmark: !bookmarked })}
      >
        <Bookmark strokeWidth={ICON_STROKE} fill={bookmarked ? 'currentColor' : 'none'} />
      </button>

      <ShareSheet event={event} open={sheet === 'share'} onOpenChange={(o) => !o && setSheet(null)} />
      <ZapSheet recipient={event.pubkey} event={event} open={sheet === 'zap'} onOpenChange={(o) => !o && setSheet(null)} />
    </div>
  );
}
