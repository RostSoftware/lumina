import { ArrowUp } from 'lucide-react';
import { UserAvatar } from '@/components/common/UserAvatar';

/** Floating pill announcing fresh posts at the top of a feed. */
export function NewPostsPill({ count, authors, onClick }: { count: number; authors: string[]; onClick: () => void }) {
  if (count === 0) return null;
  return (
    <div className="pointer-events-none sticky top-[calc(var(--bar-height)+var(--safe-top)+12px)] z-30 flex h-0 justify-center">
      <button
        type="button"
        onClick={onClick}
        className="pointer-events-auto flex h-10 items-center gap-2 rounded-full bg-accent px-4 text-meta font-semibold text-accent-foreground shadow-md animate-in fade-in slide-in-from-top-2"
      >
        <ArrowUp className="size-4" aria-hidden />
        <span className="flex -space-x-2">
          {authors.map((pk) => (
            <span key={pk} className="rounded-full ring-2 ring-accent">
              <UserAvatar pubkey={pk} size={20} />
            </span>
          ))}
        </span>
        {count >= 50 ? '50+' : count} new {count === 1 ? 'post' : 'posts'}
      </button>
    </div>
  );
}
