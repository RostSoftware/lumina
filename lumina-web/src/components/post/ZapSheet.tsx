import { useState } from 'react';
import { Link } from 'react-router-dom';
import { Copy, ExternalLink, Wallet, Zap } from 'lucide-react';
import type { NostrEvent } from '@nostrify/nostrify';
import { BottomSheet } from '@/components/common/BottomSheet';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { QRCodeCanvas } from '@/components/ui/qrcode';
import { useAuthor } from '@/hooks/useAuthor';
import { useWallet } from '@/hooks/useWallet';
import { useZap } from '@/hooks/useZap';
import { toastError } from '@/hooks/useNostrPublish';
import { toast } from '@/hooks/useToast';
import { displayName } from '@/lib/nostr';
import { formatSats } from '@/lib/format';
import { cn } from '@/lib/utils';
import { ZAP_PRESETS } from '@/lib/zapReceipt';
import { copyText } from '@/lib/clipboard';


interface ZapSheetProps {
  recipient: string;
  event?: NostrEvent;
  open: boolean;
  onOpenChange: (o: boolean) => void;
}

/** Preset-amount zap sheet (NIP-57 via NWC, WebLN or invoice QR). */
export function ZapSheet({ recipient, event, open, onOpenChange }: ZapSheetProps) {
  const { data: author } = useAuthor(recipient);
  const { wallet, defaultZap, webln } = useWallet();
  const zap = useZap();
  const [amount, setAmount] = useState<number>(defaultZap);
  const [custom, setCustom] = useState('');
  const [comment, setComment] = useState('');
  const [invoice, setInvoice] = useState<string | null>(null);

  const name = displayName(author?.metadata, recipient);
  const canZap = !!(author?.metadata?.lud16 || author?.metadata?.lud06);
  const sats = custom ? Number(custom) : amount;

  const close = (o: boolean) => {
    onOpenChange(o);
    if (!o) {
      setInvoice(null);
      setComment('');
      setCustom('');
      zap.reset();
    }
  };

  const send = () => {
    zap.mutate(
      { recipient, metadata: author?.metadata, event, sats, comment },
      {
        onSuccess: (result) => {
          if (result.status === 'paid') {
            toast({ title: `⚡ Zapped ${formatSats(sats)} sats`, description: `to ${name}` });
            close(false);
          } else {
            setInvoice(result.invoice);
          }
        },
        onError: (error) => toastError(error, 'Zap failed'),
      },
    );
  };

  return (
    <BottomSheet open={open} onOpenChange={close} title={`Zap ${name}`}>
      {invoice ? (
        <div className="flex flex-col items-center gap-4 p-4 pb-6">
          <p className="text-center text-body text-muted-foreground">Scan or open this invoice in any Lightning wallet to send {formatSats(sats)} sats.</p>
          <a href={`lightning:${invoice}`} className="rounded-lg bg-white p-3" aria-label="Open invoice in wallet">
            <QRCodeCanvas value={`lightning:${invoice}`.toUpperCase()} size={232} />
          </a>
          <div className="grid w-full grid-cols-2 gap-2">
            <Button variant="outline" className="h-11" onClick={() => copyText(invoice, 'Invoice copied')}>
              <Copy /> Copy
            </Button>
            <Button className="h-11" asChild>
              <a href={`lightning:${invoice}`}>
                <ExternalLink /> Open wallet
              </a>
            </Button>
          </div>
          <Button variant="ghost" className="h-11" onClick={() => close(false)}>
            Done
          </Button>
        </div>
      ) : !canZap ? (
        <div className="p-6 pb-8 text-center text-body text-muted-foreground">{author ? `${name} hasn't set up a lightning address yet.` : 'Loading…'}</div>
      ) : (
        <div className="space-y-4 p-4 pb-6">
          <div className="grid grid-cols-3 gap-2" role="radiogroup" aria-label="Amount">
            {ZAP_PRESETS.map((preset) => (
              <button
                key={preset}
                type="button"
                role="radio"
                aria-checked={!custom && amount === preset}
                onClick={() => {
                  setAmount(preset);
                  setCustom('');
                }}
                className={cn(
                  'flex h-14 flex-col items-center justify-center rounded-lg border text-body font-semibold transition-colors',
                  !custom && amount === preset ? 'border-zap bg-zap/10 text-foreground' : 'hover:bg-surface-muted',
                )}
              >
                <span className="flex items-center gap-1">
                  <Zap className="size-4 fill-zap text-zap" aria-hidden />
                  {formatSats(preset)}
                </span>
              </button>
            ))}
          </div>
          <Input inputMode="numeric" placeholder="Custom amount (sats)" value={custom} onChange={(e) => setCustom(e.target.value.replace(/\D/g, '').slice(0, 9))} className="h-11" aria-label="Custom amount in sats" />
          <Input placeholder="Add a comment (optional)" value={comment} onChange={(e) => setComment(e.target.value.slice(0, 200))} className="h-11" aria-label="Zap comment" />
          <div className="flex items-center gap-2 text-meta text-muted-foreground">
            <Wallet className="size-4" aria-hidden />
            {wallet ? (
              <span>Paying with {wallet.alias ?? 'your NWC wallet'}</span>
            ) : webln ? (
              <span>Paying with your browser wallet (WebLN)</span>
            ) : (
              <span>
                No wallet connected — you'll get an invoice.{' '}
                <Link to="/settings/wallet" className="text-accent hover:underline" onClick={() => close(false)}>
                  Connect a wallet
                </Link>
              </span>
            )}
          </div>
          <Button className="h-12 w-full text-body" disabled={!sats || sats < 1 || zap.isPending} onClick={send}>
            {zap.isPending ? 'Zapping…' : `Zap ${sats ? formatSats(sats) : ''} sats`}
          </Button>
        </div>
      )}
    </BottomSheet>
  );
}
