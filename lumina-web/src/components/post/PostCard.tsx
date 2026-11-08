import { useState } from 'react';
import { Link } from 'react-router-dom';
import { Zap } from 'lucide-react';
import { UserName } from '@/components/common/UserName';
import { RichText } from '@/components/common/RichText';
import { Skeleton } from '@/components/ui/skeleton';
import { useAuthor } from '@/hooks/useAuthor';
import { useCurrentUser } from '@/hooks/useCurrentUser';
import { useLike } from '@/hooks/usePostActions';
import { usePostStats } from '@/hooks/usePostStats';
import { compactNumber, formatSats, plural } from '@/lib/format';
import { handleName, postPath } from '@/lib/nostr';
import type { PicturePost } from '@/lib/picture';
import { MediaCarousel } from './MediaCarousel';
import { PostActions } from './PostActions';
import { PostHeader } from './PostHeader';

/** A post in the Following feed. */
export function PostCard({ post, priority }: { post: PicturePost; priority?: boolean }) {
  const { event } = post;
  const { user } = useCurrentUser();
  const { stats } = usePostStats(event.id);
  const like = useLike();
  const author = useAuthor(event.pubkey);
  const [imageIndex, setImageIndex] = useState(0);
  const [expanded, setExpanded] = useState(false);
  const name = handleName(author.data?.metadata, event.pubkey);

  const caption = post.caption.trim();
  const long = caption.length > 140 || caption.split('\n').length > 2;

  return (
    <article className="border-b pb-3 sm:mb-2" aria-label={`Post by ${name}`}>
      <PostHeader event={event} location={post.location} />
      <MediaCarousel
        post={post}
        priority={priority}
        altFallback={post.title || `Photo by ${name}`}
        onIndexChange={setImageIndex}
        onDoubleTap={() => {
          if (user && !stats.likedByMe) like.mutate({ post: event });
        }}
      />
      <PostActions event={event} stats={stats} imageCount={post.images.length} imageIndex={imageIndex} />
      <div className="space-y-1 px-3">
        <div className="flex flex-wrap items-center gap-x-3 text-body font-semibold">
          {stats.likeCount > 0 && (
            <Link to={postPath(event)} className="hover:opacity-70">
              {compactNumber(stats.likeCount)} {plural(stats.likeCount, 'like')}
            </Link>
          )}
          {stats.zapTotal > 0 && (
            <span className="inline-flex items-center gap-1 text-zap">
              <Zap className="size-3.5 fill-current" aria-hidden />
              {formatSats(stats.zapTotal)} sats
            </span>
          )}
        </div>
        {caption && (
          <p className="text-body">
            <UserName pubkey={event.pubkey} className="mr-1.5 align-top" />
            {long && !expanded ? (
              <>
                <RichText text={`${caption.slice(0, 120).trimEnd()}…`} />{' '}
                <button type="button" className="text-muted-foreground" onClick={() => setExpanded(true)}>
                  more
                </button>
              </>
            ) : (
              <RichText text={caption} />
            )}
          </p>
        )}
        {stats.commentCount > 0 && (
          <Link to={postPath(event)} className="block text-body text-muted-foreground hover:opacity-70">
            View {stats.commentCount === 1 ? '1 comment' : `all ${compactNumber(stats.commentCount)} comments`}
          </Link>
        )}
      </div>
    </article>
  );
}

export function PostCardSkeleton() {
  return (
    <div className="border-b pb-3" aria-hidden>
      <div className="flex items-center gap-3 px-3 py-2">
        <Skeleton className="size-8 rounded-full" />
        <Skeleton className="h-3.5 w-32" />
      </div>
      <Skeleton className="aspect-square w-full rounded-none" />
      <div className="flex gap-3 px-3 py-3">
        {[0, 1, 2, 3].map((i) => (
          <Skeleton key={i} className="size-6 rounded-full" />
        ))}
      </div>
      <div className="space-y-2 px-3">
        <Skeleton className="h-3.5 w-20" />
        <Skeleton className="h-3.5 w-3/4" />
      </div>
    </div>
  );
}
