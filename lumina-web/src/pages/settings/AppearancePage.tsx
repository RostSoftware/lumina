import { useSeoMeta } from '@unhead/react';
import { Monitor, Moon, Sun } from 'lucide-react';
import { TopBar } from '@/components/layout/TopBar';
import { SettingsSection } from '@/components/settings/SettingsRow';
import { useTheme } from '@/hooks/useTheme';
import type { Theme } from '@/contexts/AppContext';
import { cn } from '@/lib/utils';

const OPTIONS: { value: Theme; label: string; icon: typeof Sun }[] = [
  { value: 'light', label: 'Light', icon: Sun },
  { value: 'dark', label: 'Dark', icon: Moon },
  { value: 'system', label: 'System', icon: Monitor },
];

export default function AppearancePage() {
  useSeoMeta({ title: 'Appearance · LUMINA' });
  const { theme, setTheme } = useTheme();
  return (
    <>
      <TopBar back="/settings" title="Appearance" />
      <main className="column">
        <SettingsSection title="Theme">
          <div role="radiogroup" aria-label="Theme" className="grid grid-cols-3 gap-2 px-4 pb-3">
            {OPTIONS.map(({ value, label, icon: Icon }) => (
              <button
                key={value}
                type="button"
                role="radio"
                aria-checked={theme === value}
                onClick={() => setTheme(value)}
                className={cn('flex h-24 flex-col items-center justify-center gap-2 rounded-lg border text-body', theme === value ? 'border-accent ring-1 ring-accent' : 'hover:bg-surface-muted')}
              >
                <Icon className="size-6" strokeWidth={1.75} aria-hidden />
                {label}
              </button>
            ))}
          </div>
        </SettingsSection>
      </main>
    </>
  );
}
