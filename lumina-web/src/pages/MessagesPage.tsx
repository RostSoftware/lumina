import { useEffect, useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useSeoMeta } from '@unhead/react';
import { MessageCircle, SquarePen } from 'lucide-react';
import { TopBar, IconButton } from '@/components/layout/TopBar';
import { ICON_STROKE } from '@/components/common/Icons';
import { EmptyState, ErrorState } from '@/components/common/States';
import { UserAvatar } from '@/components/common/UserAvatar';
import { UserName } from '@/components/common/UserName';
import { BottomSheet } from '@/components/common/BottomSheet';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Skeleton } from '@/components/ui/skeleton';
import { useCurrentUser } from '@/hooks/useCurrentUser';
import { useFollowing } from '@/hooks/useContacts';
import { useMarkMessagesSeen, useMessages } from '@/hooks/useMessages';
import { resolveNip05 } from '@/hooks/useNip05';
import { useAuthor } from '@/hooks/useAuthor';
import { shortAge } from '@/lib/format';
import { decodePointer, displayName, npubOf } from '@/lib/nostr';
import { cn } from '@/lib/utils';
import MyProfileRedirect from './MyProfileRedirect';

export default function MessagesPage() {
  useSeoMeta({ title: 'Messages · LUMINA' });
  const { user } = useCurrentUser();
  const messages = useMessages();
  const markSeen = useMarkMessagesSeen();
  const [compose, setCompose] = useState(false);

  const sourceIds = messages.data?.sourceIds;
  useEffect(() => {
    if (sourceIds) markSeen(sourceIds);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [sourceIds?.length]);

  if (!user) return <MyProfileRedirect />;

  return (
    <>
      <TopBar
        back="/"
        title="Messages"
        right={
          <IconButton label="New message" onClick={() => setCompose(true)}>
            <SquarePen strokeWidth={ICON_STROKE} />
          </IconButton>
        }
      />
      <main className="column">
        {messages.data?.unsupported && (
          <p className="m-3 rounded-lg bg-surface-muted p-3 text-meta text-muted-foreground">
            Your signer doesn't support NIP-44 encryption, so private (NIP-17) messages can't be read or sent. Only legacy messages are shown.
          </p>
        )}
        {messages.isLoading ? (
          <div className="space-y-1 py-2" aria-label="Decrypting messages">
            {Array.from({ length: 7 }, (_, i) => (
              <div key={i} className="flex items-center gap-3 px-4 py-2">
                <Skeleton className="size-14 rounded-full" />
                <div className="flex-1 space-y-2">
                  <Skeleton className="h-3.5 w-32" />
                  <Skeleton className="h-3 w-48" />
                </div>
              </div>
            ))}
            <p className="px-4 pt-2 text-center text-meta text-muted-foreground">Decrypting messages with your signer…</p>
          </div>
        ) : messages.isError ? (
          <ErrorState error={messages.error} onRetry={() => messages.refetch()} />
        ) : messages.conversations.length === 0 ? (
          <EmptyState
            icon={<MessageCircle strokeWidth={1.5} />}
            title="Your messages"
            description="Send private, end-to-end encrypted messages (NIP-17) to anyone on Nostr."
            action={<Button className="h-11 px-6" onClick={() => setCompose(true)}>Send message</Button>}
          />
        ) : (
          <ul className="py-2">
            {messages.conversations.map((c) => (
              <li key={c.peer}>
                <Link to={`/messages/${npubOf(c.peer)}`} className="flex items-center gap-3 px-4 py-2 hover:bg-surface-muted">
                  <UserAvatar pubkey={c.peer} size={56} />
                  <div className="min-w-0 flex-1">
                    <UserName pubkey={c.peer} link={false} className={cn('text-body', !c.unread && 'font-normal')} />
                    <p className={cn('truncate text-body', c.unread ? 'font-semibold text-foreground' : 'text-muted-foreground')}>
                      {c.last.pubkey === user.pubkey ? 'You: ' : ''}
                      {c.last.content.replace(/\s+/g, ' ').slice(0, 80)} · {shortAge(c.last.created_at)}
                    </p>
                  </div>
                  {c.unread && <span className="size-2 shrink-0 rounded-full bg-accent" aria-label="Unread" />}
                </Link>
              </li>
            ))}
          </ul>
        )}
      </main>
      <NewMessageSheet open={compose} onOpenChange={setCompose} />
    </>
  );
}

function NewMessageSheet({ open, onOpenChange }: { open: boolean; onOpenChange: (o: boolean) => void }) {
  const navigate = useNavigate();
  const { user } = useCurrentUser();
  const { follows } = useFollowing(user?.pubkey);
  const [value, setValue] = useState('');
  const [error, setError] = useState('');

  const go = (pubkey: string) => {
    onOpenChange(false);
    setValue('');
    navigate(`/messages/${npubOf(pubkey)}`);
  };

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setError('');
    const input = value.trim();
    const pointer = decodePointer(input);
    if (pointer?.type === 'profile') return go(pointer.pubkey);
    if (/^[0-9a-f]{64}$/i.test(input)) return go(input.toLowerCase());
    try {
      const pk = input.includes('.') ? await resolveNip05(input) : undefined;
      if (pk) return go(pk);
    } catch {
      // fall through
    }
    setError('Enter an npub, nprofile or name@domain.');
  };

  return (
    <BottomSheet open={open} onOpenChange={onOpenChange} title="New message">
      <form onSubmit={submit} className="space-y-2 px-4 pb-3">
        <Input value={value} onChange={(e) => setValue(e.target.value)} placeholder="npub1… or name@domain" className="h-11" aria-label="Recipient" aria-invalid={!!error} />
        {error && <p className="text-meta text-destructive">{error}</p>}
        <Button type="submit" className="h-11 w-full" disabled={!value.trim()}>Chat</Button>
      </form>
      {follows.length > 0 && (
        <>
          <h3 className="px-4 pt-2 pb-1 text-meta font-semibold text-muted-foreground">Following</h3>
          <ul className="pb-4">
            {follows.slice(0, 100).map((pk) => (
              <FollowRow key={pk} pubkey={pk} onClick={() => go(pk)} />
            ))}
          </ul>
        </>
      )}
    </BottomSheet>
  );
}

function FollowRow({ pubkey, onClick }: { pubkey: string; onClick: () => void }) {
  const { data } = useAuthor(pubkey);
  return (
    <li>
      <button type="button" onClick={onClick} className="flex w-full items-center gap-3 px-4 py-2 text-left hover:bg-surface-muted">
        <UserAvatar pubkey={pubkey} size={44} />
        <span className="truncate font-semibold">{displayName(data?.metadata, pubkey)}</span>
      </button>
    </li>
  );
}
