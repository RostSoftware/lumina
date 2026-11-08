import { Copy, Link2, Share2, QrCode } from 'lucide-react';
import { useState } from 'react';
import type { NostrEvent } from '@nostrify/nostrify';
import { BottomSheet, SheetAction } from '@/components/common/BottomSheet';
import { QRCodeCanvas } from '@/components/ui/qrcode';
import { copyText } from '@/lib/clipboard';
import { neventOf, postShareUrl } from '@/lib/nostr';

/** Share a post: native share, copy web link, copy nevent, QR. */
export function ShareSheet({ event, open, onOpenChange }: { event: NostrEvent; open: boolean; onOpenChange: (o: boolean) => void }) {
  const [showQr, setShowQr] = useState(false);
  const url = postShareUrl(event);
  const canShare = typeof navigator !== 'undefined' && 'share' in navigator;

  return (
    <BottomSheet open={open} onOpenChange={(o) => { onOpenChange(o); if (!o) setShowQr(false); }} title="Share">
      {showQr ? (
        <div className="flex flex-col items-center gap-3 p-6">
          <div className="rounded-lg bg-white p-3">
            <QRCodeCanvas value={`nostr:${neventOf(event)}`} size={220} />
          </div>
          <p className="text-meta text-muted-foreground">Scan with any Nostr app</p>
        </div>
      ) : (
        <div className="pb-4">
          {canShare && (
            <SheetAction
              icon={<Share2 />}
              label="Share via…"
              onClick={() => {
                navigator.share({ url, title: 'LUMINA' }).catch(() => undefined);
                onOpenChange(false);
              }}
            />
          )}
          <SheetAction icon={<Link2 />} label="Copy link" onClick={() => { copyText(url, 'Link copied'); onOpenChange(false); }} />
          <SheetAction icon={<Copy />} label="Copy Nostr ID (nevent)" onClick={() => { copyText(`nostr:${neventOf(event)}`, 'Nostr ID copied'); onOpenChange(false); }} />
          <SheetAction icon={<QrCode />} label="Show QR code" onClick={() => setShowQr(true)} />
        </div>
      )}
    </BottomSheet>
  );
}
