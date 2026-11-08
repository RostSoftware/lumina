import { Link } from 'react-router-dom';
import { useSeoMeta } from '@unhead/react';
import { TopBar } from '@/components/layout/TopBar';
import { EmptyState } from '@/components/common/States';
import { Button } from '@/components/ui/button';
import { SearchX } from 'lucide-react';

export default function NotFound() {
  useSeoMeta({ title: 'Page not found · LUMINA' });
  return (
    <>
      <TopBar back title="Not found" />
      <main className="column">
        <EmptyState
          icon={<SearchX strokeWidth={1.5} />}
          title="This page isn't available"
          description="The link may be broken, or the page may have been removed."
          action={<Button asChild className="h-11 px-6"><Link to="/">Go to LUMINA</Link></Button>}
        />
      </main>
    </>
  );
}
