# LUMINA — Agent Guide

LUMINA is a **Nostr client that looks and feels like Instagram**. Pictures are first-class: the app is built around
**kind 20 picture events ([NIP-68](https://github.com/nostr-protocol/nips/blob/master/68.md))**. It is meant to be a
fully functional, fully implemented application — not a demo. Every feature listed below must work end-to-end against
real relays, with real signing, real uploads and real zaps. No mock data, no placeholder screens, no "coming soon".

## Repository Layout

```
/                    ← this file: product spec + cross-cutting rules
├── README.md
└── lumina-web/      ← the web app (React 19 + Vite + Tailwind 4 + shadcn/ui + Nostrify)
    ├── AGENTS.md    ← stack-level rules (hooks, Nostr security, querying, testing) — READ IT
    ├── .agents/skills/  ← loadable skills for specialized workflows
    └── src/
```

**Before working in `lumina-web/`, read `lumina-web/AGENTS.md`.** It covers the tech stack, `useNostr` / `useAuthor` /
`useNostrPublish` / `useUploadFile`, the Nostr security model (XSS, `sanitizeUrl`, author filtering), query design,
and validation. This file does not repeat that; it defines *what* LUMINA is and how its features must behave.

All commands run from `lumina-web/`:

```bash
npm run dev     # dev server
npm run test    # tsc + eslint + vitest + build — must pass before every commit
npm run build
```

## Features (all required)

| Area | Route (suggested) | Nostr primitives |
| --- | --- | --- |
| Following feed | `/` | kind 20 from `authors` in the user's kind 3 contact list |
| Discover feed | `/discover` | kind 20 globally, newest first; optional `#t` hashtag filter |
| Messages (DMs) | `/messages`, `/messages/:npub` | NIP-17 (kind 14 sealed in kind 13 / gift-wrapped kind 1059); read legacy NIP-04 kind 4 |
| Profile (own + others) | `/:npub` (via `NIP19Page`) | kind 0 metadata, kind 3 follows, kind 20 grid |
| Post detail | `/:nevent` / `/:note` (via `NIP19Page`) | kind 20 + comments, reactions, reposts, zaps |
| Create post | `/create` | Blossom upload → kind 20 with `imeta` tags |
| Notifications | `/notifications` | events tagging the user (`#p`): kinds 1111, 7, 16/6, 9735, 3 (new followers) |
| Settings | `/settings` | relays (NIP-65 kind 10002), media servers (Blossom kind 10063), DM relays (kind 10050), wallet (NWC), theme, account |

### Kind 20 picture posts (NIP-68)

- `content` = caption. Required tags: `title` (may be empty-ish), one or more `imeta` tags (`url`, `m`, `dim`, `x`,
  `blurhash`, `alt`, optional `fallback`). Optional: `t` hashtags, `p` tagged users, `location`/`g`, `content-warning`.
- Multiple `imeta` tags = carousel. Render with an Embla carousel and dot indicators.
- **Validate** every kind 20 before rendering: must have at least one `imeta` with a sanitized https `url`
  and an image mime type. Drop invalid events silently.
- Respect `dim` for aspect ratio to avoid layout shift; show `blurhash` (or a skeleton) while loading. Use
  Blossom fallback (`x` hash) when the primary URL fails.
- Honor `content-warning`: blur the media behind a tap-to-reveal overlay.

### Interactions on a post

- **Likes:** NIP-25 kind 7 (`+` content; render custom emoji reactions too). Double-tap on the image likes, with a
  heart burst animation, exactly like Instagram.
- **Comments:** NIP-22 kind 1111 (root `E`/`K`/`P` + parent `e`/`k`/`p` tags). Threaded replies. Load the
  **`nostr-comments`** skill.
- **Reposts:** NIP-18 generic repost kind 16 with `k` = `20`.
- **Zaps:** NIP-57 via NWC (load the **`nwc`** skill). Show zap total in sats and top zappers.
- **Counts:** fetch kinds 7/16/1111/9735 for a post in *one* combined query and split in JS. Consider the
  **`nip85-stats`** skill for aggregate counts in feeds.
- Share (copy `nevent` link / native share), bookmark (NIP-51 kind 10003), and a "…" menu (copy link, view raw event,
  mute user via kind 10000, report via NIP-56 kind 1984, delete own post via NIP-09 kind 5).

### Feeds

- Infinite scroll with `useInfiniteQuery` and `until` cursors — load the **`nostr-infinite-scroll`** skill.
- Following feed: if the user follows nobody or is logged out, show a friendly empty state linking to Discover.
- Discover: grid (3 columns, square crops) like Instagram Explore; tapping opens post detail.
- Filter out muted pubkeys (kind 10000) in every feed, comment list and notification list.
- Pull-to-refresh on touch devices; "new posts" pill when fresh events arrive.

### Profile

- Header: avatar, display name, NIP-05 badge, bio (`about`, rendered via the `note-content` skill), website,
  lightning address, posts / followers / following counts.
- Own profile: "Edit profile" (load the **`edit-profile`** skill) and a settings shortcut. Others: Follow/Unfollow
  (kind 3 — always fetch the latest list before mutating so follows are never wiped), Message, Zap.
- Below the header: 3-column square grid of the user's kind 20 posts; tabs for Posts / Reposts / Tagged.

### Create / upload

- Pick one or more images (and camera capture on mobile), preview, reorder, crop to aspect ratio, write caption,
  add alt text per image, hashtags, location, tagged people, content warning.
- Upload to the user's Blossom servers (kind 10063, falling back to app defaults) — load the **`file-uploads`** skill.
  Strip EXIF/GPS metadata before upload (privacy). Compute `dim`, `blurhash`, `x`, `m` for each `imeta`.
- Show per-file upload progress; disable publish until every upload succeeds.

### Messages

- NIP-17 private DMs (load the **`nostr-encryption`** skill). Publish to the recipient's kind 10050 relays.
- Conversation list (latest message, unread dot) + chat view with bubbles, timestamps, and sending state.
- Never log or persist decrypted content outside of memory/query cache.

### Notifications

- Grouped like Instagram: "alice and 4 others liked your post", with post thumbnail on the right.
- Types: likes, comments/replies, reposts, zaps (amount), mentions, new followers.
- Track a "last seen" timestamp (local storage per account) to show a badge on the nav icon.

### Settings

- Relays: NIP-65 read/write list (load the **`relay-management`** skill). DM inbox relays (kind 10050).
- Media servers: Blossom list (kind 10063) — add, remove, reorder, test.
- Wallet: connect/disconnect NWC, default zap amount.
- Appearance: light / dark / system (load the **`theming`** skill).
- Content: muted users/words, show sensitive content toggle.
- Account: switch accounts, log out, show npub with copy + QR.

## Navigation & Layout

- **Bottom navigation bar** (Instagram-style), fixed, five icons, no labels:
  **Home · Discover (search) · Create (+) · Notifications (heart) · Profile (avatar)**.
  Active tab gets the filled icon variant. Respect `env(safe-area-inset-bottom)`.
- Messages is reached from the top-right paper-plane icon on the Home header. Settings from the profile header.
- Top bar on Home: "LUMINA" wordmark left, notifications/messages icons right.
- Hide the bottom bar on full-screen flows (create post, chat thread, media viewer).
- **Mobile-first.** On wide screens (≥ `md`), center content in a ~470px feed column; on `lg`+ switch the bottom bar
  to a left sidebar rail with labels. Never stretch images across a desktop viewport.
- Logged-out users can browse Discover, profiles and posts; actions that need a signer open the `AuthDialog`.

## Design Principles

- **Clean and calm.** Content (photos) is the hero; chrome is minimal, neutral, and thin. Lots of whitespace,
  1px hairline dividers, no heavy shadows or gradients except on the brand mark.
- Typography: one sans-serif family, clear hierarchy (14px body, 600-weight usernames).
- Icons: `lucide-react`, consistent 24px stroke icons in the nav.
- Full light and dark mode support; define colors as CSS variables, never hardcode hex in components.
- Every async view has a proper **skeleton** that matches the final layout and a meaningful **empty state**.
- Micro-interactions: like heart pop, button press feedback, smooth carousel. Keep them subtle and fast (<250ms).
- Accessibility: every image needs `alt` (use `imeta` `alt` or a sensible fallback), visible focus rings,
  44px minimum touch targets, labelled icon buttons (`aria-label`).

## Code Conventions

- TypeScript strict; **never `any`**. Validate all incoming events with type guards.
- One hook per data concern in `src/hooks/` (e.g. `usePictureFeed`, `usePostInteractions`, `useNotifications`,
  `useConversations`). Components stay presentational where practical.
- Feature components in `src/components/<feature>/` (`feed/`, `post/`, `profile/`, `messages/`, `notifications/`,
  `create/`, `settings/`, `layout/`). Pages in `src/pages/`.
- Add routes in `src/AppRouter.tsx` **above** the `/:nip19` catch-all.
- Optimistic updates for likes, reposts and follows; roll back on publish failure with a toast.
- Sanitize every URL from an event (`sanitizeUrl`) — images, avatars, banners, websites. No
  `dangerouslySetInnerHTML`. Do not relax the CSP in `index.html` except to add specific, required `img-src`/`connect-src`
  origins if strictly necessary.
- If a custom tag or kind is ever introduced, document it in `lumina-web/NIP.md`.

## Definition of Done

A change is done when:

1. `npm run test` passes in `lumina-web/` (types, lint, tests, build).
2. The feature works against real relays when logged in **and** degrades gracefully when logged out.
3. Loading, empty and error states are implemented.
4. It looks right on a 375px phone viewport and on desktop, in light and dark mode.
