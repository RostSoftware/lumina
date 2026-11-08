import type { ReactNode } from 'react';
import { Drawer as DrawerPrimitive } from 'vaul';
import { cn } from '@/lib/utils';

interface BottomSheetProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  title: string;
  /** Visually hide the title (still read by screen readers). */
  hideTitle?: boolean;
  description?: string;
  children: ReactNode;
  className?: string;
}

/**
 * Bottom sheet used for post actions, zaps, share, etc. Identical on every
 * screen size: anchored to the bottom, constrained to the 470px content
 * column, 16px top corner radius, drag handle.
 */
export function BottomSheet({ open, onOpenChange, title, hideTitle, description, children, className }: BottomSheetProps) {
  return (
    <DrawerPrimitive.Root open={open} onOpenChange={onOpenChange}>
      <DrawerPrimitive.Portal>
        <DrawerPrimitive.Overlay className="fixed inset-0 z-50 bg-black/50 data-[state=open]:animate-in data-[state=open]:fade-in-0 data-[state=closed]:animate-out data-[state=closed]:fade-out-0" />
        <DrawerPrimitive.Content
          className={cn(
            'fixed inset-x-0 bottom-0 z-50 mx-auto flex max-h-[90dvh] w-full max-w-(--column-width) flex-col rounded-t-2xl border-t bg-surface outline-none',
            'pb-[var(--safe-bottom)]',
            className,
          )}
        >
          <div className="mx-auto mt-2 h-1 w-10 shrink-0 rounded-full bg-border" aria-hidden />
          <DrawerPrimitive.Title className={cn('px-4 pt-3 pb-2 text-center text-title', hideTitle && 'sr-only')}>{title}</DrawerPrimitive.Title>
          {description ? (
            <DrawerPrimitive.Description className="px-4 pb-2 text-center text-meta text-muted-foreground">{description}</DrawerPrimitive.Description>
          ) : (
            <DrawerPrimitive.Description className="sr-only">{title}</DrawerPrimitive.Description>
          )}
          <div className="min-h-0 flex-1 overflow-y-auto">{children}</div>
        </DrawerPrimitive.Content>
      </DrawerPrimitive.Portal>
    </DrawerPrimitive.Root>
  );
}

interface SheetActionProps {
  icon?: ReactNode;
  label: string;
  onClick: () => void;
  destructive?: boolean;
  disabled?: boolean;
}

/** A full-width row inside a bottom sheet. */
export function SheetAction({ icon, label, onClick, destructive, disabled }: SheetActionProps) {
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled}
      className={cn(
        'flex min-h-12 w-full items-center gap-3 px-4 py-3 text-left text-body transition-colors hover:bg-surface-muted disabled:opacity-50',
        destructive && 'text-destructive',
      )}
    >
      {icon && <span className="flex size-6 items-center justify-center [&_svg]:size-5">{icon}</span>}
      <span>{label}</span>
    </button>
  );
}
