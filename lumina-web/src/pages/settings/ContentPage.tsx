import { useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { useSeoMeta } from '@unhead/react';
import { X } from 'lucide-react';
import { TopBar } from '@/components/layout/TopBar';
import { SettingsSection } from '@/components/settings/SettingsRow';
import { UserAvatar } from '@/components/common/UserAvatar';
import { UserName } from '@/components/common/UserName';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Switch } from '@/components/ui/switch';
import { useAppContext } from '@/hooks/useAppContext';
import { useCurrentUser } from '@/hooks/useCurrentUser';
import { useMuteList, useMuteMutation } from '@/hooks/useMutes';
import { profilePath } from '@/lib/nostr';

export default function ContentPage() {
  useSeoMeta({ title: 'Content · LUMINA' });
  const { config, updateConfig } = useAppContext();
  const { user } = useCurrentUser();
  const { mutes, isLoading } = useMuteList();
  const mute = useMuteMutation();
  const [word, setWord] = useState('');

  const addWord = (e: FormEvent) => {
    e.preventDefault();
    const value = word.trim().toLowerCase();
    if (!value) return;
    const isTag = value.startsWith('#') && value.length > 1;
    mute.mutate({ item: isTag ? { type: 'hashtag', value: value.slice(1) } : { type: 'word', value }, mute: true }, { onSuccess: () => setWord('') });
  };

  return (
    <>
      <TopBar back="/settings" title="Muted & sensitive" />
      <main className="column pb-12">
        <SettingsSection title="Sensitive content">
          <label className="flex min-h-14 items-center justify-between gap-3 px-4">
            <span>
              <span className="block text-body">Show sensitive content</span>
              <span className="block text-meta text-muted-foreground">Don't blur posts marked with a content warning</span>
            </span>
            <Switch checked={config.showSensitive} onCheckedChange={(v) => updateConfig((c) => ({ ...c, showSensitive: v }))} />
          </label>
        </SettingsSection>
        {user && (
          <>
            <SettingsSection title="Muted words & hashtags" description="Posts, comments and notifications containing these are hidden. Saved in your public NIP-51 mute list.">
              <div className="space-y-2 px-4 pb-3">
                <div className="flex flex-wrap gap-1.5">
                  {[...mutes.words.map((w) => ({ type: 'word' as const, value: w, label: w })), ...[...mutes.hashtags].map((t) => ({ type: 'hashtag' as const, value: t, label: `#${t}` }))].map((item) => (
                    <span key={`${item.type}:${item.value}`} className="inline-flex h-8 items-center gap-1 rounded-full bg-surface-muted px-3 text-meta">
                      {item.label}
                      <button type="button" aria-label={`Unmute ${item.label}`} onClick={() => mute.mutate({ item, mute: false })}><X className="size-3.5" /></button>
                    </span>
                  ))}
                  {!isLoading && mutes.words.length === 0 && mutes.hashtags.size === 0 && <p className="text-meta text-muted-foreground">Nothing muted.</p>}
                </div>
                <form onSubmit={addWord} className="flex gap-2">
                  <Input value={word} onChange={(e) => setWord(e.target.value)} placeholder="Word or #hashtag" className="h-11" aria-label="Word to mute" />
                  <Button type="submit" variant="outline" className="h-11" disabled={!word.trim() || mute.isPending}>Mute</Button>
                </form>
              </div>
            </SettingsSection>
            <SettingsSection title="Muted accounts">
              {mutes.pubkeys.size === 0 ? (
                <p className="px-4 pb-3 text-meta text-muted-foreground">You haven't muted anyone. Use the “…” menu on a post or profile.</p>
              ) : (
                <ul>
                  {[...mutes.pubkeys].map((pk) => (
                    <li key={pk} className="flex items-center gap-3 px-4 py-2">
                      <Link to={profilePath(pk)} aria-hidden tabIndex={-1}><UserAvatar pubkey={pk} size={44} /></Link>
                      <div className="min-w-0 flex-1"><UserName pubkey={pk} /></div>
                      <Button variant="secondary" className="h-8 text-meta font-semibold" onClick={() => mute.mutate({ item: { type: 'pubkey', value: pk }, mute: false })}>Unmute</Button>
                    </li>
                  ))}
                </ul>
              )}
            </SettingsSection>
          </>
        )}
      </main>
    </>
  );
}
