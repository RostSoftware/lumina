import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useSeoMeta } from '@unhead/react';
import { useNostrLogin } from '@nostrify/react/login';
import { Check, Copy, LogOut, UserPlus } from 'lucide-react';
import { TopBar } from '@/components/layout/TopBar';
import { SettingsSection } from '@/components/settings/SettingsRow';
import { UserAvatar } from '@/components/common/UserAvatar';
import { UserName } from '@/components/common/UserName';
import { copyText } from '@/lib/clipboard';
import { Button } from '@/components/ui/button';
import { QRCodeCanvas } from '@/components/ui/qrcode';
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from '@/components/ui/alert-dialog';
import { useCurrentUser } from '@/hooks/useCurrentUser';
import { useLoginDialog } from '@/hooks/useLoginDialog';
import { useLogout } from '@/hooks/useLogout';
import { npubOf } from '@/lib/nostr';
import MyProfileRedirect from '../MyProfileRedirect';

const SIGNER_LABEL: Record<string, string> = {
  nsec: 'Secret key stored in this browser',
  bunker: 'Remote signer (NIP-46)',
  extension: 'Browser extension (NIP-07)',
};

export default function AccountPage() {
  useSeoMeta({ title: 'Accounts · LUMINA' });
  const { user } = useCurrentUser();
  const { logins, setLogin } = useNostrLogin();
  const { openLogin } = useLoginDialog();
  const logout = useLogout();
  const navigate = useNavigate();
  const [confirm, setConfirm] = useState<string | null>(null);

  if (!user) return <MyProfileRedirect />;
  const npub = npubOf(user.pubkey);
  const current = logins[0];

  return (
    <>
      <TopBar back="/settings" title="Accounts & keys" />
      <main className="column pb-12">
        <SettingsSection title="Public key">
          <div className="flex flex-col items-center gap-3 px-4 pb-4">
            <div className="rounded-lg bg-white p-3"><QRCodeCanvas value={`nostr:${npub}`} size={180} /></div>
            <p className="max-w-full break-all text-center font-mono text-meta text-muted-foreground">{npub}</p>
            <Button variant="outline" className="h-11" onClick={() => copyText(npub, 'npub copied')}><Copy /> Copy npub</Button>
          </div>
        </SettingsSection>
        <SettingsSection title="Signer">
          <p className="px-4 pb-3 text-body">{SIGNER_LABEL[current?.type ?? ''] ?? current?.type}</p>
          {current?.type === 'nsec' && (
            <p className="mx-4 mb-3 rounded-lg border border-destructive/40 bg-destructive/5 p-3 text-meta">
              Your secret key is stored unencrypted in this browser. Consider moving to a signer app such as Amber or nsec.app.
            </p>
          )}
        </SettingsSection>
        <SettingsSection title="Accounts">
          <ul>
            {logins.map((login) => (
              <li key={login.id} className="flex items-center gap-3 px-4 py-2">
                <button type="button" className="flex min-w-0 flex-1 items-center gap-3 text-left" onClick={() => { setLogin(login.id); navigate('/'); }} aria-label={`Switch to account ${npubOf(login.pubkey).slice(0, 16)}`}>
                  <UserAvatar pubkey={login.pubkey} size={44} />
                  <span className="min-w-0 flex-1">
                    <UserName pubkey={login.pubkey} variant="display" link={false} />
                    <span className="block text-meta text-muted-foreground">{SIGNER_LABEL[login.type] ?? login.type}</span>
                  </span>
                  {login.id === current?.id && <Check className="size-5 text-accent" aria-label="Active" />}
                </button>
                <Button variant="ghost" size="icon" className="size-11" aria-label="Log out of this account" onClick={() => setConfirm(login.id)}><LogOut /></Button>
              </li>
            ))}
          </ul>
          <button type="button" onClick={openLogin} className="flex min-h-14 w-full items-center gap-3 px-4 text-left text-accent hover:bg-surface-muted">
            <UserPlus className="size-6" strokeWidth={1.75} /> Add account
          </button>
        </SettingsSection>
        <div className="p-4">
          <Button variant="outline" className="h-11 w-full text-destructive" onClick={() => setConfirm(current?.id ?? null)}>Log out</Button>
        </div>
      </main>
      <AlertDialog open={!!confirm} onOpenChange={(o) => !o && setConfirm(null)}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Log out?</AlertDialogTitle>
            <AlertDialogDescription>
              This removes the account's keys or signer connection, its wallet connection and all cached data from this browser. Make sure you have a backup of your key.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Cancel</AlertDialogCancel>
            <AlertDialogAction
              className="bg-destructive text-destructive-foreground hover:bg-destructive/90"
              onClick={() => {
                const wasCurrent = confirm === current?.id;
                logout(confirm ?? undefined);
                setConfirm(null);
                if (wasCurrent) navigate('/');
              }}
            >
              Log out
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </>
  );
}
