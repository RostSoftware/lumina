import type { ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { ChevronRight } from 'lucide-react';

export function SettingsRow({ to, icon, label, detail }: { to: string; icon: ReactNode; label: string; detail?: string }) {
  return (
    <Link to={to} className="flex min-h-14 items-center gap-3 px-4 hover:bg-surface-muted">
      <span className="flex size-6 items-center justify-center [&_svg]:size-6" aria-hidden>{icon}</span>
      <span className="flex-1 text-body">{label}</span>
      {detail && <span className="max-w-[40%] truncate text-meta text-muted-foreground">{detail}</span>}
      <ChevronRight className="size-4 text-muted-foreground" aria-hidden />
    </Link>
  );
}

export function SettingsSection({ title, children, description }: { title?: string; description?: ReactNode; children: ReactNode }) {
  return (
    <section className="border-b py-2">
      {title && <h2 className="px-4 pt-2 pb-1 text-meta font-semibold text-muted-foreground">{title}</h2>}
      {description && <p className="px-4 pb-2 text-meta text-muted-foreground">{description}</p>}
      {children}
    </section>
  );
}
