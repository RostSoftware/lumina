import { useEffect, useMemo, useRef, useState } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { useSeoMeta } from '@unhead/react';
import { MapPin, Zap } from 'lucide-react';
import type { NostrEvent } from '@nostrify/nostrify';
import { TopBar } from '@/components/layout/TopBar';
import { ErrorState } from '@/components/common/States';
import { RichText } from '@/components/common/RichText';
import { UserAvatar } from '@/components/common/UserAvatar';
import { UserName } from '@/components/common/UserName';
import { PostCardSkeleton } from '@/components/post/PostCard';
import { PostHeader } from '@/components/post/PostHeader';
import { MediaCarousel } from '@/components/post/MediaCarousel';
import { PostActions } from '@/components/post/PostActions';
import { CommentComposer, CommentList } from '@/components/post/Comments';
import { UserListSheet } from '@/components/profile/UserListSheet';
import { useAuthor } from '@/hooks/useAuthor';
import { useCurrentUser } from '@/hooks/useCurrentUser';
import { useEventById } from '@/hooks/useFeed';
import { useLike } from '@/hooks/usePostActions';
import { usePostStats } from '@/hooks/usePostStats';
import { compactNumber, formatSats, fullDate, plural } from '@/lib/format';
import { handleName, profilePath } from '@/lib/nostr';
import { parsePicturePost } from '@/lib/picture';

export default function PostPage({ id, relays, author }: { id: string; relays?: string[]; author?: string }) {
  const { data: event, isLoading, isError, error, refetch } = useEventById(id, { relays, author });

  if (isLoading) {
    return (
      <>
        <TopBar back title="Post" />
        <main className="column"><PostCardSkeleton /></main>
      </>
    );
  }
  if (isError || !event) {
    return (
      <>
        <TopBar back title="Post" />
        <main className="column"><ErrorState title="Post not found" error={error} onRetry={() => refetch()} /></main>
      </>
    );
  }
  return <PostDetail event={event} />;
}

function PostDetail({ event }: { event: NostrEvent }) {
  const post = useMemo(() => parsePicturePost(event), [event]);
  const author = useAuthor(event.pubkey);
  const name = handleName(author.data?.metadata, event.pubkey);
  const { user } = useCurrentUser();
  const { stats, comments } = usePostStats(event.id);
  const like = useLike();
  const location = useLocation();
  const composer = useRef<HTMLInputElement>(null);
  const [replyTo, setReplyTo] = useState<NostrEvent | undefined>();
  const [imageIndex, setImageIndex] = useState(0);
  const [sheet, setSheet] = useState<'likes' | 'zaps' | null>(null);

  useSeoMeta({ title: `${name} on LUMINA`, description: post?.caption.slice(0, 160) || 'A photo on LUMINA' });

  useEffect(() => {
    if ((location.state as { focusComment?: boolean } | null)?.focusComment) composer.current?.focus();
  }, [location.state]);

  if (!post) {
    return (
      <>
        <TopBar back title="Post" />
        <main className="column">
          <ErrorState title="Not a picture post" error="This event isn't a valid NIP-68 picture post, so LUMINA can't display it." />
        </main>
      </>
    );
  }

  const focusComposer = () => composer.current?.focus();

  return (
    <>
      <TopBar back title="Post" />
      <main className="column pb-24">
        <PostHeader event={event} location={post.location} showFollow />
        <MediaCarousel post={post} priority altFallback={post.title || `Photo by ${name}`} onIndexChange={setImageIndex} onDoubleTap={() => user && !stats.likedByMe && like.mutate({ post: event })} />
        <PostActions event={event} stats={stats} imageCount={post.images.length} imageIndex={imageIndex} onComment={focusComposer} />
        <div className="space-y-2 px-3">
          <div className="flex flex-wrap items-center gap-x-3 gap-y-1 text-body font-semibold">
            <button type="button" className="hover:opacity-70" onClick={() => setSheet('likes')}>
              {stats.likeCount === 0 ? (
                <span className="font-normal">Be the first to <span className="font-semibold">like this</span></span>
              ) : (
                <>{compactNumber(stats.likeCount)} {plural(stats.likeCount, 'like')}</>
              )}
            </button>
            {stats.repostCount > 0 && <span>{compactNumber(stats.repostCount)} {plural(stats.repostCount, 'repost')}</span>}
            {stats.zapTotal > 0 && (
              <button type="button" className="inline-flex items-center gap-1 text-zap hover:opacity-70" onClick={() => setSheet('zaps')}>
                <Zap className="size-3.5 fill-current" aria-hidden /> {formatSats(stats.zapTotal)} sats · {stats.zapCount} {plural(stats.zapCount, 'zap')}
              </button>
            )}
          </div>
          {stats.topZappers.length > 0 && (
            <button type="button" onClick={() => setSheet('zaps')} className="flex items-center gap-2 text-meta text-muted-foreground">
              <span className="flex -space-x-2">
                {stats.topZappers.slice(0, 3).map((z) => (
                  <span key={z.pubkey} className="rounded-full ring-2 ring-background"><UserAvatar pubkey={z.pubkey} size={20} /></span>
                ))}
              </span>
              Top zappers
            </button>
          )}
          {stats.emojis.some((e) => e.content !== '+') && (
            <div className="flex flex-wrap gap-1.5" aria-label="Reactions">
              {stats.emojis.map((e) => (
                <span key={e.content} className="inline-flex h-7 items-center gap-1 rounded-full bg-surface-muted px-2 text-meta">
                  {e.url && e.url.startsWith('https://') ? <img src={e.url} alt={e.content} className="size-4" referrerPolicy="no-referrer" /> : <span>{e.content === '+' ? '❤️' : e.content.slice(0, 8)}</span>}
                  {e.count}
                </span>
              ))}
            </div>
          )}
          {post.title && post.title !== post.caption && <p className="text-body font-semibold">{post.title}</p>}
          {post.caption && (
            <p className="text-body">
              <UserName pubkey={event.pubkey} className="mr-1.5 align-top" />
              <RichText text={post.caption} />
            </p>
          )}
          {post.hashtags.length > 0 && (
            <div className="flex flex-wrap gap-x-2 text-body">
              {post.hashtags.filter((t) => !post.caption.toLowerCase().includes(`#${t}`)).map((t) => (
                <Link key={t} to={`/discover?t=${encodeURIComponent(t)}`} className="text-accent hover:underline">#{t}</Link>
              ))}
            </div>
          )}
          {post.tagged.length > 0 && (
            <div className="flex flex-wrap items-center gap-2 text-meta text-muted-foreground">
              With
              {post.tagged.map((pk) => (
                <Link key={pk} to={profilePath(pk)} className="inline-flex items-center gap-1 rounded-full bg-surface-muted py-0.5 pr-2 pl-0.5 text-foreground">
                  <UserAvatar pubkey={pk} size={20} />
                  <UserName pubkey={pk} link={false} className="font-normal" />
                </Link>
              ))}
            </div>
          )}
          {post.location && (
            <p className="flex items-center gap-1 text-meta text-muted-foreground"><MapPin className="size-3.5" aria-hidden /> {post.location}</p>
          )}
          <time className="block text-meta text-muted-foreground uppercase" dateTime={new Date(event.created_at * 1000).toISOString()}>{fullDate(event.created_at)}</time>
        </div>
        <section aria-label="Comments" className="mt-3 border-t">
          <CommentList root={event} comments={comments} onReply={(c) => { setReplyTo(c); focusComposer(); }} />
        </section>
      </main>
      <div className="fixed inset-x-0 bottom-[calc(var(--bar-height)+var(--safe-bottom))] z-30">
        <div className="column">
          <CommentComposer ref={composer} root={event} replyTo={replyTo} onCancelReply={() => setReplyTo(undefined)} />
        </div>
      </div>
      <UserListSheet title="Likes" users={stats.likers} open={sheet === 'likes'} onOpenChange={(o) => !o && setSheet(null)} />
      <UserListSheet
        title="Zaps"
        users={stats.topZappers.length ? [...new Set(stats.zaps.map((z) => z.sender).filter((s): s is string => !!s))] : []}
        open={sheet === 'zaps'}
        onOpenChange={(o) => !o && setSheet(null)}
        trailing={(pk) => (
          <span className="inline-flex items-center gap-1 text-meta font-semibold text-zap">
            <Zap className="size-3.5 fill-current" aria-hidden />
            {formatSats(stats.zaps.filter((z) => z.sender === pk).reduce((s, z) => s + z.sats, 0))}
          </span>
        )}
      />
    </>
  );
}
