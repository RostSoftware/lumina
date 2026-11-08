import { Link } from 'react-router-dom';
import { BadgeCheck } from 'lucide-react';
import { useAuthor } from '@/hooks/useAuthor';
import { useNip05 } from '@/hooks/useNip05';
import { handleName, displayName, profilePath } from '@/lib/nostr';
import { cn } from '@/lib/utils';

interface UserNameProps {
  pubkey: string;
  /** "handle" for post headers, "display" for profile names. */
  variant?: 'handle' | 'display';
  link?: boolean;
  showBadge?: boolean;
  className?: string;
}

export function UserName({ pubkey, variant = 'handle', link = true, showBadge = false, className }: UserNameProps) {
  const { data, isLoading } = useAuthor(pubkey);
  const metadata = data?.metadata;
  const name = variant === 'handle' ? handleName(metadata, pubkey) : displayName(metadata, pubkey);
  const content = (
    <>
      <span className={cn('truncate', isLoading && !metadata && 'text-muted-foreground')}>{name}</span>
      {showBadge && <Nip05Badge nip05={metadata?.nip05} pubkey={pubkey} />}
    </>
  );
  const classes = cn('inline-flex min-w-0 items-center gap-1 font-semibold', className);
  if (!link) return <span className={classes}>{content}</span>;
  return (
    <Link to={profilePath(pubkey)} className={cn(classes, 'hover:opacity-70')}>
      {content}
    </Link>
  );
}

export function Nip05Badge({ nip05, pubkey }: { nip05?: string; pubkey: string }) {
  const { data: verified } = useNip05(nip05, pubkey);
  if (!verified) return null;
  return <BadgeCheck className="size-3.5 shrink-0 text-accent" aria-label={`Verified as ${nip05}`} />;
}
