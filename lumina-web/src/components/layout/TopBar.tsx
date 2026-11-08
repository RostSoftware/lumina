import type { ReactNode } from 'react';
import { useNavigate } from 'react-router-dom';
import { ArrowLeft } from 'lucide-react';
import { ICON_STROKE } from '@/components/common/Icons';
import { cn } from '@/lib/utils';

interface TopBarProps {
  title?: ReactNode;
  /** Show a back arrow. A string navigates to that path; true goes back in history. */
  back?: boolean | string;
  left?: ReactNode;
  right?: ReactNode;
  className?: string;
}

/** 56px top bar: surface background, hairline bottom border, content inside the column. */
export function TopBar({ title, back, left, right, className }: TopBarProps) {
  const navigate = useNavigate();
  const goBack = () => {
    if (typeof back === 'string') navigate(back);
    else if (window.history.state && window.history.state.idx > 0) navigate(-1);
    else navigate('/');
  };

  return (
    <header className={cn('sticky top-0 z-40 border-b bg-surface pt-[var(--safe-top)]', className)}>
      <div className="column flex h-(--bar-height) items-center gap-2 px-2">
        {back && (
          <IconButton label="Back" onClick={goBack}>
            <ArrowLeft />
          </IconButton>
        )}
        {left}
        {title && <h1 className={cn('min-w-0 flex-1 truncate text-title', !back && !left && 'pl-2')}>{title}</h1>}
        {!title && <div className="flex-1" />}
        {right && <div className="flex items-center">{right}</div>}
      </div>
    </header>
  );
}

/** 44px+ square icon button with an accessible label. */
export function IconButton({
  label,
  onClick,
  children,
  className,
  disabled,
  pressed,
}: {
  label: string;
  onClick?: () => void;
  children: ReactNode;
  className?: string;
  disabled?: boolean;
  pressed?: boolean;
}) {
  return (
    <button
      type="button"
      aria-label={label}
      title={label}
      aria-pressed={pressed}
      onClick={onClick}
      disabled={disabled}
      className={cn(
        'relative inline-flex size-11 shrink-0 items-center justify-center rounded-full text-foreground transition-opacity hover:opacity-60 disabled:opacity-40 [&_svg]:size-6',
        className,
      )}
    >
      {children}
    </button>
  );
}

export { ICON_STROKE };
