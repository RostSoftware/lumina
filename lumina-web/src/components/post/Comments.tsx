import { forwardRef, useMemo, useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { X } from 'lucide-react';
import type { NostrEvent } from '@nostrify/nostrify';
import { UserAvatar } from '@/components/common/UserAvatar';
import { UserName } from '@/components/common/UserName';
import { RichText } from '@/components/common/RichText';
import { useAuthor } from '@/hooks/useAuthor';
import { useCurrentUser } from '@/hooks/useCurrentUser';
import { useLoginDialog } from '@/hooks/useLoginDialog';
import { useComment } from '@/hooks/usePostActions';
import { useMuteList } from '@/hooks/useMutes';
import { isMuted } from '@/lib/mute';
import { shortAge, fullDate } from '@/lib/format';
import { handleName, profilePath } from '@/lib/nostr';

interface Node {
  event: NostrEvent;
  children: Node[];
}

/** Build a reply tree from NIP-22 comments (parent = lowercase `e`). */
function buildTree(rootId: string, comments: NostrEvent[]): Node[] {
  const nodes = new Map<string, Node>();
  for (const c of comments) nodes.set(c.id, { event: c, children: [] });
  const roots: Node[] = [];
  for (const node of nodes.values()) {
    const parent = node.event.tags.find(([n]) => n === 'e')?.[1];
    const parentNode = parent && parent !== rootId ? nodes.get(parent) : undefined;
    if (parentNode) parentNode.children.push(node);
    else roots.push(node);
  }
  const sort = (list: Node[]) => {
    list.sort((a, b) => a.event.created_at - b.event.created_at);
    list.forEach((n) => sort(n.children));
  };
  sort(roots);
  return roots;
}

export function CommentList({ root, comments, onReply }: { root: NostrEvent; comments: NostrEvent[]; onReply: (c: NostrEvent) => void }) {
  const { mutes } = useMuteList();
  const tree = useMemo(() => buildTree(root.id, comments.filter((c) => !isMuted(c, mutes))), [root.id, comments, mutes]);
  if (tree.length === 0) {
    return <p className="px-4 py-8 text-center text-body text-muted-foreground">No comments yet. Start the conversation.</p>;
  }
  return (
    <ul className="space-y-4 px-3 py-4">
      {tree.map((node) => (
        <CommentItem key={node.event.id} node={node} depth={0} onReply={onReply} />
      ))}
    </ul>
  );
}

function CommentItem({ node, depth, onReply }: { node: Node; depth: number; onReply: (c: NostrEvent) => void }) {
  const [open, setOpen] = useState(depth > 0 || node.children.length <= 2);
  const { event } = node;
  return (
    <li>
      <div className="flex gap-3">
        <Link to={profilePath(event.pubkey)} aria-hidden tabIndex={-1}>
          <UserAvatar pubkey={event.pubkey} size={32} />
        </Link>
        <div className="min-w-0 flex-1">
          <p className="text-body">
            <UserName pubkey={event.pubkey} className="mr-1.5 align-top" />
            <RichText text={event.content} />
          </p>
          <div className="mt-1 flex items-center gap-4 text-meta text-muted-foreground">
            <time title={fullDate(event.created_at)}>{shortAge(event.created_at)}</time>
            <button type="button" className="min-h-6 font-semibold hover:text-foreground" onClick={() => onReply(event)}>
              Reply
            </button>
          </div>
        </div>
      </div>
      {node.children.length > 0 && (
        <div className={depth < 3 ? 'mt-3 ml-11' : 'mt-3'}>
          {open ? (
            <ul className="space-y-3">
              {node.children.map((child) => (
                <CommentItem key={child.event.id} node={child} depth={depth + 1} onReply={onReply} />
              ))}
            </ul>
          ) : (
            <button type="button" className="flex items-center gap-3 text-meta font-semibold text-muted-foreground" onClick={() => setOpen(true)}>
              <span className="h-px w-6 bg-muted-foreground" aria-hidden /> View {node.children.length} replies
            </button>
          )}
        </div>
      )}
    </li>
  );
}

interface ComposerProps {
  root: NostrEvent;
  replyTo?: NostrEvent;
  onCancelReply: () => void;
}

export const CommentComposer = forwardRef<HTMLInputElement, ComposerProps>(function CommentComposer({ root, replyTo, onCancelReply }, ref) {
  const { user } = useCurrentUser();
  const { requireLogin } = useLoginDialog();
  const comment = useComment();
  const [text, setText] = useState('');
  const replyAuthor = useAuthor(replyTo?.pubkey);

  const submit = (e: FormEvent) => {
    e.preventDefault();
    if (!requireLogin()) return;
    const content = text.trim();
    if (!content) return;
    comment.mutate(
      { root, parent: replyTo, content },
      {
        onSuccess: () => {
          setText('');
          onCancelReply();
        },
      },
    );
  };

  return (
    <div className="border-t bg-surface">
      {replyTo && (
        <div className="flex items-center justify-between bg-surface-muted px-4 py-2 text-meta text-muted-foreground">
          <span>Replying to @{handleName(replyAuthor.data?.metadata, replyTo.pubkey)}</span>
          <button type="button" aria-label="Cancel reply" onClick={onCancelReply} className="inline-flex size-6 items-center justify-center">
            <X className="size-4" />
          </button>
        </div>
      )}
      <form onSubmit={submit} className="flex items-center gap-3 px-3 py-2">
        {user && <UserAvatar pubkey={user.pubkey} size={32} />}
        <input
          ref={ref}
          value={text}
          onChange={(e) => setText(e.target.value)}
          onFocus={() => !user && requireLogin()}
          placeholder={user ? 'Add a comment…' : 'Log in to comment'}
          aria-label="Add a comment"
          maxLength={2000}
          className="h-11 min-w-0 flex-1 bg-transparent text-body outline-none placeholder:text-muted-foreground"
        />
        <button type="submit" disabled={!text.trim() || comment.isPending} className="min-h-11 px-1 text-body font-semibold text-accent disabled:opacity-40">
          {comment.isPending ? 'Posting…' : 'Post'}
        </button>
      </form>
    </div>
  );
});
