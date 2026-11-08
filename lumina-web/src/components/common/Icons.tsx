import { Heart, House, Search, SquarePlus, Plus, type LucideProps } from 'lucide-react';

/**
 * LUMINA icon conventions (Lucide, 24px, 1.75 stroke). Active = filled glyph.
 * Some glyphs need a custom filled rendering so inner details stay visible.
 */
export const ICON_STROKE = 1.75;

export function HomeIcon({ active, ...props }: LucideProps & { active?: boolean }) {
  return <House strokeWidth={ICON_STROKE} fill={active ? 'currentColor' : 'none'} {...props} />;
}

export function DiscoverIcon({ active, ...props }: LucideProps & { active?: boolean }) {
  // A filled magnifier is unreadable; Instagram-style active search uses a heavier stroke.
  return <Search strokeWidth={active ? 2.75 : ICON_STROKE} {...props} />;
}

export function CreateIcon({ active, size = 24, ...props }: LucideProps & { active?: boolean }) {
  if (!active) return <SquarePlus size={size} strokeWidth={ICON_STROKE} {...props} />;
  return (
    <span className="relative inline-flex" style={{ width: Number(size), height: Number(size) }}>
      <SquarePlus size={size} strokeWidth={ICON_STROKE} fill="currentColor" {...props} />
      <Plus size={Number(size) * 0.5} strokeWidth={2.5} className="absolute inset-0 m-auto text-background" aria-hidden />
    </span>
  );
}

export function HeartIcon({ active, ...props }: LucideProps & { active?: boolean }) {
  return <Heart strokeWidth={ICON_STROKE} fill={active ? 'currentColor' : 'none'} {...props} />;
}
