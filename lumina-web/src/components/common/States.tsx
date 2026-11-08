import type { ReactNode } from 'react';
import { AlertCircle, RefreshCw } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { cn } from '@/lib/utils';

export function EmptyState({ icon, title, description, action, className }: { icon?: ReactNode; title: string; description?: ReactNode; action?: ReactNode; className?: string }) {
  return (
    <div className={cn('flex flex-col items-center px-8 py-16 text-center', className)}>
      {icon && (
        <div className="mb-4 flex size-16 items-center justify-center rounded-full border-2 border-foreground [&_svg]:size-8" aria-hidden>
          {icon}
        </div>
      )}
      <h2 className="text-title">{title}</h2>
      {description && <p className="mt-1 max-w-xs text-body text-muted-foreground">{description}</p>}
      {action && <div className="mt-5">{action}</div>}
    </div>
  );
}

export function ErrorState({ title = "Couldn't load this", error, onRetry, className }: { title?: string; error?: unknown; onRetry?: () => void; className?: string }) {
  const message = error instanceof Error ? error.message : typeof error === 'string' ? error : 'Check your connection and relays, then try again.';
  return (
    <div role="alert" className={cn('flex flex-col items-center px-8 py-12 text-center', className)}>
      <AlertCircle className="mb-3 size-8 text-destructive" aria-hidden />
      <h2 className="text-title">{title}</h2>
      <p className="mt-1 max-w-xs text-body text-muted-foreground">{message}</p>
      {onRetry && (
        <Button variant="outline" className="mt-4 h-11" onClick={onRetry}>
          <RefreshCw /> Try again
        </Button>
      )}
    </div>
  );
}

export function Spinner({ className }: { className?: string }) {
  return (
    <span
      role="status"
      aria-label="Loading"
      className={cn('inline-block size-5 animate-spin rounded-full border-2 border-muted-foreground/30 border-t-foreground', className)}
    />
  );
}
