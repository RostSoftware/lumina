# Design system (web)

The tokens are defined once in the root `AGENTS.md` and implemented in `src/index.css`. Android mirrors the same
values. **Never hardcode colours or sizes in components** — use the Tailwind utilities generated from the tokens.

## Colour tokens → Tailwind

| Token | Light | Dark | Utility |
| --- | --- | --- | --- |
| background | `#FFFFFF` | `#000000` | `bg-background` |
| surface | `#FFFFFF` | `#0A0A0A` | `bg-surface` (bars, sheets, dialogs) |
| surface-muted | `#F5F5F5` | `#171717` | `bg-surface-muted` (inputs, skeletons, their chat bubbles) |
| foreground | `#0A0A0A` | `#FAFAFA` | `text-foreground` |
| muted-foreground | `#737373` | `#A3A3A3` | `text-muted-foreground` |
| border | `#E5E5E5` | `#262626` | `border` (default border colour) |
| primary / on-primary | `#0A0A0A` / `#FFFFFF` | `#FAFAFA` / `#0A0A0A` | `bg-primary text-primary-foreground` |
| accent | `#7C3AED` | `#A78BFA` | `text-accent`, `bg-accent text-accent-foreground`, focus ring |
| like | `#FF3040` | `#FF3040` | `text-like`, `bg-like` |
| zap | `#F59E0B` | `#FBBF24` | `text-zap`, `fill-zap` |
| destructive | `#DC2626` | `#F87171` | `text-destructive`, `bg-destructive` |

shadcn/ui aliases (`card`, `popover`, `secondary`, `muted`, `input`, `ring`) are mapped onto these tokens. Because
LUMINA's `accent` is purple, the shadcn primitives in `components/ui/` were changed to use `bg-muted` (not
`bg-accent`) for hover/focus backgrounds. Keep that convention when adding new primitives.

**Brand gradient** (`brand-gradient`, `brand-gradient-text` utilities): only the wordmark (`Wordmark.tsx`), the app
icon (`public/icon.svg`) and the profile avatar story ring (`UserAvatar ring`).

## Typography

Inter Variable (self-hosted). Utilities: `text-wordmark` (24/600), `text-title` (16/600), `text-body` (14/400),
`text-meta` (12/400). Usernames and counts use `text-body font-semibold`. Sizes are in rem, so browser font scaling
works.

## Theme

`light` / `dark` / `system` (default). `AppProvider` toggles the `.dark` class on `<html>` and follows OS changes
when on System. `color-scheme` is set so native controls match.

## Layout rules

- `--bar-height: 56px`, `--column-width: 470px`. Utility `column` = centered, max 470 px. Every screen renders its
  content inside one column on every screen size; outside is plain `background`.
- **Bottom navigation on every screen size** (`BottomNav.tsx`): Home · Discover · Create · Notifications · Profile,
  no labels, filled glyph when active (Create uses a filled square with a background-coloured plus; Discover uses a
  heavier stroke since a filled magnifier is unreadable), profile avatar 26 with a 1.5 px foreground ring when active,
  `like` dot on the heart for unread notifications. Bar background spans the full width; icons sit in the column.
  Height = 56 + safe-area inset.
- **Top bar** (`TopBar.tsx`): 56 high, sticky, `surface`, hairline bottom border. Home shows the wordmark + messages
  icon; other screens a back arrow + title + actions.
- Full-screen flows (`/create`, `/messages/:npub`, media viewer) hide the bottom bar (`AppShell.isFullScreen`).
- Toasts sit just above the bottom bar, inside the column.

## Components & sizes

| Thing | Spec |
| --- | --- |
| Avatars | 32 post header · 26 bottom bar · 86 profile header · 44 lists (`UserAvatar size`) |
| Icons | Lucide, 24 px, stroke 1.75 (`ICON_STROKE`) in bars and action rows |
| Radius | feed photos 0 · cards/inputs/buttons 8 (`rounded-lg`) · sheets 16 top corners · avatars/pills full |
| Grids | 3 columns, square, 2 px gap (`PostGrid`) |
| Spacing | multiples of 4; screen gutter 16 (`px-4`), post header 12 (`px-3`) |
| Touch targets | icon buttons are 44×44 (`IconButton`, action row) |
| Sheets | `BottomSheet` (vaul) for post actions, share, zap, report, raw event, user lists, new message — same on desktop |
| Motion | 150–250 ms ease-out; `animate-heart-pop` (like), `animate-heart-burst` (double tap); `prefers-reduced-motion` disables animations |

## States

Every async view has a skeleton matching its final layout (`PostCardSkeleton`, `PostGridSkeleton`, list skeletons),
an `EmptyState`, and an `ErrorState` with retry.

## Accessibility

Images get alt text from `imeta alt`, then the post title, then "Photo by <name>". Icon buttons have `aria-label`s;
toggles use `aria-pressed`; tabs/radios use proper roles; focus ring is `accent` (`:focus-visible`).
