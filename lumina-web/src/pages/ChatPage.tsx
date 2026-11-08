import { useEffect, useLayoutEffect, useMemo, useRef, useState, type FormEvent } from 'react';
import { Link, useParams } from 'react-router-dom';
import { useSeoMeta } from '@unhead/react';
import { AlertCircle, Lock, RotateCcw, SendHorizontal } from 'lucide-react';
import { TopBar } from '@/components/layout/TopBar';
import { UserAvatar } from '@/components/common/UserAvatar';
import { UserName } from '@/components/common/UserName';
import { RichText } from '@/components/common/RichText';
import { Spinner } from '@/components/common/States';
import { useCurrentUser } from '@/hooks/useCurrentUser';
import { useMarkConversationRead, useMessages, useSendMessage } from '@/hooks/useMessages';
import { useAuthor } from '@/hooks/useAuthor';
import { classifySignerError, signerFailureTitle } from '@/lib/signer';
import { clockTime } from '@/lib/format';
import { decodePointer, displayName, profilePath } from '@/lib/nostr';
import { cn } from '@/lib/utils';
import NotFound from './NotFound';
import MyProfileRedirect from './MyProfileRedirect';

interface Pending {
  key: string;
  content: string;
  created_at: number;
  status: 'sending' | 'failed';
  error?: string;
}

export default function ChatPage() {
  const { npub } = useParams<{ npub: string }>();
  const pointer = decodePointer(npub ?? '');
  const { user } = useCurrentUser();
  if (!user) return <MyProfileRedirect />;
  if (pointer?.type !== 'profile') return <NotFound />;
  return <Chat peer={pointer.pubkey} me={user.pubkey} />;
}

function Chat({ peer, me }: { peer: string; me: string }) {
  const { data: author } = useAuthor(peer);
  useSeoMeta({ title: `${displayName(author?.metadata, peer)} · Messages` });
  const { conversations, isLoading } = useMessages();
  const send = useSendMessage();
  const markRead = useMarkConversationRead();
  const [text, setText] = useState('');
  const [pending, setPending] = useState<Pending[]>([]);
  const bottom = useRef<HTMLDivElement>(null);

  const messages = useMemo(() => conversations.find((c) => c.peer === peer)?.messages ?? [], [conversations, peer]);
  const lastAt = messages[messages.length - 1]?.created_at ?? 0;

  useEffect(() => {
    if (lastAt) markRead(peer, lastAt);
  }, [lastAt, peer, markRead]);

  useLayoutEffect(() => {
    bottom.current?.scrollIntoView({ block: 'end' });
  }, [messages.length, pending.length]);

  const deliver = (item: Pending) => {
    setPending((p) => [...p.filter((x) => x.key !== item.key), { ...item, status: 'sending', error: undefined }]);
    send.mutate(
      { peer, content: item.content },
      {
        onSuccess: () => setPending((p) => p.filter((x) => x.key !== item.key)),
        onError: (error) => {
          const e = classifySignerError(error);
          setPending((p) => p.map((x) => (x.key === item.key ? { ...x, status: 'failed', error: e.reason === 'unknown' ? e.message : signerFailureTitle(e.reason) } : x)));
        },
      },
    );
  };

  const submit = (e: FormEvent) => {
    e.preventDefault();
    const content = text.trim();
    if (!content) return;
    setText('');
    deliver({ key: crypto.randomUUID(), content, created_at: Math.floor(Date.now() / 1000), status: 'sending' });
  };

  return (
    <div className="flex min-h-dvh flex-col">
      <TopBar
        back="/messages"
        left={
          <Link to={profilePath(peer)} className="flex min-w-0 items-center gap-2">
            <UserAvatar pubkey={peer} size={32} />
            <UserName pubkey={peer} variant="display" link={false} showBadge className="text-title" />
          </Link>
        }
      />
      <main className="column flex flex-1 flex-col px-3 pt-4">
        <div className="mb-4 flex flex-col items-center gap-2 text-center">
          <UserAvatar pubkey={peer} size={86} />
          <p className="flex items-center gap-1 text-meta text-muted-foreground">
            <Lock className="size-3" aria-hidden /> Messages are end-to-end encrypted (NIP-17)
          </p>
        </div>
        {isLoading && (
          <div className="flex justify-center py-6"><Spinner /></div>
        )}
        <ol className="flex flex-1 flex-col gap-1">
          {messages.map((m, i) => {
            const mine = m.pubkey === me;
            const prev = messages[i - 1];
            const showTime = !prev || m.created_at - prev.created_at > 15 * 60;
            return (
              <li key={`${m.protocol}:${m.id}`} className="flex flex-col">
                {showTime && <time className="my-2 text-center text-meta text-muted-foreground">{new Date(m.created_at * 1000).toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' })}</time>}
                <Bubble mine={mine} content={m.content} time={clockTime(m.created_at)} legacy={m.protocol === 'nip04'} />
              </li>
            );
          })}
          {pending.map((p) => (
            <li key={p.key} className="flex flex-col items-end">
              <Bubble mine content={p.content} time={p.status === 'sending' ? 'Sending…' : 'Not sent'} faded={p.status === 'sending'} />
              {p.status === 'failed' && (
                <button type="button" onClick={() => deliver(p)} className="mt-1 flex items-center gap-1 text-meta text-destructive">
                  <AlertCircle className="size-3.5" /> {p.error} · <RotateCcw className="size-3.5" /> Retry
                </button>
              )}
            </li>
          ))}
        </ol>
        <div ref={bottom} className="h-4" />
      </main>
      <form onSubmit={submit} className="sticky bottom-0 border-t bg-surface pb-[var(--safe-bottom)]">
        <div className="column flex items-end gap-2 p-2">
          <textarea
            value={text}
            onChange={(e) => setText(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter' && !e.shiftKey) {
                e.preventDefault();
                e.currentTarget.form?.requestSubmit();
              }
            }}
            rows={1}
            placeholder="Message…"
            aria-label="Message"
            className="max-h-32 min-h-11 flex-1 resize-none rounded-[22px] border bg-surface-muted px-4 py-2.5 text-body outline-none focus-visible:ring-2 focus-visible:ring-accent"
          />
          <button type="submit" aria-label="Send" disabled={!text.trim()} className="flex size-11 items-center justify-center rounded-full bg-accent text-accent-foreground disabled:opacity-40">
            <SendHorizontal className="size-5" />
          </button>
        </div>
      </form>
    </div>
  );
}

function Bubble({ mine, content, time, faded, legacy }: { mine: boolean; content: string; time: string; faded?: boolean; legacy?: boolean }) {
  return (
    <div className={cn('flex', mine ? 'justify-end' : 'justify-start')}>
      <div
        className={cn(
          'max-w-[78%] rounded-[20px] px-3.5 py-2 text-body',
          mine ? 'bg-accent text-accent-foreground' : 'bg-surface-muted text-foreground',
          faded && 'opacity-60',
        )}
      >
        <RichText text={content} className={mine ? '[&_a]:text-accent-foreground [&_a]:underline' : undefined} />
        <span className={cn('mt-0.5 block text-right text-[0.6875rem]', mine ? 'opacity-80' : 'text-muted-foreground')}>
          {legacy && 'NIP-04 · '}
          {time}
        </span>
      </div>
    </div>
  );
}
