import { useState } from 'react';
import { useSeoMeta } from '@unhead/react';
import { CheckCircle2, XCircle } from 'lucide-react';
import { TopBar } from '@/components/layout/TopBar';
import { SettingsSection } from '@/components/settings/SettingsRow';
import { UrlListEditor } from '@/components/settings/UrlListEditor';
import { Spinner } from '@/components/common/States';
import { Button } from '@/components/ui/button';
import { Switch } from '@/components/ui/switch';
import { useAppContext } from '@/hooks/useAppContext';
import { useCurrentUser } from '@/hooks/useCurrentUser';
import { useSaveBlossomServers } from '@/hooks/useSettingsLists';
import { APP_BLOSSOM_SERVERS } from '@/lib/appBlossom';
import { testBlossomServer } from '@/lib/blossom';
import { normalizeServerUrl } from '@/lib/sanitize';

type TestResult = { status: 'testing' } | { status: 'ok'; ms: number } | { status: 'fail'; error: string };

export default function MediaServersPage() {
  useSeoMeta({ title: 'Media servers · LUMINA' });
  const { config } = useAppContext();
  const { user } = useCurrentUser();
  const [servers, setServers] = useState(config.blossomServerMetadata.servers);
  const [useApp, setUseApp] = useState(config.useAppBlossomServers);
  const [tests, setTests] = useState<Record<string, TestResult>>({});
  const save = useSaveBlossomServers();
  const dirty = JSON.stringify(servers) !== JSON.stringify(config.blossomServerMetadata.servers) || useApp !== config.useAppBlossomServers;

  const test = async (url: string) => {
    setTests((t) => ({ ...t, [url]: { status: 'testing' } }));
    try {
      const ms = await testBlossomServer(url);
      setTests((t) => ({ ...t, [url]: { status: 'ok', ms } }));
    } catch (e) {
      setTests((t) => ({ ...t, [url]: { status: 'fail', error: e instanceof Error ? e.message : 'Unreachable' } }));
    }
  };

  return (
    <>
      <TopBar back="/settings" title="Media servers" />
      <main className="column pb-12">
        <SettingsSection title="Blossom servers (BUD-03)" description="Photos are uploaded to the first server that accepts them; the others are used as fallbacks when an image fails to load. Saved as a kind 10063 list.">
          <div className="space-y-3 px-4 pb-3">
            <UrlListEditor
              items={servers}
              onChange={setServers}
              normalize={normalizeServerUrl}
              placeholder="https://blossom.example.com"
              reorder
              emptyText="No personal servers. LUMINA's default servers will be used."
              renderExtra={(url) => {
                const r = tests[url];
                return (
                  <button type="button" onClick={() => test(url)} className="flex h-9 items-center gap-1 rounded-lg px-2 text-meta hover:bg-surface-muted" aria-label={`Test ${url}`}>
                    {!r ? 'Test' : r.status === 'testing' ? <Spinner className="size-4" /> : r.status === 'ok' ? <><CheckCircle2 className="size-4 text-accent" /> {r.ms} ms</> : <><XCircle className="size-4 text-destructive" /> Failed</>}
                  </button>
                );
              }}
            />
            <label className="flex items-center justify-between gap-3 py-2">
              <span>
                <span className="block text-body">Also use LUMINA's default servers</span>
                <span className="block text-meta text-muted-foreground">{APP_BLOSSOM_SERVERS.servers.map((s) => new URL(s).host).join(', ')}</span>
              </span>
              <Switch checked={useApp} onCheckedChange={setUseApp} />
            </label>
            <Button className="h-11 w-full" disabled={!dirty || save.isPending || (!useApp && servers.length === 0)} onClick={() => save.mutate({ servers, useAppServers: useApp })}>
              {save.isPending ? 'Saving…' : user ? 'Save & publish' : 'Save'}
            </Button>
          </div>
        </SettingsSection>
      </main>
    </>
  );
}
