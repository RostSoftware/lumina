import { useSeoMeta } from '@unhead/react';
import { Ban, Image, Link2, Palette, Radio, User, UserCog, Wallet } from 'lucide-react';
import { TopBar } from '@/components/layout/TopBar';
import { SettingsRow, SettingsSection } from '@/components/settings/SettingsRow';
import { Button } from '@/components/ui/button';
import { useAppContext } from '@/hooks/useAppContext';
import { useCurrentUser } from '@/hooks/useCurrentUser';
import { useLoginDialog } from '@/hooks/useLoginDialog';
import { useWallet } from '@/hooks/useWallet';
import { toast } from '@/hooks/useToast';

export default function SettingsPage() {
  useSeoMeta({ title: 'Settings · LUMINA' });
  const { user } = useCurrentUser();
  const { config } = useAppContext();
  const { openLogin } = useLoginDialog();
  const { wallet } = useWallet();

  const registerHandler = () => {
    try {
      navigator.registerProtocolHandler('nostr', `${window.location.origin}/open?uri=%s`);
      toast({ title: 'LUMINA will open nostr: links', description: 'Confirm the browser prompt if one appears.' });
    } catch {
      toast({ title: 'Your browser does not allow this', variant: 'destructive' });
    }
  };

  return (
    <>
      <TopBar back title="Settings" />
      <main className="column">
        {!user && (
          <div className="border-b p-4">
            <p className="mb-3 text-body text-muted-foreground">Log in to sync your relays, media servers and lists across devices.</p>
            <Button className="h-11 w-full" onClick={openLogin}>Log in</Button>
          </div>
        )}
        {user && (
          <SettingsSection title="Your account">
            <SettingsRow to="/settings/profile" icon={<User strokeWidth={1.75} />} label="Edit profile" />
            <SettingsRow to="/settings/account" icon={<UserCog strokeWidth={1.75} />} label="Accounts & keys" />
          </SettingsSection>
        )}
        <SettingsSection title="Network">
          <SettingsRow to="/settings/relays" icon={<Radio strokeWidth={1.75} />} label="Relays" detail={`${config.relayMetadata.relays.length}`} />
          <SettingsRow to="/settings/media" icon={<Image strokeWidth={1.75} />} label="Media servers" />
        </SettingsSection>
        {user && (
          <SettingsSection title="Payments">
            <SettingsRow to="/settings/wallet" icon={<Wallet strokeWidth={1.75} />} label="Wallet" detail={wallet ? wallet.alias ?? 'Connected' : 'Not connected'} />
          </SettingsSection>
        )}
        <SettingsSection title="Preferences">
          <SettingsRow to="/settings/appearance" icon={<Palette strokeWidth={1.75} />} label="Appearance" detail={config.theme === 'system' ? 'System' : config.theme === 'dark' ? 'Dark' : 'Light'} />
          <SettingsRow to="/settings/content" icon={<Ban strokeWidth={1.75} />} label="Muted & sensitive content" />
          <button type="button" onClick={registerHandler} className="flex min-h-14 w-full items-center gap-3 px-4 text-left hover:bg-surface-muted">
            <Link2 className="size-6" strokeWidth={1.75} aria-hidden />
            <span className="flex-1 text-body">Open nostr: links with LUMINA</span>
          </button>
        </SettingsSection>
        <p className="px-4 py-6 text-center text-meta text-muted-foreground">LUMINA · photos on Nostr</p>
      </main>
    </>
  );
}
