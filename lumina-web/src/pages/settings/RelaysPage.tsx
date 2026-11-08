import { useState } from 'react';
import { useSeoMeta } from '@unhead/react';
import { TopBar } from '@/components/layout/TopBar';
import { SettingsSection } from '@/components/settings/SettingsRow';
import { UrlListEditor } from '@/components/settings/UrlListEditor';
import { Button } from '@/components/ui/button';
import { Switch } from '@/components/ui/switch';
import { useAppContext } from '@/hooks/useAppContext';
import { useCurrentUser } from '@/hooks/useCurrentUser';
import { useDMRelays } from '@/hooks/useMessages';
import { useSaveDMRelays, useSaveRelays } from '@/hooks/useSettingsLists';
import { APP_RELAYS } from '@/lib/appRelays';
import { normalizeRelayUrl } from '@/lib/sanitize';

type Relay = { url: string; read: boolean; write: boolean };

export default function RelaysPage() {
  useSeoMeta({ title: 'Relays · LUMINA' });
  const { config } = useAppContext();
  const { user } = useCurrentUser();
  const [relays, setRelays] = useState<Relay[]>(config.relayMetadata.relays);
  const save = useSaveRelays();
  const dirty = JSON.stringify(relays) !== JSON.stringify(config.relayMetadata.relays);

  const { data: dmData, isLoading: dmLoading } = useDMRelays(user?.pubkey);
  const [dmRelays, setDmRelays] = useState<string[] | null>(null);
  const dmList = dmRelays ?? dmData?.relays ?? [];
  const saveDM = useSaveDMRelays();

  return (
    <>
      <TopBar back="/settings" title="Relays" />
      <main className="column pb-12">
        <SettingsSection title="Relays (NIP-65)" description="LUMINA reads posts from your read relays and publishes to your write relays. Saved as a kind 10002 list so other apps use the same relays.">
          <div className="space-y-2 px-4 pb-3">
            <UrlListEditor
              items={relays.map((r) => r.url)}
              onChange={(urls) => setRelays(urls.map((url) => relays.find((r) => r.url === url) ?? { url, read: true, write: true }))}
              normalize={normalizeRelayUrl}
              placeholder="wss://relay.example.com"
              emptyText="No relays. LUMINA needs at least one read and one write relay."
              renderExtra={(url) => {
                const r = relays.find((x) => x.url === url)!;
                const set = (patch: Partial<Relay>) => setRelays(relays.map((x) => (x.url === url ? { ...x, ...patch } : x)));
                return (
                  <span className="flex items-center gap-2 text-meta text-muted-foreground">
                    <label className="flex items-center gap-1">R <Switch checked={r.read} onCheckedChange={(v) => set({ read: v })} aria-label={`Read from ${url}`} /></label>
                    <label className="flex items-center gap-1">W <Switch checked={r.write} onCheckedChange={(v) => set({ write: v })} aria-label={`Write to ${url}`} /></label>
                  </span>
                );
              }}
            />
            <div className="flex gap-2 pt-1">
              <Button className="h-11 flex-1" disabled={!dirty || save.isPending || !relays.some((r) => r.read) || !relays.some((r) => r.write)} onClick={() => save.mutate(relays)}>
                {save.isPending ? 'Saving…' : user ? 'Save & publish' : 'Save'}
              </Button>
              <Button variant="outline" className="h-11" onClick={() => setRelays(APP_RELAYS.relays)}>Defaults</Button>
            </div>
          </div>
        </SettingsSection>
        {user && (
          <SettingsSection title="Direct message inbox (NIP-17)" description="Private messages to you are delivered to these relays (kind 10050). Use relays you trust; relays that require authentication (NIP-42) are best.">
            <div className="space-y-2 px-4 pb-3">
              {dmLoading ? (
                <p className="text-meta text-muted-foreground">Loading…</p>
              ) : (
                <UrlListEditor items={dmList} onChange={setDmRelays} normalize={normalizeRelayUrl} placeholder="wss://inbox.example.com" emptyText="No DM relays set — people will send messages to your read relays instead." />
              )}
              <Button className="h-11 w-full" disabled={dmRelays === null || saveDM.isPending} onClick={() => saveDM.mutate(dmList, { onSuccess: () => setDmRelays(null) })}>
                {saveDM.isPending ? 'Saving…' : 'Save DM relays'}
              </Button>
            </div>
          </SettingsSection>
        )}
      </main>
    </>
  );
}
