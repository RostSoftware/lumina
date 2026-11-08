import { Navigate, useSearchParams } from 'react-router-dom';
import { decodePointer } from '@/lib/nostr';
import NotFound from './NotFound';

/**
 * Target of the registered `nostr:` protocol handler: /open?uri=nostr:npub1…
 * Redirects to the NIP-19 route.
 */
export default function OpenUriPage() {
  const [params] = useSearchParams();
  const uri = (params.get('uri') ?? '').trim();
  const value = decodeURIComponent(uri).replace(/^(web\+)?nostr:(\/\/)?/i, '');
  if (!decodePointer(value)) return <NotFound />;
  return <Navigate to={`/${value}`} replace />;
}
