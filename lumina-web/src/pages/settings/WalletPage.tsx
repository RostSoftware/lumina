import { useState, type FormEvent } from 'react';
import { useSeoMeta } from '@unhead/react';
import { Wallet, Zap } from 'lucide-react';
import { TopBar } from '@/components/layout/TopBar';
import { SettingsSection } from '@/components/settings/SettingsRow';
import { Spinner } from '@/components/common/States';
import { ZAP_PRESETS } from '@/lib/zapReceipt';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { useWallet } from '@/hooks/useWallet';
import { useCurrentUser } from '@/hooks/useCurrentUser';
import { toast } from '@/hooks/useToast';
import { formatSats } from '@/lib/format';
import { cn } from '@/lib/utils';
import MyProfileRedirect from '../MyProfileRedirect';

export default function WalletPage() {
  useSeoMeta({ title: 'Wallet · LUMINA' });
  const { user } = useCurrentUser();
  const { wallet, info, connect, disconnect, defaultZap, setDefaultZap, webln } = useWallet();
  const [uri, setUri] = useState('');
  const [custom, setCustom] = useState('');

  if (!user) return <MyProfileRedirect />;

  const submit = (e: FormEvent) => {
    e.preventDefault();
    connect.mutate(uri, {
      onSuccess: () => {
        setUri('');
        toast({ title: 'Wallet connected' });
      },
      onError: (error) => toast({ title: 'Could not connect wallet', description: error.message, variant: 'destructive' }),
    });
  };

  return (
    <>
      <TopBar back="/settings" title="Wallet" />
      <main className="column pb-12">
        <SettingsSection title="Nostr Wallet Connect (NIP-47)" description="Connect a wallet such as Alby Hub, Primal, Coinos or Mutiny to zap with one tap. The connection secret is stored only in this browser for this account and is deleted when you log out.">
          <div className="space-y-3 px-4 pb-3">
            {wallet ? (
              <div className="flex items-center gap-3 rounded-lg border p-3">
                <span className="flex size-10 items-center justify-center rounded-full bg-surface-muted"><Wallet className="size-5" /></span>
                <div className="min-w-0 flex-1">
                  <p className="truncate font-semibold">{info.data?.alias || wallet.alias || 'Lightning wallet'}</p>
                  <p className="text-meta text-muted-foreground">
                    {info.isLoading ? 'Checking connection…' : info.isError ? 'Wallet not responding' : info.data?.balanceSats !== undefined ? `${formatSats(info.data.balanceSats)} sats` : 'Connected'}
                  </p>
                </div>
                <Button variant="outline" className="h-9 text-destructive" onClick={() => { disconnect(); toast({ title: 'Wallet disconnected' }); }}>
                  Disconnect
                </Button>
              </div>
            ) : (
              <form onSubmit={submit} className="space-y-2">
                <Input type="password" value={uri} onChange={(e) => setUri(e.target.value)} placeholder="nostr+walletconnect://…" autoComplete="off" className="h-11 font-mono" aria-label="Wallet connection string" />
                <Button type="submit" className="h-11 w-full" disabled={!uri.trim() || connect.isPending}>
                  {connect.isPending ? <><Spinner className="size-4 border-on-primary/30 border-t-on-primary" /> Connecting…</> : 'Connect wallet'}
                </Button>
              </form>
            )}
            {!wallet && webln && <p className="text-meta text-muted-foreground">A browser wallet (WebLN) was detected and will be used when no NWC wallet is connected.</p>}
          </div>
        </SettingsSection>
        <SettingsSection title="Default zap amount">
          <div className="space-y-3 px-4 pb-3">
            <div className="grid grid-cols-3 gap-2">
              {ZAP_PRESETS.map((p) => (
                <button
                  key={p}
                  type="button"
                  aria-pressed={defaultZap === p}
                  onClick={() => setDefaultZap(p)}
                  className={cn('flex h-12 items-center justify-center gap-1 rounded-lg border font-semibold', defaultZap === p ? 'border-zap bg-zap/10' : 'hover:bg-surface-muted')}
                >
                  <Zap className="size-4 fill-zap text-zap" aria-hidden /> {formatSats(p)}
                </button>
              ))}
            </div>
            <form onSubmit={(e) => { e.preventDefault(); const n = Number(custom); if (n >= 1) { setDefaultZap(n); setCustom(''); toast({ title: `Default zap: ${formatSats(n)} sats` }); } }} className="flex gap-2">
              <Input inputMode="numeric" value={custom} onChange={(e) => setCustom(e.target.value.replace(/\D/g, '').slice(0, 9))} placeholder={`Custom (current: ${formatSats(defaultZap)})`} className="h-11" aria-label="Custom default zap amount" />
              <Button type="submit" variant="outline" className="h-11" disabled={!custom}>Set</Button>
            </form>
          </div>
        </SettingsSection>
      </main>
    </>
  );
}
