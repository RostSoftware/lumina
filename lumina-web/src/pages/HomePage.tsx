import { useMemo } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useSeoMeta } from '@unhead/react';
import { Camera, Send } from 'lucide-react';
import { TopBar, IconButton } from '@/components/layout/TopBar';
import { Wordmark } from '@/components/common/Wordmark';
import { ICON_STROKE } from '@/components/common/Icons';
import { EmptyState, ErrorState } from '@/components/common/States';
import { InfiniteTrigger } from '@/components/common/InfiniteTrigger';
import { PullToRefresh } from '@/components/common/PullToRefresh';
import { PostCard, PostCardSkeleton } from '@/components/post/PostCard';
import { NewPostsPill } from '@/components/post/NewPostsPill';
import { Button } from '@/components/ui/button';
import { useCurrentUser } from '@/hooks/useCurrentUser';
import { useFollowing } from '@/hooks/useContacts';
import { useNewPostsCount, usePictureFeed, useRefreshFeed, type FeedSpec } from '@/hooks/useFeed';
import { useLoginDialog } from '@/hooks/useLoginDialog';
import { useHasNewMessages } from '@/hooks/useMessages';

export default function HomePage() {
  useSeoMeta({ title: 'LUMINA', description: 'Photos on Nostr.' });
  const navigate = useNavigate();
  const { user } = useCurrentUser();
  const { openLogin } = useLoginDialog();
  const following = useFollowing(user?.pubkey);
  const unreadDMs = useHasNewMessages();

  const spec = useMemo<FeedSpec | undefined>(
    () => (user && following.follows.length ? { type: 'following', authors: [...following.follows, user.pubkey] } : undefined),
    [user, following.follows],
  );
  const feed = usePictureFeed(spec);
  const newest = feed.posts[0]?.event.created_at;
  const fresh = useNewPostsCount(spec, newest);
  const refresh = useRefreshFeed(spec);

  const showNewPosts = async () => {
    window.scrollTo({ top: 0, behavior: 'smooth' });
    await refresh();
  };

  const loadingContacts = !!user && following.isLoading;
  const noFollows = !user || (!following.isLoading && following.follows.length === 0);

  return (
    <>
      <TopBar
        left={<Link to="/" className="pl-2" aria-label="LUMINA home"><Wordmark /></Link>}
        right={
          <IconButton label={unreadDMs ? 'Messages (unread)' : 'Messages'} onClick={() => (user ? navigate('/messages') : openLogin())}>
            <Send strokeWidth={ICON_STROKE} />
            {unreadDMs && <span className="absolute top-2 right-2 size-2 rounded-full bg-like" aria-hidden />}
          </IconButton>
        }
      />
      <main className="column">
        <NewPostsPill count={fresh.count} authors={fresh.authors} onClick={showNewPosts} />
        <PullToRefresh onRefresh={refresh}>
          {loadingContacts || (spec && feed.isLoading) ? (
            <>
              <PostCardSkeleton />
              <PostCardSkeleton />
            </>
          ) : noFollows ? (
            <EmptyState
              icon={<Camera strokeWidth={1.5} />}
              title={user ? 'Your feed is empty' : 'Welcome to LUMINA'}
              description={user ? 'Follow people to see their photos here. Discover great photographers to get started.' : 'Photos from the people you follow on Nostr. Log in or explore what people are sharing.'}
              action={
                <div className="flex flex-col gap-2 sm:flex-row">
                  <Button asChild className="h-11 px-6">
                    <Link to="/discover">Explore Discover</Link>
                  </Button>
                  {!user && (
                    <Button variant="outline" className="h-11 px-6" onClick={openLogin}>
                      Log in
                    </Button>
                  )}
                </div>
              }
            />
          ) : feed.isError && feed.posts.length === 0 ? (
            <ErrorState title="Couldn't load your feed" error={feed.error} onRetry={() => feed.refetch()} />
          ) : feed.posts.length === 0 ? (
            <EmptyState
              icon={<Camera strokeWidth={1.5} />}
              title="No photos yet"
              description="The people you follow haven't posted any pictures yet."
              action={
                <Button asChild className="h-11 px-6">
                  <Link to="/discover">Explore Discover</Link>
                </Button>
              }
            />
          ) : (
            <>
              {feed.posts.map((post, i) => (
                <PostCard key={post.event.id} post={post} priority={i < 2} />
              ))}
              <InfiniteTrigger onLoadMore={() => feed.fetchNextPage()} hasMore={!!feed.hasNextPage} loading={feed.isFetchingNextPage} />
            </>
          )}
        </PullToRefresh>
      </main>
    </>
  );
}
