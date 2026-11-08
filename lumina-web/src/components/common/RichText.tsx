import { Fragment, type ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { nip19 } from 'nostr-tools';
import { useAuthor } from '@/hooks/useAuthor';
import { decodePointer, handleName } from '@/lib/nostr';
import { cn } from '@/lib/utils';

/**
 * Renders untrusted text with links, nostr: mentions and #hashtags.
 * Never uses innerHTML. Only https:// URLs become links (the regex is the
 * sanitizer).
 */
const TOKEN = /(https:\/\/[^\s<>"']+[^\s<>"'.,;:!?)\]])|((?:nostr:)?(?:npub1|nprofile1|note1|nevent1|naddr1)[02-9ac-hj-np-z]{20,})|(#[\p{L}\p{N}_]{1,64})/gu;

export function RichText({ text, className, linkHashtags = true }: { text: string; className?: string; linkHashtags?: boolean }) {
  const parts: ReactNode[] = [];
  let last = 0;
  for (const match of text.matchAll(TOKEN)) {
    const index = match.index ?? 0;
    if (index > last) parts.push(text.slice(last, index));
    const [full, url, mention, hashtag] = match;
    if (url) {
      parts.push(
        <a key={index} href={url} target="_blank" rel="noopener noreferrer nofollow" className="text-accent hover:underline break-all">
          {url.replace(/^https:\/\//, '').slice(0, 48)}
          {url.length > 56 ? '…' : ''}
        </a>,
      );
    } else if (mention) {
      parts.push(<Mention key={index} value={mention} />);
    } else if (hashtag) {
      const tag = hashtag.slice(1).toLowerCase();
      parts.push(
        linkHashtags ? (
          <Link key={index} to={`/discover?t=${encodeURIComponent(tag)}`} className="text-accent hover:underline">
            {hashtag}
          </Link>
        ) : (
          <span key={index} className="text-accent">{hashtag}</span>
        ),
      );
    } else {
      parts.push(full);
    }
    last = index + full.length;
  }
  if (last < text.length) parts.push(text.slice(last));
  return <span className={cn('whitespace-pre-wrap break-words', className)}>{parts.map((p, i) => <Fragment key={i}>{p}</Fragment>)}</span>;
}

function Mention({ value }: { value: string }) {
  const pointer = decodePointer(value);
  if (!pointer) return <>{value}</>;
  if (pointer.type === 'profile') return <ProfileMention pubkey={pointer.pubkey} />;
  const bech = value.replace(/^nostr:/, '');
  return (
    <Link to={`/${bech}`} className="text-accent hover:underline">
      {bech.slice(0, 12)}…
    </Link>
  );
}

function ProfileMention({ pubkey }: { pubkey: string }) {
  const { data } = useAuthor(pubkey);
  return (
    <Link to={`/${nip19.npubEncode(pubkey)}`} className="text-accent hover:underline">
      @{handleName(data?.metadata, pubkey)}
    </Link>
  );
}
