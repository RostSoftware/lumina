import { useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useSeoMeta } from '@unhead/react';
import { useNostr } from '@nostrify/react';
import { useQueryClient } from '@tanstack/react-query';
import { AlertCircle, Camera, CheckCircle2, ChevronLeft, ChevronRight, ImagePlus, LocateFixed, MapPin, Plus, RotateCcw, Trash2, UserPlus, X } from 'lucide-react';
import { TopBar } from '@/components/layout/TopBar';
import { CropEditor } from '@/components/create/CropEditor';
import { ASPECTS, type CropState } from '@/lib/crop';
import { UserAvatar } from '@/components/common/UserAvatar';
import { UserName } from '@/components/common/UserName';
import { Spinner } from '@/components/common/States';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Textarea } from '@/components/ui/textarea';
import { Switch } from '@/components/ui/switch';
import { Slider } from '@/components/ui/slider';
import { Progress } from '@/components/ui/progress';
import { useAppContext } from '@/hooks/useAppContext';
import { useCurrentUser } from '@/hooks/useCurrentUser';
import { signWithFeedback, toastError } from '@/hooks/useNostrPublish';
import { resolveNip05 } from '@/hooks/useNip05';
import { toast } from '@/hooks/useToast';
import { getEffectiveBlossomServers } from '@/lib/appBlossom';
import { uploadToBlossom } from '@/lib/blossom';
import { computeCrop, imageSize, processImage } from '@/lib/image';
import { encodeGeohash } from '@/lib/geohash';
import { decodePointer, extractHashtags, KIND_PICTURE, neventOf } from '@/lib/nostr';
import { cn } from '@/lib/utils';
import MyProfileRedirect from './MyProfileRedirect';

const MAX_IMAGES = 10;
const MAX_BYTES = 50 * 1024 * 1024;

interface UploadState {
  status: 'idle' | 'processing' | 'uploading' | 'done' | 'error';
  progress: number;
  key?: string;
  imeta?: string[];
  meta?: { url: string; mime: string; hash: string };
  error?: string;
}

interface DraftImage {
  id: string;
  file: File;
  preview: string;
  width: number;
  height: number;
  crop: CropState;
  alt: string;
  upload: UploadState;
}

function cropKey(img: DraftImage): string {
  const r = computeCrop(img.width, img.height, img.crop.aspect, img.crop.zoom, img.crop.panX, img.crop.panY);
  return `${r.x},${r.y},${r.width},${r.height}`;
}

export default function CreatePage() {
  useSeoMeta({ title: 'New post · LUMINA' });
  const { user } = useCurrentUser();
  if (!user) return <MyProfileRedirect />;
  return <Composer />;
}

function Composer() {
  const navigate = useNavigate();
  const { nostr } = useNostr();
  const queryClient = useQueryClient();
  const { user } = useCurrentUser();
  const { config } = useAppContext();
  const [step, setStep] = useState<'select' | 'details'>('select');
  const [images, setImages] = useState<DraftImage[]>([]);
  const [current, setCurrent] = useState(0);
  const [caption, setCaption] = useState('');
  const [extraTags, setExtraTags] = useState<string[]>([]);
  const [tagInput, setTagInput] = useState('');
  const [location, setLocation] = useState('');
  const [geohash, setGeohash] = useState<string | undefined>();
  const [people, setPeople] = useState<string[]>([]);
  const [personInput, setPersonInput] = useState('');
  const [cw, setCw] = useState(false);
  const [cwReason, setCwReason] = useState('');
  const [publishing, setPublishing] = useState(false);
  const fileInput = useRef<HTMLInputElement>(null);
  const cameraInput = useRef<HTMLInputElement>(null);
  const controllers = useRef(new Map<string, AbortController>());
  const imagesRef = useRef(images);
  useEffect(() => {
    imagesRef.current = images;
  });

  // Revoke object URLs and abort uploads on unmount.
  useEffect(() => {
    const ctrls = controllers.current;
    return () => {
      for (const img of imagesRef.current) URL.revokeObjectURL(img.preview);
      for (const c of ctrls.values()) c.abort();
    };
  }, []);

  const servers = useMemo(() => getEffectiveBlossomServers(config.blossomServerMetadata, config.useAppBlossomServers), [config.blossomServerMetadata, config.useAppBlossomServers]);

  const addFiles = async (files: FileList | null) => {
    if (!files) return;
    const list = [...files].slice(0, MAX_IMAGES - images.length);
    if (files.length > list.length) toast({ title: `You can add up to ${MAX_IMAGES} photos` });
    const added: DraftImage[] = [];
    for (const file of list) {
      if (!file.type.startsWith('image/')) {
        toast({ title: `${file.name} is not an image`, variant: 'destructive' });
        continue;
      }
      if (file.size > MAX_BYTES) {
        toast({ title: `${file.name} is too large (max 50 MB)`, variant: 'destructive' });
        continue;
      }
      try {
        const size = await imageSize(file);
        added.push({
          id: crypto.randomUUID(),
          file,
          preview: URL.createObjectURL(file),
          ...size,
          crop: { aspect: images.length === 0 && added.length === 0 ? null : images[0]?.crop.aspect ?? null, zoom: 1, panX: 0, panY: 0 },
          alt: '',
          upload: { status: 'idle', progress: 0 },
        });
      } catch {
        toast({ title: `Can't read ${file.name}`, description: 'This image format is not supported by your browser.', variant: 'destructive' });
      }
    }
    if (added.length) {
      setImages((prev) => [...prev, ...added]);
      setCurrent(images.length);
    }
  };

  const update = (id: string, patch: Partial<DraftImage> | ((img: DraftImage) => Partial<DraftImage>)) => {
    setImages((prev) => prev.map((img) => (img.id === id ? { ...img, ...(typeof patch === 'function' ? patch(img) : patch) } : img)));
  };

  const remove = (id: string) => {
    const img = images.find((i) => i.id === id);
    if (img) URL.revokeObjectURL(img.preview);
    controllers.current.get(id)?.abort();
    setImages((prev) => prev.filter((i) => i.id !== id));
    setCurrent((c) => Math.max(0, Math.min(c, images.length - 2)));
  };

  const move = (index: number, delta: number) => {
    const target = index + delta;
    if (target < 0 || target >= images.length) return;
    setImages((prev) => {
      const next = [...prev];
      [next[index], next[target]] = [next[target], next[index]];
      return next;
    });
    setCurrent(target);
  };

  const startUpload = async (img: DraftImage) => {
    if (!user) return;
    const key = cropKey(img);
    controllers.current.get(img.id)?.abort();
    const controller = new AbortController();
    controllers.current.set(img.id, controller);
    update(img.id, { upload: { status: 'processing', progress: 0, key } });
    try {
      const crop = computeCrop(img.width, img.height, img.crop.aspect, img.crop.zoom, img.crop.panX, img.crop.panY);
      const processed = await processImage(img.file, crop);
      if (controller.signal.aborted) return;
      update(img.id, { upload: { status: 'uploading', progress: 0, key } });
      const { descriptor } = await uploadToBlossom({
        servers,
        signer: user.signer,
        blob: processed.blob,
        mime: processed.mime,
        hash: processed.sha256,
        signal: controller.signal,
        onProgress: (p) => update(img.id, (cur) => (cur.upload.key === key ? { upload: { ...cur.upload, progress: p } } : {})),
      });
      const imeta = [
        `url ${descriptor.url}`,
        `m ${processed.mime}`,
        `dim ${processed.width}x${processed.height}`,
        `x ${processed.sha256}`,
        `size ${processed.blob.size}`,
        ...(processed.blurhash ? [`blurhash ${processed.blurhash}`] : []),
      ];
      update(img.id, (cur) =>
        cur.upload.key === key ? { upload: { status: 'done', progress: 1, key, imeta, meta: { url: descriptor.url, mime: processed.mime, hash: processed.sha256 } } } : {},
      );
    } catch (error) {
      if (controller.signal.aborted || (error instanceof DOMException && error.name === 'AbortError')) return;
      update(img.id, { upload: { status: 'error', progress: 0, key, error: error instanceof Error ? error.message : String(error) } });
    }
  };

  /** Go to the details step and start (or restart after a crop change) uploads. */
  const goToDetails = () => {
    setStep('details');
    for (const img of images) {
      if (img.upload.key !== cropKey(img) || img.upload.status === 'error') startUpload(img);
    }
  };

  const hashtags = useMemo(() => [...new Set([...extractHashtags(caption), ...extraTags])], [caption, extraTags]);
  const allDone = images.length > 0 && images.every((i) => i.upload.status === 'done' && i.upload.key === cropKey(i));

  const addTag = () => {
    const t = tagInput.trim().replace(/^#/, '').toLowerCase();
    if (t && /^[\p{L}\p{N}_]{1,64}$/u.test(t) && !extraTags.includes(t)) setExtraTags([...extraTags, t]);
    setTagInput('');
  };

  const addPerson = async () => {
    const value = personInput.trim();
    if (!value) return;
    let pk: string | undefined;
    const pointer = decodePointer(value);
    if (pointer?.type === 'profile') pk = pointer.pubkey;
    else if (value.includes('.')) pk = await resolveNip05(value).catch(() => undefined);
    if (!pk) {
      toast({ title: 'Person not found', description: 'Use an npub or name@domain.', variant: 'destructive' });
      return;
    }
    if (!people.includes(pk)) setPeople([...people, pk]);
    setPersonInput('');
  };

  const addMyLocation = () => {
    if (!navigator.geolocation) return toast({ title: 'Location is not available' });
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        setGeohash(encodeGeohash(pos.coords.latitude, pos.coords.longitude, 5));
        toast({ title: 'Approximate location added', description: 'Only a ~5 km area is shared.' });
      },
      () => toast({ title: 'Could not get your location', variant: 'destructive' }),
      { maximumAge: 600_000, timeout: 10_000 },
    );
  };

  const publish = async () => {
    if (!user || !allDone) return;
    setPublishing(true);
    try {
      const text = caption.trim();
      const firstLine = text.split('\n')[0].replace(/#[\p{L}\p{N}_]+/gu, '').trim();
      const tags: string[][] = [['title', firstLine.slice(0, 80) || 'Photo']];
      for (const img of images) {
        const imeta = [...img.upload.imeta!];
        if (img.alt.trim()) imeta.push(`alt ${img.alt.trim()}`);
        tags.push(['imeta', ...imeta]);
      }
      const mimes = [...new Set(images.map((i) => i.upload.meta!.mime))];
      for (const m of mimes) tags.push(['m', m]);
      for (const img of images) tags.push(['x', img.upload.meta!.hash]);
      for (const t of hashtags) tags.push(['t', t]);
      for (const p of people) tags.push(['p', p]);
      if (location.trim()) tags.push(['location', location.trim().slice(0, 100)]);
      if (geohash) tags.push(['g', geohash]);
      if (cw) tags.push(cwReason.trim() ? ['content-warning', cwReason.trim()] : ['content-warning']);

      const event = await signWithFeedback(user, { kind: KIND_PICTURE, content: text, tags });
      await nostr.event(event, { signal: AbortSignal.timeout(10_000) });
      queryClient.invalidateQueries({ queryKey: ['nostr', 'feed'] });
      queryClient.invalidateQueries({ queryKey: ['nostr', 'count', 'posts', user.pubkey] });
      toast({ title: 'Your post has been shared' });
      navigate(`/${neventOf(event)}`, { replace: true });
    } catch (error) {
      toastError(error, 'Could not publish');
    } finally {
      setPublishing(false);
    }
  };

  const img = images[current];
  const pickers = (
    <>
      <input ref={fileInput} type="file" accept="image/*" multiple className="hidden" onChange={(e) => { addFiles(e.target.files); e.target.value = ''; }} />
      <input ref={cameraInput} type="file" accept="image/*" capture="environment" className="hidden" onChange={(e) => { addFiles(e.target.files); e.target.value = ''; }} />
    </>
  );

  if (step === 'select') {
    return (
      <>
        <TopBar
          back
          title="New post"
          right={
            <Button variant="link" className="h-11 text-body font-semibold" disabled={images.length === 0} onClick={goToDetails}>
              Next
            </Button>
          }
        />
        <main className="column pb-8">
          {pickers}
          {!img ? (
            <div className="flex aspect-square flex-col items-center justify-center gap-4 p-8 text-center">
              <ImagePlus className="size-16 text-foreground" strokeWidth={1} aria-hidden />
              <p className="text-title font-normal">Pick photos to share</p>
              <p className="max-w-xs text-meta text-muted-foreground">Location and camera data (EXIF/GPS) are removed from your photos before they are uploaded.</p>
              <div className="flex flex-col gap-2 sm:flex-row">
                <Button className="h-11 px-6" onClick={() => fileInput.current?.click()}>Select from device</Button>
                <Button variant="outline" className="h-11 px-6" onClick={() => cameraInput.current?.click()}>
                  <Camera /> Take photo
                </Button>
              </div>
            </div>
          ) : (
            <>
              <CropEditor src={img.preview} width={img.width} height={img.height} crop={img.crop} onChange={(crop) => update(img.id, { crop })} alt={img.alt || `Photo ${current + 1}`} />
              <div className="space-y-4 p-4">
                <div className="flex flex-wrap items-center gap-2" role="radiogroup" aria-label="Crop">
                  {ASPECTS.map((a) => (
                    <button
                      key={a.label}
                      type="button"
                      role="radio"
                      aria-checked={img.crop.aspect === a.value}
                      onClick={() => update(img.id, { crop: { ...img.crop, aspect: a.value, panX: 0, panY: 0 } })}
                      className={cn('h-8 rounded-full border px-3 text-meta font-semibold', img.crop.aspect === a.value ? 'border-foreground bg-foreground text-background' : 'hover:bg-surface-muted')}
                    >
                      {a.label}
                    </button>
                  ))}
                </div>
                <label className="flex items-center gap-3 text-meta text-muted-foreground">
                  Zoom
                  <Slider min={1} max={3} step={0.01} value={[img.crop.zoom]} onValueChange={([z]) => update(img.id, { crop: { ...img.crop, zoom: z } })} className="flex-1" aria-label="Zoom" />
                </label>
                <Input value={img.alt} onChange={(e) => update(img.id, { alt: e.target.value.slice(0, 500) })} placeholder="Alt text — describe this photo for people who can't see it" className="h-11" aria-label="Alt text" />
              </div>
              <div className="flex gap-2 overflow-x-auto px-4 pb-2" aria-label="Selected photos">
                {images.map((it, i) => (
                  <div key={it.id} className="shrink-0">
                    <button
                      type="button"
                      onClick={() => setCurrent(i)}
                      aria-label={`Edit photo ${i + 1}`}
                      aria-current={i === current}
                      className={cn('relative block size-20 overflow-hidden rounded-lg border-2', i === current ? 'border-accent' : 'border-transparent')}
                    >
                      <img src={it.preview} alt="" className="size-full object-cover" />
                      <span className="absolute top-1 left-1 flex size-5 items-center justify-center rounded-full bg-black/60 text-[0.625rem] text-white">{i + 1}</span>
                    </button>
                    {i === current && (
                      <div className="mt-1 flex justify-center gap-0.5">
                        <button type="button" aria-label="Move left" disabled={i === 0} onClick={() => move(i, -1)} className="flex size-7 items-center justify-center rounded-full hover:bg-surface-muted disabled:opacity-30"><ChevronLeft className="size-4" /></button>
                        <button type="button" aria-label="Remove photo" onClick={() => remove(it.id)} className="flex size-7 items-center justify-center rounded-full text-destructive hover:bg-surface-muted"><Trash2 className="size-4" /></button>
                        <button type="button" aria-label="Move right" disabled={i === images.length - 1} onClick={() => move(i, 1)} className="flex size-7 items-center justify-center rounded-full hover:bg-surface-muted disabled:opacity-30"><ChevronRight className="size-4" /></button>
                      </div>
                    )}
                  </div>
                ))}
                {images.length < MAX_IMAGES && (
                  <div className="flex shrink-0 flex-col gap-1">
                    <button type="button" onClick={() => fileInput.current?.click()} aria-label="Add photos" className="flex size-20 items-center justify-center rounded-lg border border-dashed hover:bg-surface-muted"><Plus className="size-6" /></button>
                    <button type="button" onClick={() => cameraInput.current?.click()} aria-label="Take photo" className="flex h-7 items-center justify-center rounded-lg hover:bg-surface-muted"><Camera className="size-4" /></button>
                  </div>
                )}
              </div>
            </>
          )}
        </main>
      </>
    );
  }

  return (
    <>
      <TopBar
        left={
          <button type="button" aria-label="Back to editing" onClick={() => setStep('select')} className="inline-flex size-11 items-center justify-center rounded-full hover:opacity-60">
            <ChevronLeft className="size-6" />
          </button>
        }
        title="New post"
        right={
          <Button variant="link" className="h-11 text-body font-semibold" disabled={!allDone || publishing} onClick={publish}>
            {publishing ? 'Sharing…' : 'Share'}
          </Button>
        }
      />
      <main className="column space-y-5 p-4 pb-12">
        <ul className="space-y-2" aria-label="Uploads">
          {images.map((it, i) => {
            const fresh = it.upload.key === cropKey(it);
            const status = fresh ? it.upload.status : 'processing';
            return (
              <li key={it.id} className="flex items-center gap-3">
                <img src={it.preview} alt="" className="size-12 shrink-0 rounded-lg object-cover" />
                <div className="min-w-0 flex-1">
                  <p className="truncate text-meta">Photo {i + 1}{it.alt ? ` · ${it.alt}` : ''}</p>
                  {status === 'error' ? (
                    <p className="flex items-center gap-1 text-meta text-destructive"><AlertCircle className="size-3.5 shrink-0" /> <span className="truncate">{it.upload.error}</span></p>
                  ) : status === 'done' ? (
                    <p className="flex items-center gap-1 text-meta text-muted-foreground"><CheckCircle2 className="size-3.5 text-accent" /> Uploaded</p>
                  ) : (
                    <Progress value={status === 'uploading' ? Math.round(it.upload.progress * 100) : 5} className="mt-1 h-1.5" aria-label={`Upload progress photo ${i + 1}`} />
                  )}
                </div>
                {status === 'error' && (
                  <Button variant="outline" size="sm" className="h-9" onClick={() => startUpload(it)}><RotateCcw /> Retry</Button>
                )}
                {(status === 'processing' || status === 'uploading') && <Spinner className="size-4" />}
              </li>
            );
          })}
        </ul>
        {!allDone && <p className="text-meta text-muted-foreground">Uploading to {new URL(servers[0] ?? 'https://none').host}{servers.length > 1 ? ` (+${servers.length - 1} fallback)` : ''}. Sharing unlocks when every photo is uploaded.</p>}

        <div className="flex gap-3">
          <UserAvatar pubkey={user!.pubkey} size={32} />
          <Textarea value={caption} onChange={(e) => setCaption(e.target.value.slice(0, 2200))} placeholder="Write a caption… use #hashtags" rows={4} className="min-h-24 flex-1 resize-none border-none bg-transparent p-0 shadow-none focus-visible:ring-0" aria-label="Caption" />
        </div>

        <section className="space-y-2 border-t pt-4">
          <h2 className="text-body font-semibold">Hashtags</h2>
          <div className="flex flex-wrap gap-1.5">
            {hashtags.map((t) => (
              <span key={t} className="inline-flex h-8 items-center gap-1 rounded-full bg-surface-muted px-3 text-meta text-accent">
                #{t}
                {extraTags.includes(t) && (
                  <button type="button" aria-label={`Remove #${t}`} onClick={() => setExtraTags(extraTags.filter((x) => x !== t))}><X className="size-3.5" /></button>
                )}
              </span>
            ))}
          </div>
          <form onSubmit={(e) => { e.preventDefault(); addTag(); }} className="flex gap-2">
            <Input value={tagInput} onChange={(e) => setTagInput(e.target.value)} placeholder="Add a hashtag" className="h-11" aria-label="Add hashtag" />
            <Button type="submit" variant="outline" className="h-11" disabled={!tagInput.trim()}>Add</Button>
          </form>
        </section>

        <section className="space-y-2 border-t pt-4">
          <h2 className="flex items-center gap-2 text-body font-semibold"><MapPin className="size-4" /> Location</h2>
          <div className="flex gap-2">
            <Input value={location} onChange={(e) => setLocation(e.target.value)} placeholder="Add a place name" className="h-11" aria-label="Location" />
            <Button type="button" variant={geohash ? 'secondary' : 'outline'} className="h-11" onClick={() => (geohash ? setGeohash(undefined) : addMyLocation())} aria-pressed={!!geohash}>
              <LocateFixed /> {geohash ? 'Remove area' : 'Area'}
            </Button>
          </div>
        </section>

        <section className="space-y-2 border-t pt-4">
          <h2 className="flex items-center gap-2 text-body font-semibold"><UserPlus className="size-4" /> Tag people</h2>
          {people.length > 0 && (
            <div className="flex flex-wrap gap-1.5">
              {people.map((pk) => (
                <span key={pk} className="inline-flex h-8 items-center gap-1.5 rounded-full bg-surface-muted pr-2 pl-1">
                  <UserAvatar pubkey={pk} size={24} />
                  <UserName pubkey={pk} link={false} className="text-meta" />
                  <button type="button" aria-label="Remove tag" onClick={() => setPeople(people.filter((p) => p !== pk))}><X className="size-3.5" /></button>
                </span>
              ))}
            </div>
          )}
          <form onSubmit={(e) => { e.preventDefault(); addPerson(); }} className="flex gap-2">
            <Input value={personInput} onChange={(e) => setPersonInput(e.target.value)} placeholder="npub1… or name@domain" className="h-11" aria-label="Tag a person" />
            <Button type="submit" variant="outline" className="h-11" disabled={!personInput.trim()}>Add</Button>
          </form>
        </section>

        <section className="space-y-2 border-t pt-4">
          <label className="flex items-center justify-between gap-3">
            <span>
              <span className="block text-body font-semibold">Sensitive content</span>
              <span className="block text-meta text-muted-foreground">Blur the photos behind a warning</span>
            </span>
            <Switch checked={cw} onCheckedChange={setCw} />
          </label>
          {cw && <Input value={cwReason} onChange={(e) => setCwReason(e.target.value)} placeholder="Reason (optional)" className="h-11" aria-label="Content warning reason" />}
        </section>

        <Button className="h-12 w-full text-body" disabled={!allDone || publishing} onClick={publish}>
          {publishing ? 'Sharing…' : allDone ? 'Share' : 'Waiting for uploads…'}
        </Button>
      </main>
    </>
  );
}
