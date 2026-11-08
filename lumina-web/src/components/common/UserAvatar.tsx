import { useState } from 'react';
import { User } from 'lucide-react';
import { useAuthor } from '@/hooks/useAuthor';
import { displayName } from '@/lib/nostr';
import { cn } from '@/lib/utils';

interface UserAvatarProps {
  pubkey: string;
  /** Pixel size: 26 bottom bar, 32 post header, 44 lists, 86 profile header. */
  size?: number;
  /** Brand-gradient story ring. */
  ring?: boolean;
  className?: string;
  /** Override the picture (e.g. in the profile editor preview). */
  src?: string;
}

/** Round avatar from kind 0 `picture` (sanitized), with a neutral fallback. */
export function UserAvatar({ pubkey, size = 32, ring, className, src }: UserAvatarProps) {
  const { data } = useAuthor(src ? undefined : pubkey);
  const picture = src ?? data?.metadata?.picture;
  const [failed, setFailed] = useState<string | null>(null);
  const name = displayName(data?.metadata, pubkey);
  const showImage = picture && failed !== picture;

  const inner = (
    <span
      className={cn('relative flex shrink-0 items-center justify-center overflow-hidden rounded-full bg-surface-muted text-muted-foreground', !ring && className)}
      style={{ width: size, height: size }}
    >
      {showImage ? (
        <img
          src={picture}
          alt={name}
          loading="lazy"
          decoding="async"
          referrerPolicy="no-referrer"
          className="size-full object-cover"
          onError={() => setFailed(picture)}
        />
      ) : (
        <User aria-label={name} style={{ width: size * 0.55, height: size * 0.55 }} strokeWidth={1.75} />
      )}
    </span>
  );

  if (!ring) return inner;
  return (
    <span className={cn('brand-gradient inline-flex shrink-0 rounded-full p-[2px]', className)}>
      <span className="rounded-full bg-background p-[2px]">{inner}</span>
    </span>
  );
}
