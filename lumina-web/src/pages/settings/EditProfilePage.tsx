import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useSeoMeta } from '@unhead/react';
import { useNostr } from '@nostrify/react';
import { useQueryClient } from '@tanstack/react-query';
import { Camera, Loader2 } from 'lucide-react';
import { TopBar } from '@/components/layout/TopBar';
import { UserAvatar } from '@/components/common/UserAvatar';
import { ErrorState } from '@/components/common/States';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Textarea } from '@/components/ui/textarea';
import { Skeleton } from '@/components/ui/skeleton';
import { useAppContext } from '@/hooks/useAppContext';
import { useCurrentUser } from '@/hooks/useCurrentUser';
import { signWithFeedback, toastError } from '@/hooks/useNostrPublish';
import { toast } from '@/hooks/useToast';
import { getEffectiveBlossomServers } from '@/lib/appBlossom';
import { uploadToBlossom } from '@/lib/blossom';
import { processImage } from '@/lib/image';
import { newest, npubOf } from '@/lib/nostr';
import { sanitizeUrl } from '@/lib/sanitize';
import MyProfileRedirect from '../MyProfileRedirect';

const FIELDS = ['name', 'display_name', 'about', 'picture', 'banner', 'website', 'nip05', 'lud16'] as const;
type Field = (typeof FIELDS)[number];

export default function EditProfilePage() {
  useSeoMeta({ title: 'Edit profile · LUMINA' });
  const { user } = useCurrentUser();
  const { nostr } = useNostr();
  const { config } = useAppContext();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [base, setBase] = useState<Record<string, unknown> | null>(null);
  const [form, setForm] = useState<Record<Field, string>>(() => Object.fromEntries(FIELDS.map((f) => [f, ''])) as Record<Field, string>);
  const [loadError, setLoadError] = useState<unknown>(null);
  const [saving, setSaving] = useState(false);
  const [uploading, setUploading] = useState<'picture' | 'banner' | null>(null);
  const avatarInput = useRef<HTMLInputElement>(null);
  const bannerInput = useRef<HTMLInputElement>(null);
  const [attempt, setAttempt] = useState(0);

  // Always start from the latest kind 0 on relays so unknown fields are preserved.
  useEffect(() => {
    if (!user) return;
    let cancelled = false;
    nostr
      .query([{ kinds: [0], authors: [user.pubkey], limit: 1 }], { signal: AbortSignal.timeout(6000) })
      .then((events) => {
        if (cancelled) return;
        let parsed: Record<string, unknown> = {};
        try {
          const content = newest(events)?.content;
          parsed = content ? (JSON.parse(content) as Record<string, unknown>) : {};
        } catch {
          parsed = {};
        }
        setBase(parsed);
        setForm(Object.fromEntries(FIELDS.map((f) => [f, typeof parsed[f] === 'string' ? (parsed[f] as string) : ''])) as Record<Field, string>);
      })
      .catch((e) => !cancelled && setLoadError(e));
    return () => {
      cancelled = true;
    };
  }, [user, nostr, attempt]);

  if (!user) return <MyProfileRedirect />;

  const upload = async (field: 'picture' | 'banner', file: File | undefined) => {
    if (!file) return;
    setUploading(field);
    try {
      const processed = await processImage(file);
      const { descriptor } = await uploadToBlossom({
        servers: getEffectiveBlossomServers(config.blossomServerMetadata, config.useAppBlossomServers),
        signer: user.signer,
        blob: processed.blob,
        mime: processed.mime,
        hash: processed.sha256,
      });
      setForm((f) => ({ ...f, [field]: descriptor.url }));
    } catch (error) {
      toastError(error, 'Upload failed');
    } finally {
      setUploading(null);
    }
  };

  const save = async () => {
    for (const f of ['picture', 'banner'] as const) {
      if (form[f] && !sanitizeUrl(form[f])) {
        toast({ title: `The ${f} must be an https:// URL`, variant: 'destructive' });
        return;
      }
    }
    setSaving(true);
    try {
      const metadata: Record<string, unknown> = { ...(base ?? {}) };
      for (const f of FIELDS) {
        const v = form[f].trim();
        if (v) metadata[f] = v;
        else delete metadata[f];
      }
      const event = await signWithFeedback(user, { kind: 0, content: JSON.stringify(metadata) });
      await nostr.event(event, { signal: AbortSignal.timeout(8000) });
      queryClient.invalidateQueries({ queryKey: ['nostr', 'author', user.pubkey] });
      toast({ title: 'Profile saved' });
      navigate(`/${npubOf(user.pubkey)}`);
    } catch (error) {
      toastError(error, 'Could not save profile');
    } finally {
      setSaving(false);
    }
  };

  const field = (name: Field, label: string, opts: { placeholder?: string; multiline?: boolean; hint?: string } = {}) => (
    <label className="block space-y-1">
      <span className="text-meta font-semibold">{label}</span>
      {opts.multiline ? (
        <Textarea value={form[name]} onChange={(e) => setForm({ ...form, [name]: e.target.value })} placeholder={opts.placeholder} rows={4} maxLength={1000} />
      ) : (
        <Input value={form[name]} onChange={(e) => setForm({ ...form, [name]: e.target.value })} placeholder={opts.placeholder} className="h-11" />
      )}
      {opts.hint && <span className="block text-meta text-muted-foreground">{opts.hint}</span>}
    </label>
  );

  const banner = sanitizeUrl(form.banner);

  return (
    <>
      <TopBar
        back
        title="Edit profile"
        right={
          <Button variant="link" className="h-11 font-semibold" disabled={!base || saving || !!uploading} onClick={save}>
            {saving ? 'Saving…' : 'Done'}
          </Button>
        }
      />
      <main className="column pb-12">
        {loadError ? (
          <ErrorState error={loadError} onRetry={() => { setLoadError(null); setAttempt((a) => a + 1); }} />
        ) : !base ? (
          <div className="space-y-4 p-4">
            <Skeleton className="mx-auto size-[86px] rounded-full" />
            {[0, 1, 2, 3].map((i) => <Skeleton key={i} className="h-11 w-full" />)}
          </div>
        ) : (
          <>
            <div className="relative">
              <button type="button" onClick={() => bannerInput.current?.click()} className="relative block aspect-[3/1] w-full overflow-hidden bg-surface-muted" aria-label="Change banner">
                {banner && <img src={banner} alt="" className="size-full object-cover" referrerPolicy="no-referrer" />}
                <span className="absolute right-3 bottom-3 flex items-center gap-1 rounded-full bg-black/60 px-3 py-1 text-meta text-white">
                  {uploading === 'banner' ? <Loader2 className="size-3.5 animate-spin" /> : <Camera className="size-3.5" />} Banner
                </span>
              </button>
              <div className="-mt-11 flex flex-col items-center gap-2">
                <button type="button" onClick={() => avatarInput.current?.click()} className="relative rounded-full ring-4 ring-background" aria-label="Change profile photo">
                  <UserAvatar pubkey={user.pubkey} size={86} src={sanitizeUrl(form.picture)} />
                  {uploading === 'picture' && <span className="absolute inset-0 flex items-center justify-center rounded-full bg-black/40"><Loader2 className="size-6 animate-spin text-white" /></span>}
                </button>
                <button type="button" onClick={() => avatarInput.current?.click()} className="text-body font-semibold text-accent">Change photo</button>
              </div>
              <input ref={avatarInput} type="file" accept="image/*" className="hidden" onChange={(e) => { upload('picture', e.target.files?.[0]); e.target.value = ''; }} />
              <input ref={bannerInput} type="file" accept="image/*" className="hidden" onChange={(e) => { upload('banner', e.target.files?.[0]); e.target.value = ''; }} />
            </div>
            <div className="space-y-4 p-4">
              {field('display_name', 'Name', { placeholder: 'Your name' })}
              {field('name', 'Username', { placeholder: 'username' })}
              {field('about', 'Bio', { multiline: true, placeholder: 'Tell people about yourself. #hashtags and links work.' })}
              {field('website', 'Website', { placeholder: 'https://…' })}
              {field('nip05', 'Nostr address (NIP-05)', { placeholder: 'you@example.com', hint: 'Shows a verified badge when your domain confirms it.' })}
              {field('lud16', 'Lightning address', { placeholder: 'you@wallet.com', hint: 'Lets people zap you.' })}
              {field('picture', 'Profile photo URL', { placeholder: 'https://…' })}
              {field('banner', 'Banner URL', { placeholder: 'https://…' })}
            </div>
          </>
        )}
      </main>
    </>
  );
}
