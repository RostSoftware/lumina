import { cn } from '@/lib/utils';

/** The LUMINA wordmark — the only text that uses the brand gradient. */
export function Wordmark({ className }: { className?: string }) {
  return <span className={cn('brand-gradient-text text-wordmark tracking-tight select-none', className)}>LUMINA</span>;
}
