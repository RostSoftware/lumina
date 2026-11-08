import { useMemo, useState, type FormEvent } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useSeoMeta } from '@unhead/react';
import { Hash, Search, X } from 'lucide-react';
import { TopBar } from '@/components/layout/TopBar';
import { EmptyState, ErrorState } from '@/components/common/States';
import { InfiniteTrigger } from '@/components/common/InfiniteTrigger';
import { PullToRefresh } from '@/components/common/PullToRefresh';
import { PostGrid, PostGridSkeleton } from '@/components/post/PostGrid';
import { NewPostsPill } from '@/components/post/NewPostsPill';
import { useNewPostsCount, usePictureFeed, useRefreshFeed, type FeedSpec } from '@/hooks/useFeed';
import { resolveNip05 } from '@/hooks/useNip05';
import { toast } from '@/hooks/useToast';
import { decodePointer, npubOf } from '@/lib/nostr';

const SUGGESTED = ['photography', 'nature', 'art', 'travel', 'streetphotography', 'food', 'portrait', 'bitcoin'];

export default function DiscoverPage() {
  const [params, setParams] = useSearchParams();
  const navigate = useNavigate();
  const tag = params.get('t')?.toLowerCase().replace(/^#/, '') || undefined;
  const [query, setQuery] = useState(tag ? `#${tag}` : '');
  useSeoMeta({ title: tag ? `#${tag} · LUMINA` : 'Discover · LUMINA' });

  const spec = useMemo<FeedSpec>(() => ({ type: 'discover', hashtag: tag }), [tag]);
  const feed = usePictureFeed(spec);
  const fresh = useNewPostsCount(spec, feed.posts[0]?.event.created_at);
  const refresh = useRefreshFeed(spec);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    const value = query.trim();
    if (!value) {
      setParams({});
      return;
    }
    const pointer = decodePointer(value);
    if (pointer) {
      navigate(`/${value.replace(/^nostr:/, '')}`);
      return;
    }
    if (/^[^\s@]*@?[a-z0-9.-]+\.[a-z]{2,}$/i.test(value) && value.includes('.')) {
      try {
        const pubkey = await resolveNip05(value);
        if (pubkey) {
          navigate(`/${npubOf(pubkey)}`);
          return;
        }
      } catch {
        // not a NIP-05 — treat as hashtag
      }
    }
    const hashtag = value.replace(/^#/, '').replace(/\s+/g, '').toLowerCase();
    if (!/^[\p{L}\p{N}_]+$/u.test(hashtag)) {
      toast({ title: 'Search by #hashtag, npub or name@domain' });
      return;
    }
    setQuery(`#${hashtag}`);
    setParams({ t: hashtag });
  };

  return (
    <>
      <TopBar
        title={
          <form onSubmit={submit} role="search" className="relative w-full font-normal">
            <Search className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-muted-foreground" aria-hidden />
            <input
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="Search #hashtags, npub or name@domain"
              aria-label="Search"
              enterKeyHint="search"
              className="h-9 w-full rounded-lg bg-surface-muted pr-9 pl-9 text-body outline-none placeholder:text-muted-foreground focus-visible:ring-2 focus-visible:ring-accent"
            />
            {query && (
              <button type="button" aria-label="Clear search" onClick={() => { setQuery(''); setParams({}); }} className="absolute inset-y-0 right-0 flex w-9 items-center justify-center text-muted-foreground">
                <X className="size-4" />
              </button>
            )}
          </form>
        }
      />
      <main className="column">
        <NewPostsPill count={fresh.count} authors={fresh.authors} onClick={() => { window.scrollTo({ top: 0, behavior: 'smooth' }); refresh(); }} />
        {!tag && (
          <div className="scrollbar-none flex gap-2 overflow-x-auto px-3 py-2">
            {SUGGESTED.map((s) => (
              <button key={s} type="button" onClick={() => { setQuery(`#${s}`); setParams({ t: s }); }} className="h-8 shrink-0 rounded-full border px-3 text-meta font-semibold hover:bg-surface-muted">
                #{s}
              </button>
            ))}
          </div>
        )}
        {tag && (
          <div className="flex items-center gap-3 px-4 py-4">
            <span className="flex size-14 items-center justify-center rounded-full border"><Hash className="size-6" aria-hidden /></span>
            <div>
              <h2 className="text-title">#{tag}</h2>
              <p className="text-meta text-muted-foreground">Latest photos</p>
            </div>
          </div>
        )}
        <PullToRefresh onRefresh={refresh}>
          {feed.isLoading ? (
            <PostGridSkeleton count={18} />
          ) : feed.isError && feed.posts.length === 0 ? (
            <ErrorState error={feed.error} onRetry={() => feed.refetch()} />
          ) : feed.posts.length === 0 ? (
            <EmptyState icon={<Hash strokeWidth={1.5} />} title={tag ? `No photos for #${tag}` : 'Nothing here yet'} description="Try another hashtag or check your relays in Settings." />
          ) : (
            <>
              <PostGrid posts={feed.posts} />
              <InfiniteTrigger onLoadMore={() => feed.fetchNextPage()} hasMore={!!feed.hasNextPage} loading={feed.isFetchingNextPage} />
            </>
          )}
        </PullToRefresh>
      </main>
    </>
  );
}
