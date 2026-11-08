import { useCallback, useEffect, useRef, useState, type ReactNode } from 'react';
import { ArrowLeft, ChevronRight, Copy, Download, Eye, EyeOff, Globe, KeyRound, Link2, Loader2, Smartphone, Sparkles } from 'lucide-react';
import { generateSecretKey, getPublicKey, nip19 } from 'nostr-tools';
import { Dialog, DialogContent, DialogDescription, DialogTitle } from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { QRCodeCanvas } from '@/components/ui/qrcode';
import { Wordmark } from '@/components/common/Wordmark';
import { useLoginActions, generateNostrConnectParams, generateNostrConnectURI, type NostrConnectParams, type NostrConnectStatus } from '@/hooks/useLoginActions';
import { useCurrentUser } from '@/hooks/useCurrentUser';
import { signWithFeedback, toastError } from '@/hooks/useNostrPublish';
import { useNostr } from '@nostrify/react';
import { toast } from '@/hooks/useToast';
import { cn } from '@/lib/utils';

type Step = 'choose' | 'connect' | 'bunker' | 'nsec' | 'create' | 'profile';

function isMobileDevice(): boolean {
  return typeof navigator !== 'undefined' && /Android|iPhone|iPad|iPod/i.test(navigator.userAgent);
}

function hasExtension(): boolean {
  return typeof window !== 'undefined' && 'nostr' in window && !!(window as { nostr?: unknown }).nostr;
}

interface LoginDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

/**
 * LUMINA login. Ordered to prefer external signers:
 * signer app (nostrconnect / Amber) → bunker URI → browser extension → nsec → new account.
 */
export function LoginDialog({ open, onOpenChange }: LoginDialogProps) {
  const [step, setStep] = useState<Step>('choose');
  const close = useCallback(() => onOpenChange(false), [onOpenChange]);

  useEffect(() => {
    if (!open) {
      const t = setTimeout(() => setStep('choose'), 200);
      return () => clearTimeout(t);
    }
  }, [open]);

  const titles: Record<Step, string> = {
    choose: 'Log in to LUMINA',
    connect: 'Connect a signer app',
    bunker: 'Log in with bunker',
    nsec: 'Log in with secret key',
    create: 'Create an account',
    profile: 'Set up your profile',
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[92dvh] w-[calc(100%-2rem)] max-w-sm gap-0 overflow-y-auto rounded-2xl p-0">
        <div className="flex items-center gap-2 px-4 pt-4">
          {step !== 'choose' && step !== 'profile' && (
            <button type="button" aria-label="Back" onClick={() => setStep('choose')} className="-ml-2 inline-flex size-11 items-center justify-center rounded-full hover:bg-surface-muted">
              <ArrowLeft className="size-5" />
            </button>
          )}
          <DialogTitle className="text-title">{titles[step]}</DialogTitle>
        </div>
        <DialogDescription className="sr-only">Choose how you want to sign in to LUMINA.</DialogDescription>
        <div className="p-4 pt-3">
          {step === 'choose' && <ChooseStep onSelect={setStep} onDone={close} />}
          {step === 'connect' && <ConnectStep onDone={close} />}
          {step === 'bunker' && <BunkerStep onDone={close} />}
          {step === 'nsec' && <NsecStep onDone={close} />}
          {step === 'create' && <CreateStep onCreated={() => setStep('profile')} />}
          {step === 'profile' && <ProfileStep onDone={close} />}
        </div>
      </DialogContent>
    </Dialog>
  );
}

function Option({ icon, title, description, onClick, disabled }: { icon: ReactNode; title: string; description: string; onClick: () => void; disabled?: boolean }) {
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled}
      className="flex w-full items-center gap-3 rounded-lg border p-3 text-left transition-colors hover:bg-surface-muted disabled:opacity-50"
    >
      <span className="flex size-10 shrink-0 items-center justify-center rounded-full bg-surface-muted [&_svg]:size-5">{icon}</span>
      <span className="min-w-0 flex-1">
        <span className="block font-semibold">{title}</span>
        <span className="block text-meta text-muted-foreground">{description}</span>
      </span>
      <ChevronRight className="size-4 text-muted-foreground" aria-hidden />
    </button>
  );
}

function ChooseStep({ onSelect, onDone }: { onSelect: (s: Step) => void; onDone: () => void }) {
  const login = useLoginActions();
  const [busy, setBusy] = useState(false);
  const extension = hasExtension();

  const loginExtension = async () => {
    setBusy(true);
    try {
      await login.extension();
      onDone();
    } catch (error) {
      toastError(error, 'Extension login failed');
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="space-y-4">
      <div className="py-2 text-center">
        <Wordmark />
        <p className="mt-1 text-meta text-muted-foreground">Your keys stay in your signer. LUMINA never needs your private key.</p>
      </div>
      <div className="space-y-2">
        <Option icon={<Smartphone />} title="Signer app" description="Amber, nsec.app & other NIP-46 signers — scan a QR code or open the app" onClick={() => onSelect('connect')} />
        <Option icon={<Link2 />} title="Bunker URI" description="Paste a bunker:// connection string" onClick={() => onSelect('bunker')} />
        <Option
          icon={busy ? <Loader2 className="animate-spin" /> : <Globe />}
          title="Browser extension"
          description={extension ? 'Alby, nos2x & other NIP-07 extensions' : 'No NIP-07 extension detected in this browser'}
          onClick={loginExtension}
          disabled={!extension || busy}
        />
        <Option icon={<KeyRound />} title="Secret key (nsec)" description="Stored in this browser — least secure" onClick={() => onSelect('nsec')} />
      </div>
      <div className="relative py-1 text-center text-meta text-muted-foreground">
        <span className="relative z-10 bg-surface px-2">New to Nostr?</span>
        <span className="absolute inset-x-0 top-1/2 h-px bg-border" aria-hidden />
      </div>
      <Button variant="outline" className="h-11 w-full" onClick={() => onSelect('create')}>
        <Sparkles /> Create a new account
      </Button>
    </div>
  );
}

function statusText(status: NostrConnectStatus | null): string {
  if (status === 'getting-public-key') return 'Connected — fetching your account…';
  return 'Waiting for your signer to connect…';
}

/** NIP-46 nostrconnect: LUMINA shows a nostrconnect:// link + QR for the signer. */
function ConnectStep({ onDone }: { onDone: () => void }) {
  const login = useLoginActions();
  const loginRef = useRef(login);
  const doneRef = useRef(onDone);
  useEffect(() => {
    loginRef.current = login;
    doneRef.current = onDone;
  });

  const [session, setSession] = useState<{ params: NostrConnectParams; uri: string } | null>(null);
  const [status, setStatus] = useState<NostrConnectStatus | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    const params = generateNostrConnectParams(loginRef.current.getRelayUrls());
    const uri = generateNostrConnectURI(params, {
      name: 'LUMINA',
      callback: isMobileDevice() ? `${window.location.origin}/remoteloginsuccess` : undefined,
    });
    setSession({ params, uri });
    setError(null);
    setStatus(null);
    const controller = new AbortController();
    loginRef.current
      .nostrconnect(params, controller.signal, (s) => !controller.signal.aborted && setStatus(s))
      .then(() => {
        if (!controller.signal.aborted) {
          toast({ title: 'Signed in' });
          doneRef.current();
        }
      })
      .catch((e: unknown) => {
        if (controller.signal.aborted || (e instanceof Error && e.name === 'AbortError')) return;
        setError(e instanceof Error ? e.message : 'The signer did not complete the connection.');
      });
    return () => controller.abort();
  }, [attempt]);

  if (!session) return null;

  return (
    <div className="space-y-4">
      <p className="text-body text-muted-foreground">
        Scan this code with your signer app, or open it directly on this device. With <strong className="text-foreground">Amber</strong> on Android, tap “Open signer app”.
      </p>
      <div className="mx-auto w-fit rounded-lg bg-white p-3">
        <QRCodeCanvas value={session.uri} size={220} />
      </div>
      {error ? (
        <div className="space-y-2 text-center" role="alert">
          <p className="text-body text-destructive">{error}</p>
          <Button variant="outline" className="h-11" onClick={() => setAttempt((a) => a + 1)}>
            Try again
          </Button>
        </div>
      ) : (
        <p className="flex items-center justify-center gap-2 text-meta text-muted-foreground" aria-live="polite">
          <Loader2 className="size-4 animate-spin" /> {statusText(status)}
        </p>
      )}
      <div className="grid grid-cols-2 gap-2">
        <Button className="h-11" asChild>
          <a href={session.uri}>Open signer app</a>
        </Button>
        <Button
          variant="outline"
          className="h-11"
          onClick={() => navigator.clipboard.writeText(session.uri).then(() => toast({ title: 'Link copied' }), () => undefined)}
        >
          <Copy /> Copy link
        </Button>
      </div>
    </div>
  );
}

function BunkerStep({ onDone }: { onDone: () => void }) {
  const login = useLoginActions();
  const [uri, setUri] = useState('');
  const [pending, setPending] = useState(false);
  const [error, setError] = useState('');

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    const value = uri.trim();
    if (!value.startsWith('bunker://')) {
      setError('A bunker URI starts with bunker://');
      return;
    }
    setPending(true);
    setError('');
    try {
      await Promise.race([
        login.bunker(value),
        new Promise((_, reject) => setTimeout(() => reject(new Error('The bunker did not respond in time. Is it online?')), 60_000)),
      ]);
      toast({ title: 'Signed in' });
      onDone();
    } catch (err) {
      const message = err instanceof Error ? err.message : String(err);
      setError(/reject|denied/i.test(message) ? 'The connection was rejected by your signer.' : message || 'Could not connect to the bunker.');
    } finally {
      setPending(false);
    }
  };

  return (
    <form onSubmit={submit} className="space-y-3">
      <p className="text-body text-muted-foreground">Paste the bunker:// URI from nsec.app, Amber, or your own NIP-46 signer.</p>
      <Input value={uri} onChange={(e) => setUri(e.target.value)} placeholder="bunker://…" autoComplete="off" spellCheck={false} className="h-11 font-mono" aria-invalid={!!error} aria-label="Bunker URI" />
      {error && <p className="text-meta text-destructive" role="alert">{error}</p>}
      {pending && (
        <p className="flex items-center gap-2 text-meta text-muted-foreground" aria-live="polite">
          <Loader2 className="size-4 animate-spin" /> Connecting… approve the request in your signer.
        </p>
      )}
      <Button type="submit" className="h-11 w-full" disabled={pending || !uri.trim()}>
        {pending ? 'Waiting for approval…' : 'Connect'}
      </Button>
    </form>
  );
}

function NsecStep({ onDone }: { onDone: () => void }) {
  const login = useLoginActions();
  const [nsec, setNsec] = useState('');
  const [show, setShow] = useState(false);
  const [error, setError] = useState('');

  const submit = (e: React.FormEvent) => {
    e.preventDefault();
    const value = nsec.trim();
    if (!/^nsec1[02-9ac-hj-np-z]{58}$/.test(value)) {
      setError('Enter a valid nsec1… key.');
      return;
    }
    try {
      login.nsec(value);
      setNsec('');
      toast({ title: 'Signed in' });
      onDone();
    } catch {
      setError("Couldn't log in with this key.");
    }
  };

  return (
    <form onSubmit={submit} className="space-y-3">
      <div className="rounded-lg border border-destructive/40 bg-destructive/5 p-3 text-meta">
        Your secret key will be stored <strong>unencrypted in this browser</strong>. Anyone with access to this device or a malicious script could steal it. Prefer a signer app or extension.
      </div>
      <div className="relative">
        <Input type={show ? 'text' : 'password'} value={nsec} onChange={(e) => { setNsec(e.target.value); setError(''); }} placeholder="nsec1…" autoComplete="off" spellCheck={false} className="h-11 pr-11 font-mono" aria-invalid={!!error} aria-label="Secret key" />
        <button type="button" aria-label={show ? 'Hide key' : 'Show key'} onClick={() => setShow(!show)} className="absolute inset-y-0 right-0 flex w-11 items-center justify-center text-muted-foreground">
          {show ? <EyeOff className="size-4" /> : <Eye className="size-4" />}
        </button>
      </div>
      {error && <p className="text-meta text-destructive" role="alert">{error}</p>}
      <Button type="submit" className="h-11 w-full" disabled={!nsec.trim()}>
        Log in
      </Button>
    </form>
  );
}

function downloadKey(nsec: string) {
  const decoded = nip19.decode(nsec);
  if (decoded.type !== 'nsec') return;
  const npub = nip19.npubEncode(getPublicKey(decoded.data));
  const blob = new Blob([`LUMINA / Nostr secret key — keep this private!\n\n${nsec}\n\nPublic key: ${npub}\n`], { type: 'text/plain;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = `lumina-${npub.slice(5, 13)}.nsec.txt`;
  a.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}

function CreateStep({ onCreated }: { onCreated: () => void }) {
  const login = useLoginActions();
  const [nsec] = useState(() => nip19.nsecEncode(generateSecretKey()));
  const [saved, setSaved] = useState(false);
  const [show, setShow] = useState(false);

  return (
    <div className="space-y-4">
      <p className="text-body text-muted-foreground">
        We generated a Nostr key for you. This secret key <strong className="text-foreground">is</strong> your account — there is no password reset. Save it somewhere safe, ideally in a password manager or signer app.
      </p>
      <div className="relative">
        <Input readOnly type={show ? 'text' : 'password'} value={nsec} className="h-11 pr-11 font-mono" aria-label="Your new secret key" />
        <button type="button" aria-label={show ? 'Hide key' : 'Show key'} onClick={() => setShow(!show)} className="absolute inset-y-0 right-0 flex w-11 items-center justify-center text-muted-foreground">
          {show ? <EyeOff className="size-4" /> : <Eye className="size-4" />}
        </button>
      </div>
      <div className="grid grid-cols-2 gap-2">
        <Button variant="outline" className="h-11" onClick={() => { downloadKey(nsec); setSaved(true); }}>
          <Download /> Download
        </Button>
        <Button variant="outline" className="h-11" onClick={() => navigator.clipboard.writeText(nsec).then(() => { setSaved(true); toast({ title: 'Key copied' }); }, () => undefined)}>
          <Copy /> Copy
        </Button>
      </div>
      <label className="flex items-start gap-2 text-meta">
        <input type="checkbox" checked={saved} onChange={(e) => setSaved(e.target.checked)} className="mt-0.5 size-4 accent-(--accent)" />
        I saved my secret key. I understand it's stored in this browser until I log out.
      </label>
      <Button
        className="h-11 w-full"
        disabled={!saved}
        onClick={() => {
          login.nsec(nsec);
          onCreated();
        }}
      >
        Continue
      </Button>
    </div>
  );
}

function ProfileStep({ onDone }: { onDone: () => void }) {
  const { user } = useCurrentUser();
  const { nostr } = useNostr();
  const [name, setName] = useState('');
  const [about, setAbout] = useState('');
  const [busy, setBusy] = useState(false);

  const save = async () => {
    if (!user) return onDone();
    setBusy(true);
    try {
      const metadata: Record<string, string> = {};
      if (name.trim()) {
        metadata.name = name.trim();
        metadata.display_name = name.trim();
      }
      if (about.trim()) metadata.about = about.trim();
      if (Object.keys(metadata).length) {
        const event = await signWithFeedback(user, { kind: 0, content: JSON.stringify(metadata) });
        await nostr.event(event, { signal: AbortSignal.timeout(8000) });
      }
      toast({ title: 'Welcome to LUMINA!' });
      onDone();
    } catch (error) {
      toastError(error, 'Could not save profile');
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="space-y-3">
      <Input value={name} onChange={(e) => setName(e.target.value)} placeholder="Name" className="h-11" aria-label="Name" maxLength={64} />
      <Input value={about} onChange={(e) => setAbout(e.target.value)} placeholder="Bio (optional)" className="h-11" aria-label="Bio" maxLength={300} />
      <Button className={cn('h-11 w-full')} disabled={busy} onClick={save}>
        {busy ? 'Saving…' : 'Finish'}
      </Button>
      <Button variant="ghost" className="h-11 w-full" onClick={onDone}>
        Skip for now
      </Button>
    </div>
  );
}
