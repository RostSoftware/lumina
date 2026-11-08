import { Link } from 'react-router-dom';
import { CheckCircle2 } from 'lucide-react';
import { EmptyState } from '@/components/common/States';
import { Button } from '@/components/ui/button';

/** Callback page after a mobile signer app approved a nostrconnect login. */
export default function RemoteLoginSuccess() {
  return (
    <main className="column pt-16">
      <EmptyState
        icon={<CheckCircle2 strokeWidth={1.5} />}
        title="Signer connected"
        description="You can return to the LUMINA tab where you started logging in."
        action={<Button asChild className="h-11 px-6"><Link to="/">Open LUMINA</Link></Button>}
      />
    </main>
  );
}
