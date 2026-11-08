import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Bookmark, Code, ExternalLink, Flag, Link2, Share2, Trash2, UserX } from 'lucide-react';
import type { NostrEvent } from '@nostrify/nostrify';
import { BottomSheet, SheetAction } from '@/components/common/BottomSheet';
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from '@/components/ui/alert-dialog';
import { useCurrentUser } from '@/hooks/useCurrentUser';
import { useLoginDialog } from '@/hooks/useLoginDialog';
import { useBookmarks, useToggleBookmark } from '@/hooks/useBookmarks';
import { useMuteList, useMuteMutation } from '@/hooks/useMutes';
import { useDeleteEvent } from '@/hooks/usePostActions';
import { postPath, postShareUrl } from '@/lib/nostr';
import { ShareSheet } from './ShareSheet';
import { copyText } from '@/lib/clipboard';
import { RawEventSheet } from './RawEventSheet';
import { ReportSheet } from './ReportSheet';

/** The "…" menu of a post. */
export function PostMenuSheet({ event, open, onOpenChange, showOpen = true }: { event: NostrEvent; open: boolean; onOpenChange: (o: boolean) => void; showOpen?: boolean }) {
  const navigate = useNavigate();
  const { user } = useCurrentUser();
  const { requireLogin } = useLoginDialog();
  const { ids: bookmarks } = useBookmarks();
  const toggleBookmark = useToggleBookmark();
  const { mutes } = useMuteList();
  const mute = useMuteMutation();
  const del = useDeleteEvent();
  const [sub, setSub] = useState<'share' | 'raw' | 'report' | 'delete' | null>(null);

  const own = user?.pubkey === event.pubkey;
  const bookmarked = bookmarks.has(event.id);
  const muted = mutes.pubkeys.has(event.pubkey);

  const openSub = (s: typeof sub) => {
    onOpenChange(false);
    setTimeout(() => setSub(s), 150);
  };

  return (
    <>
      <BottomSheet open={open} onOpenChange={onOpenChange} title="Post options" hideTitle>
        <div className="pb-4">
          <SheetAction
            icon={<Bookmark fill={bookmarked ? 'currentColor' : 'none'} />}
            label={bookmarked ? 'Remove from saved' : 'Save'}
            onClick={() => {
              onOpenChange(false);
              if (requireLogin()) toggleBookmark.mutate({ event, bookmark: !bookmarked });
            }}
          />
          <SheetAction icon={<Share2 />} label="Share" onClick={() => openSub('share')} />
          <SheetAction icon={<Link2 />} label="Copy link" onClick={() => { copyText(postShareUrl(event), 'Link copied'); onOpenChange(false); }} />
          {showOpen && <SheetAction icon={<ExternalLink />} label="Go to post" onClick={() => { onOpenChange(false); navigate(postPath(event)); }} />}
          <SheetAction icon={<Code />} label="View raw event" onClick={() => openSub('raw')} />
          {!own && (
            <SheetAction
              icon={<UserX />}
              label={muted ? 'Unmute account' : 'Mute account'}
              onClick={() => {
                onOpenChange(false);
                if (requireLogin()) mute.mutate({ item: { type: 'pubkey', value: event.pubkey }, mute: !muted });
              }}
            />
          )}
          {!own && <SheetAction icon={<Flag />} label="Report" destructive onClick={() => { if (requireLogin()) openSub('report'); else onOpenChange(false); }} />}
          {own && <SheetAction icon={<Trash2 />} label="Delete" destructive onClick={() => openSub('delete')} />}
        </div>
      </BottomSheet>

      <ShareSheet event={event} open={sub === 'share'} onOpenChange={(o) => !o && setSub(null)} />
      <RawEventSheet event={event} open={sub === 'raw'} onOpenChange={(o) => !o && setSub(null)} />
      <ReportSheet event={event} pubkey={event.pubkey} open={sub === 'report'} onOpenChange={(o) => !o && setSub(null)} />
      <AlertDialog open={sub === 'delete'} onOpenChange={(o) => !o && setSub(null)}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Delete post?</AlertDialogTitle>
            <AlertDialogDescription>
              A deletion request (NIP-09) is sent to your relays. Most relays and clients will remove the post, but copies may remain elsewhere.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Cancel</AlertDialogCancel>
            <AlertDialogAction
              className="bg-destructive text-destructive-foreground hover:bg-destructive/90"
              onClick={() => del.mutate(event, { onSuccess: () => { if (location.pathname === postPath(event)) navigate(-1); } })}
            >
              Delete
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </>
  );
}
