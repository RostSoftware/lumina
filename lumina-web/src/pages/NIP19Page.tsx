import { Navigate, useParams } from 'react-router-dom';
import { TopBar } from '@/components/layout/TopBar';
import { ErrorState } from '@/components/common/States';
import { PostCardSkeleton } from '@/components/post/PostCard';
import { useAddressableEvent } from '@/hooks/useFeed';
import { decodePointer, KIND_PICTURE, neventOf } from '@/lib/nostr';
import NotFound from './NotFound';
import PostPage from './PostPage';
import ProfilePage from './ProfilePage';

/**
 * Routes NIP-19 identifiers (npub, nprofile, note, nevent, naddr), optionally
 * prefixed with `nostr:`, to the matching profile or post screen.
 * nsec and unknown prefixes are a 404.
 */
export function NIP19Page() {
  const { nip19: raw } = useParams<{ nip19: string }>();
  const identifier = decodeURIComponent(raw ?? '');
  const pointer = decodePointer(identifier);

  if (!pointer) return <NotFound />;
  if (identifier.startsWith('nostr:')) return <Navigate to={`/${identifier.slice(6)}`} replace />;

  switch (pointer.type) {
    case 'profile':
      return <ProfilePage key={pointer.pubkey} pubkey={pointer.pubkey} />;
    case 'event':
      return <PostPage key={pointer.id} id={pointer.id} relays={pointer.relays} author={pointer.author} />;
    case 'address':
      return <AddressRedirect pointer={pointer} />;
  }
}

function AddressRedirect({ pointer }: { pointer: { kind: number; pubkey: string; identifier: string; relays?: string[] } }) {
  const { data, isLoading, isError, error } = useAddressableEvent(pointer);
  if (isLoading) {
    return (
      <>
        <TopBar back title="Post" />
        <main className="column"><PostCardSkeleton /></main>
      </>
    );
  }
  if (isError || !data) {
    return (
      <>
        <TopBar back title="Not found" />
        <main className="column"><ErrorState title="Couldn't find this" error={error} /></main>
      </>
    );
  }
  if (data.kind === KIND_PICTURE) return <Navigate to={`/${neventOf(data)}`} replace />;
  return (
    <>
      <TopBar back title="Unsupported" />
      <main className="column">
        <ErrorState title="Not supported in LUMINA" error={`This is a kind ${data.kind} event. LUMINA only shows picture posts and profiles.`} />
      </main>
    </>
  );
}
