import type { NostrEvent } from '@nostrify/nostrify';
import { BottomSheet } from '@/components/common/BottomSheet';
import { Button } from '@/components/ui/button';
import { copyText } from '@/lib/clipboard';

/** Shows the raw JSON of an event (as plain text — never as HTML). */
export function RawEventSheet({ event, open, onOpenChange }: { event: NostrEvent; open: boolean; onOpenChange: (o: boolean) => void }) {
  const json = JSON.stringify(event, null, 2);
  return (
    <BottomSheet open={open} onOpenChange={onOpenChange} title="Raw event">
      <div className="space-y-3 p-4">
        <pre className="max-h-[55dvh] overflow-auto rounded-lg bg-surface-muted p-3 font-mono text-meta whitespace-pre-wrap break-all">{json}</pre>
        <Button variant="outline" className="h-11 w-full" onClick={() => copyText(json, 'Event JSON copied')}>
          Copy JSON
        </Button>
      </div>
    </BottomSheet>
  );
}
