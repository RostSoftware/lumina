import { useMemo, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useSeoMeta } from '@unhead/react';
import { Camera, Copy, Ellipsis, Flag, Globe, Grid3x3, QrCode, Repeat2, Settings, SquareUserRound, UserX, Zap } from 'lucide-react';
import { TopBar, IconButton } from '@/components/layout/TopBar';
import { ICON_STROKE } from '@/components/common/Icons';
import { UserAvatar } from '@/components/common/UserAvatar';
import { Nip05Badge } from '@/components/common/UserName';
import { RichText } from '@/components/common/RichText';
import { BottomSheet, SheetAction } from '@/components/common/BottomSheet';
import { EmptyState, ErrorState } from '@/components/common/States';
import { InfiniteTrigger } from '@/components/common/InfiniteTrigger';
import { PostGrid, PostGridSkeleton } from '@/components/post/PostGrid';
import { ZapSheet } from '@/components/post/ZapSheet';
import { ReportSheet } from '@/components/post/ReportSheet';
import { copyText } from '@/lib/clipboard';
import { FollowButton } from '@/components/profile/FollowButton';
import { UserListSheet } from '@/components/profile/UserListSheet';
import { Button } from '@/components/ui/button';
import { Skeleton } from '@/components/ui/skeleton';
import { QRCodeCanvas } from '@/components/ui/qrcode';
import { useAuthor } from '@/hooks/useAuthor';
import { useCurrentUser } from '@/hooks/useCurrentUser';
import { useFollowing } from '@/hooks/useContacts';
import { useEventCount } from '@/hooks/useCount';
import { usePictureFeed, useRepostFeed, type FeedSpec } from '@/hooks/useFeed';
import { useLoginDialog } from '@/hooks/useLoginDialog';
import { useMuteList, useMuteMutation } from '@/hooks/useMutes';
import { useNostr } from '@nostrify/react';
import { useQuery } from '@tanstack/react-query';
import { compactNumber } from '@/lib/format';
import { displayName, handleName, KIND_CONTACTS, KIND_PICTURE, npubOf } from '@/lib/nostr';
import { cn } from '@/lib/utils';

type Tab = 'posts' | 'reposts' | 'tagged';

export default function ProfilePage({ pubkey }: { pubkey: string }) {
  const navigate = useNavigate();
  const { user } = useCurrentUser();
  const { requireLogin } = useLoginDialog();
  const author = useAuthor(pubkey);
  const metadata = author.data?.metadata;
  const own = user?.pubkey === pubkey;
  const name = displayName(metadata, pubkey);
  const handle = handleName(metadata, pubkey);
  const [tab, setTab] = useState<Tab>('posts');
  const [sheet, setSheet] = useState<'menu' | 'zap' | 'report' | 'qr' | 'followers' | 'following' | null>(null);
  const { mutes } = useMuteList();
  const mute = useMuteMutation();
  const muted = mutes.pubkeys.has(pubkey);

  useSeoMeta({ title: `${name} · LUMINA`, description: metadata?.about?.slice(0, 160) });

  const following = useFollowing(pubkey);
  const postCount = useEventCount(['posts', pubkey], { kinds: [KIND_PICTURE], authors: [pubkey] }, { cap: 500 });
  const followerCount = useEventCount(['followers', pubkey], { kinds: [KIND_CONTACTS], '#p': [pubkey] }, { distinctAuthors: true, cap: 2000 });

  const postsSpec = useMemo<FeedSpec>(() => ({ type: 'author', pubkey }), [pubkey]);
  const taggedSpec = useMemo<FeedSpec>(() => ({ type: 'tagged', pubkey }), [pubkey]);
  const posts = usePictureFeed(tab === 'posts' ? postsSpec : undefined);
  const tagged = usePictureFeed(tab === 'tagged' ? taggedSpec : undefined);
  const reposts = useRepostFeed(tab === 'reposts' ? pubkey : undefined);
  const repostPosts = useMemo(() => (reposts.data?.pages ?? []).flatMap((p) => p.posts), [reposts.data]);

  const active =
    tab === 'posts'
      ? { posts: posts.posts, q: posts }
      : tab === 'tagged'
        ? { posts: tagged.posts, q: tagged }
        : { posts: repostPosts, q: reposts };

  const lightning = metadata?.lud16 || undefined;
  const canZap = !!(metadata?.lud16 || metadata?.lud06);

  return (
    <>
      <TopBar
        back={!own}
        title={handle}
        right={
          own ? (
            <IconButton label="Settings" onClick={() => navigate('/settings')}>
              <Settings strokeWidth={ICON_STROKE} />
            </IconButton>
          ) : (
            <IconButton label="More options" onClick={() => setSheet('menu')}>
              <Ellipsis strokeWidth={ICON_STROKE} />
            </IconButton>
          )
        }
      />
      <main className="column">
        <section className="px-4 pt-4 pb-3">
          <div className="flex items-center gap-6">
            {author.isLoading ? <Skeleton className="size-[94px] rounded-full" /> : <UserAvatar pubkey={pubkey} size={86} ring />}
            <dl className="grid flex-1 grid-cols-3 text-center">
              <Stat label="posts" value={postCount.data ? `${compactNumber(postCount.data.count)}${postCount.data.capped ? '+' : ''}` : undefined} />
              <button type="button" onClick={() => setSheet('followers')} className="rounded-lg hover:bg-surface-muted">
                <Stat label="followers" value={followerCount.data ? `${compactNumber(followerCount.data.count)}${followerCount.data.capped ? '+' : ''}` : undefined} />
              </button>
              <button type="button" onClick={() => setSheet('following')} className="rounded-lg hover:bg-surface-muted">
                <Stat label="following" value={following.isLoading ? undefined : compactNumber(following.follows.length)} />
              </button>
            </dl>
          </div>
          <div className="mt-3 space-y-1">
            {author.isLoading ? (
              <Skeleton className="h-4 w-40" />
            ) : (
              <h2 className="flex items-center gap-1 text-body font-semibold">
                {name}
                <Nip05Badge nip05={metadata?.nip05} pubkey={pubkey} />
              </h2>
            )}
            {metadata?.nip05 && <p className="text-meta text-muted-foreground">{metadata.nip05.replace(/^_@/, '')}</p>}
            {metadata?.about && <p className="text-body"><RichText text={metadata.about} /></p>}
            {metadata?.website && (
              <a href={metadata.website} target="_blank" rel="noopener noreferrer nofollow" className="flex items-center gap-1 text-body font-semibold text-accent hover:underline">
                <Globe className="size-3.5" aria-hidden />
                {metadata.website.replace(/^https:\/\//, '').replace(/\/$/, '')}
              </a>
            )}
            {lightning && (
              <p className="flex items-center gap-1 text-meta text-muted-foreground">
                <Zap className="size-3.5 text-zap" aria-hidden /> {lightning}
              </p>
            )}
          </div>
          <div className="mt-4 flex gap-2">
            {own ? (
              <>
                <Button variant="secondary" className="h-9 flex-1 font-semibold" asChild>
                  <Link to="/settings/profile">Edit profile</Link>
                </Button>
                <Button variant="secondary" className="h-9 flex-1 font-semibold" onClick={() => setSheet('qr')}>
                  Share profile
                </Button>
              </>
            ) : (
              <>
                <FollowButton pubkey={pubkey} className="flex-1" />
                <Button variant="secondary" className="h-9 flex-1 font-semibold" onClick={() => requireLogin() && navigate(`/messages/${npubOf(pubkey)}`)}>
                  Message
                </Button>
                {canZap && (
                  <Button variant="secondary" className="h-9 px-3" aria-label="Zap" onClick={() => requireLogin() && setSheet('zap')}>
                    <Zap className="text-zap" />
                  </Button>
                )}
              </>
            )}
          </div>
        </section>

        <div role="tablist" aria-label="Profile content" className="flex border-t">
          {([
            ['posts', Grid3x3, 'Posts'],
            ['reposts', Repeat2, 'Reposts'],
            ['tagged', SquareUserRound, 'Tagged'],
          ] as const).map(([key, Icon, label]) => (
            <button
              key={key}
              role="tab"
              type="button"
              aria-selected={tab === key}
              aria-label={label}
              onClick={() => setTab(key)}
              className={cn('-mt-px flex h-12 flex-1 items-center justify-center border-t text-muted-foreground', tab === key && 'border-foreground text-foreground')}
            >
              <Icon className="size-6" strokeWidth={ICON_STROKE} />
            </button>
          ))}
        </div>

        <div role="tabpanel">
          {active.q.isLoading ? (
            <PostGridSkeleton />
          ) : active.q.isError && active.posts.length === 0 ? (
            <ErrorState error={active.q.error} onRetry={() => active.q.refetch()} />
          ) : active.posts.length === 0 ? (
            <EmptyState
              icon={tab === 'reposts' ? <Repeat2 strokeWidth={1.5} /> : tab === 'tagged' ? <SquareUserRound strokeWidth={1.5} /> : <Camera strokeWidth={1.5} />}
              title={tab === 'posts' ? (own ? 'Share your first photo' : 'No posts yet') : tab === 'reposts' ? 'No reposts yet' : 'No tagged photos'}
              description={tab === 'posts' && own ? 'When you share photos, they will appear on your profile.' : undefined}
              action={tab === 'posts' && own ? <Button className="h-11 px-6" asChild><Link to="/create">Create a post</Link></Button> : undefined}
            />
          ) : (
            <>
              <PostGrid posts={active.posts} />
              <InfiniteTrigger onLoadMore={() => active.q.fetchNextPage()} hasMore={!!active.q.hasNextPage} loading={active.q.isFetchingNextPage} />
            </>
          )}
        </div>
      </main>

      <BottomSheet open={sheet === 'menu'} onOpenChange={(o) => !o && setSheet(null)} title="Profile options" hideTitle>
        <div className="pb-4">
          <SheetAction icon={<QrCode />} label="Show QR code" onClick={() => setSheet('qr')} />
          <SheetAction icon={<Copy />} label="Copy npub" onClick={() => { copyText(npubOf(pubkey), 'npub copied'); setSheet(null); }} />
          <SheetAction icon={<Copy />} label="Copy profile link" onClick={() => { copyText(`${location.origin}/${npubOf(pubkey)}`, 'Link copied'); setSheet(null); }} />
          <SheetAction icon={<UserX />} label={muted ? 'Unmute' : 'Mute'} onClick={() => { setSheet(null); if (requireLogin()) mute.mutate({ item: { type: 'pubkey', value: pubkey }, mute: !muted }); }} />
          <SheetAction icon={<Flag />} label="Report" destructive onClick={() => (requireLogin() ? setSheet('report') : setSheet(null))} />
        </div>
      </BottomSheet>
      <BottomSheet open={sheet === 'qr'} onOpenChange={(o) => !o && setSheet(null)} title={handle}>
        <div className="flex flex-col items-center gap-3 p-6 pb-8">
          <div className="rounded-lg bg-white p-3"><QRCodeCanvas value={`nostr:${npubOf(pubkey)}`} size={220} /></div>
          <p className="max-w-full break-all text-center font-mono text-meta text-muted-foreground">{npubOf(pubkey)}</p>
          <Button variant="outline" className="h-11" onClick={() => copyText(npubOf(pubkey), 'npub copied')}><Copy /> Copy npub</Button>
        </div>
      </BottomSheet>
      <ZapSheet recipient={pubkey} open={sheet === 'zap'} onOpenChange={(o) => !o && setSheet(null)} />
      <ReportSheet pubkey={pubkey} open={sheet === 'report'} onOpenChange={(o) => !o && setSheet(null)} />
      <UserListSheet title="Following" users={following.follows} open={sheet === 'following'} onOpenChange={(o) => !o && setSheet(null)} />
      <FollowersSheet pubkey={pubkey} open={sheet === 'followers'} onOpenChange={(o) => !o && setSheet(null)} />
    </>
  );
}

function Stat({ label, value }: { label: string; value?: string }) {
  return (
    <div className="flex flex-col py-1">
      <dd className="text-body font-semibold">{value ?? <Skeleton className="mx-auto h-4 w-8" />}</dd>
      <dt className="text-meta text-muted-foreground">{label}</dt>
    </div>
  );
}

function FollowersSheet({ pubkey, open, onOpenChange }: { pubkey: string; open: boolean; onOpenChange: (o: boolean) => void }) {
  const { nostr } = useNostr();
  const { data } = useQuery({
    queryKey: ['nostr', 'followers', pubkey],
    enabled: open,
    queryFn: async ({ signal }) => {
      const events = await nostr.query([{ kinds: [KIND_CONTACTS], '#p': [pubkey], limit: 500 }], { signal: AbortSignal.any([signal, AbortSignal.timeout(8000)]) });
      return [...new Set(events.sort((a, b) => b.created_at - a.created_at).map((e) => e.pubkey))];
    },
  });
  return <UserListSheet title="Followers" users={data ?? []} open={open} onOpenChange={onOpenChange} />;
}
