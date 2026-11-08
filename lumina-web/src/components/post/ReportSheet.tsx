import { useState } from 'react';
import type { NostrEvent } from '@nostrify/nostrify';
import { BottomSheet } from '@/components/common/BottomSheet';
import { Button } from '@/components/ui/button';
import { Textarea } from '@/components/ui/textarea';
import { useReport, type ReportType } from '@/hooks/usePostActions';
import { cn } from '@/lib/utils';

const REASONS: { type: ReportType; label: string }[] = [
  { type: 'spam', label: 'Spam' },
  { type: 'nudity', label: 'Nudity or sexual content' },
  { type: 'profanity', label: 'Hate speech or harassment' },
  { type: 'illegal', label: 'Illegal content' },
  { type: 'impersonation', label: 'Impersonation' },
  { type: 'malware', label: 'Scam or malware' },
  { type: 'other', label: 'Something else' },
];

/** NIP-56 report flow. */
export function ReportSheet({ event, pubkey, open, onOpenChange }: { event?: NostrEvent; pubkey: string; open: boolean; onOpenChange: (o: boolean) => void }) {
  const [type, setType] = useState<ReportType | null>(null);
  const [reason, setReason] = useState('');
  const report = useReport();

  const submit = () => {
    if (!type) return;
    report.mutate(
      { event, pubkey, type, reason },
      {
        onSuccess: () => {
          onOpenChange(false);
          setType(null);
          setReason('');
        },
      },
    );
  };

  return (
    <BottomSheet open={open} onOpenChange={onOpenChange} title={event ? 'Report post' : 'Report account'} description="Reports are public Nostr events (NIP-56) that relays and clients can use for moderation.">
      <div className="space-y-1 px-2 pb-2" role="radiogroup">
        {REASONS.map((r) => (
          <button
            key={r.type}
            type="button"
            role="radio"
            aria-checked={type === r.type}
            onClick={() => setType(r.type)}
            className={cn('flex min-h-12 w-full items-center justify-between rounded-lg px-3 text-left hover:bg-surface-muted', type === r.type && 'bg-surface-muted font-semibold')}
          >
            {r.label}
            <span className={cn('size-4 rounded-full border-2', type === r.type ? 'border-accent bg-accent' : 'border-border')} aria-hidden />
          </button>
        ))}
      </div>
      <div className="space-y-3 px-4 pb-4">
        <Textarea placeholder="Add details (optional)" value={reason} onChange={(e) => setReason(e.target.value)} rows={2} />
        <Button className="h-11 w-full" variant="destructive" disabled={!type || report.isPending} onClick={submit}>
          {report.isPending ? 'Sending…' : 'Submit report'}
        </Button>
      </div>
    </BottomSheet>
  );
}
