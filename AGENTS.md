# LUMINA — Agent Guide (whole repository)

LUMINA is a **Nostr client that looks and feels like Instagram**. Pictures are first-class: every client is built
around **kind 20 picture events ([NIP-68](https://github.com/nostr-protocol/nips/blob/master/68.md))**.

LUMINA ships on several platforms from this one repository. Each app must be a **fully functional, fully implemented
product** — not a demo. Every feature below must work end-to-end against real relays, with real signing, real uploads
and real zaps. No mock data, no placeholder screens, no "coming soon".

This file is the **source of truth for the product** and the rules that apply to every platform. Each app folder has
its own `AGENTS.md` with stack-specific rules. **Always read this file *and* the `AGENTS.md` of the app you are working
in.** If they conflict, the app-level file wins for implementation details; this file wins for product behavior.

## Repository Layout

```
/
├── AGENTS.md          ← this file: product spec + cross-platform rules
├── README.md
├── lumina-web/        ← Web app: React 19 + Vite + Tailwind 4 + shadcn/ui + Nostrify
│   ├── AGENTS.md      ← web stack rules (hooks, Nostr security, querying, testing)
│   └── .agents/skills/← loadable skills for web workflows
└── android/           ← Android app: Kotlin + Jetpack Compose + Quartz (Nostr SDK)
    ├── AGENTS.md      ← Android stack rules
    └── PLAN.md        ← Android implementation plan & progress
```

| App | Folder | Nostr SDK | Status |
| --- | --- | --- | --- |
| Web | `lumina-web/` | [Nostrify](https://nostrify.dev) | in progress |
| Android | `android/` | [Quartz](https://github.com/vitorpamplona/quartz) | planned — see `android/PLAN.md` |

New platforms get their own top-level folder with their own `AGENTS.md` (and `PLAN.md` while being built), and a row in
the table above.

## Working Across the Repo

- **Stay in your lane.** A task for one app should only touch that app's folder, plus this file if product behavior
  changes. Don't refactor another platform's code as a side effect.
- **Product changes are cross-platform.** If you change *what* a feature does (not just how), update this file so the
  other platforms can follow, and mention the parity gap in the relevant app's `PLAN.md` / notes.
- **Protocol compatibility is mandatory.** All apps read and write the *same* events with the *same* tags. A post made
  on Android must render perfectly on web and vice versa. Never invent app-private tags or kinds; if a custom tag/kind
  is unavoidable, document it in a root-level `NIP.md` and implement it on every platform.
- **Verify before committing.** Run the checks of the app you touched (see the app's `AGENTS.md`):
  - Web: `cd lumina-web && npm run test`
  - Android: `cd android && ./gradlew lint testDebugUnitTest assembleDebug`
- **Commits** are scoped and prefixed with the area: `web: …`, `android: …`, `docs: …`, `repo: …`.

## Features (all required on every platform)

| Area | Nostr primitives |
| --- | --- |
| Following feed | kind 20 from `authors` in the user's kind 3 contact list |
| Discover feed | kind 20 globally, newest first; optional `#t` hashtag filter |
| Messages (DMs) | NIP-17 (kind 14 sealed in kind 13, gift-wrapped kind 1059); read legacy NIP-04 kind 4 |
| Profile (own + others) | kind 0 metadata, kind 3 follows, grid of the user's kind 20 posts |
| Post detail | kind 20 + comments, likes, reposts, zaps |
| Create post | Blossom upload → kind 20 with `imeta` tags |
| Notifications | events tagging the user (`#p`): kinds 1111, 7, 16/6, 9735, 3 (new followers) |
| Settings | relays (NIP-65 kind 10002), DM relays (kind 10050), media servers (Blossom kind 10063), wallet (NWC), theme, account |

Deep links: all platforms understand NIP-19 identifiers (`npub`, `nprofile`, `note`, `nevent`, `naddr`) and the
`nostr:` URI scheme, and route them to the matching profile or post screen.

### Login & signing (all platforms)

Users must be able to log in with **remote / external signers** — the private key should not have to live in LUMINA.

| Method | Web | Android |
| --- | --- | --- |
| **NIP-46 bunker** — paste `bunker://…` URI | ✅ required | ✅ required |
| **NIP-46 nostrconnect** — LUMINA shows `nostrconnect://…` (link + QR) for the signer to scan | ✅ required | ✅ required |
| **Amber** | ✅ via NIP-46 (Amber acts as bunker / scans nostrconnect) | ✅ via **NIP-55** (native Android signer intents/content provider) |
| NIP-07 browser extension | ✅ | — |
| nsec / new account | ✅ (warn: stored in browser) | ✅ (stored encrypted with Android Keystore) |

Rules:
- Prefer external signers in the UI ordering: Amber/signer app → bunker → extension → nsec.
- Remote signing is asynchronous and may be denied: every publish path handles "pending approval", "rejected" and
  "signer unreachable" states with clear UI, never a silent failure or a frozen button.
- Multiple accounts with account switching; logging out wipes that account's keys, tokens and decrypted caches.
- Never log, send to analytics, or persist outside secure storage: nsec, bunker secrets, NWC URIs, decrypted DMs.

### Kind 20 picture posts (NIP-68)

- `content` = caption. One or more `imeta` tags (`url`, `m`, `dim`, `x`, `blurhash`, `alt`, optional `fallback`) plus a
  `title` tag. Optional: `t` hashtags, `p` tagged users, `location` / `g`, `content-warning`.
- Multiple `imeta` tags = swipeable carousel with dot indicators.
- **Validate** every kind 20 before rendering: at least one `imeta` with an https `url` and an image mime type. Drop
  invalid events silently.
- Use `dim` for aspect ratio to avoid layout shift; show the `blurhash` (or skeleton) while loading. Fall back to other
  Blossom servers by `x` hash when the primary URL fails.
- Honor `content-warning`: blur the media behind a tap-to-reveal overlay.

### Interactions on a post

- **Likes:** NIP-25 kind 7 (`+`; render custom-emoji reactions too). **Double-tap the image to like**, with a heart-burst
  animation.
- **Comments:** NIP-22 kind 1111 (root `E`/`K`/`P` + parent `e`/`k`/`p` tags), threaded replies.
- **Reposts:** NIP-18 generic repost kind 16 with `k` = `20`.
- **Zaps:** NIP-57 via NWC (NIP-47), with a preset-amount sheet. Show zap total in sats and top zappers.
- **Counts:** fetch kinds 7 / 16 / 1111 / 9735 for a post in **one** combined query and split client-side.
- Share (`nevent` link / native share), bookmark (NIP-51 kind 10003), "…" menu: copy link, view raw event, mute
  (kind 10000), report (NIP-56 kind 1984), delete own post (NIP-09 kind 5).
- Likes, reposts and follows update optimistically and roll back with an error message on failure.

### Feeds

- Infinite scroll with `until` cursors; pull-to-refresh; "new posts" pill when fresh events arrive.
- Following feed: if the user follows nobody or is logged out, show a friendly empty state linking to Discover.
- Discover: 3-column square-crop grid like Instagram Explore; tapping opens post detail. Hashtag search.
- Filter muted pubkeys/words (kind 10000) out of every feed, comment list and notification list.

### Profile

- Header: avatar, display name, NIP-05 badge, bio (with links, mentions, hashtags rendered), website, lightning address,
  posts / followers / following counts.
- Own profile: "Edit profile" (kind 0) and settings shortcut. Others: Follow/Unfollow, Message, Zap.
  **Follow/unfollow always fetches the latest kind 3 first** so a stale list never wipes the user's follows.
- Below: 3-column grid of the user's kind 20 posts; tabs Posts / Reposts / Tagged.

### Create / upload

- Pick one or more images (and camera capture on mobile), preview, reorder, crop, caption, per-image alt text,
  hashtags, location, tagged people, content warning.
- Upload to the user's Blossom servers (kind 10063; fall back to app defaults). **Strip EXIF/GPS before upload.**
  Compute `dim`, `blurhash`, `x` (sha256), `m` for each `imeta`.
- Per-file progress; publish is disabled until every upload succeeds.

### Messages

- NIP-17 private DMs; publish gift wraps to the recipient's kind 10050 relays.
- Conversation list (last message, unread dot) + chat view with bubbles, timestamps and sending state.

### Notifications

- Grouped like Instagram: "alice and 4 others liked your post", with the post thumbnail on the right.
- Types: likes, comments/replies, reposts, zaps (with amount), mentions, new followers.
- "Last seen" timestamp per account drives the unread badge on the nav icon (and system notifications on Android).

### Settings

- Relays: NIP-65 read/write list; DM inbox relays (kind 10050).
- Media servers: Blossom list (kind 10063) — add, remove, reorder, test.
- Wallet: connect/disconnect NWC, default zap amount.
- Appearance: light / dark / system.
- Content: muted users/words, show sensitive content toggle.
- Account: switch accounts, log out, show npub with copy + QR, signer info.

## Navigation & Layout (identical on web and Android)

Web and Android must look and behave like **the same app**. A user switching between them should find every screen,
icon, color and gesture where they expect it. Platform conventions only win where they truly have to (system back,
share sheet, permission prompts).

- **Bottom navigation bar on every platform and every screen size.** That includes desktop web, tablets and foldables.
  There is no sidebar and no navigation rail.
  - Five icons, no labels, in this order: **Home · Discover (search) · Create (+) · Notifications (heart) · Profile (avatar)**.
  - Active tab: the same icon, filled (the profile avatar gets a 1.5px foreground ring). Inactive: outline.
  - Height 56 plus the bottom safe-area inset. Background `surface` with a hairline `border` on top.
  - On wide screens the bar's background spans the full width, but its icons sit inside the centered content column.
  - Unread notifications show a small `like` red dot on the heart icon.
- **Top bar** (56 high, `surface`, hairline bottom border):
  - Home: "LUMINA" wordmark on the left, messages (paper-plane) icon on the right.
  - Other screens: back arrow and title on the left, context actions on the right.
- Messages is reached from the top bar on Home. Settings is reached from the profile header.
- Hide the bottom bar on full-screen flows: create post, chat thread and the media viewer.
- **One centered content column, max 470 wide**, on every screen size. Photos are never stretched beyond it. On wide
  screens the area outside the column is plain `background`.
- Logged-out users can browse Discover, profiles and posts. Any action that needs a signer opens the login flow.

## Shared Design System

These tokens are the **single source of truth**. Web defines them as CSS variables (Tailwind theme in
`lumina-web/src/index.css`). Android defines them in `core/designsystem` (`LuminaTheme`). Both must use exactly these
values. If you change a token, change it here and on both platforms in the same change, or record the gap in the
other app's plan.

### Colors

| Token | Light | Dark | Use |
| --- | --- | --- | --- |
| `background` | `#FFFFFF` | `#000000` | page background |
| `surface` | `#FFFFFF` | `#0A0A0A` | bars, sheets, dialogs |
| `surface-muted` | `#F5F5F5` | `#171717` | inputs, skeletons, chat bubbles (theirs) |
| `foreground` | `#0A0A0A` | `#FAFAFA` | primary text and icons |
| `muted-foreground` | `#737373` | `#A3A3A3` | secondary text, timestamps |
| `border` | `#E5E5E5` | `#262626` | hairline dividers (1px web / 0.5–1dp Android) |
| `primary` | `#0A0A0A` | `#FAFAFA` | primary buttons (Follow, Share, Publish) |
| `on-primary` | `#FFFFFF` | `#0A0A0A` | text on primary |
| `accent` | `#7C3AED` | `#A78BFA` | links, mentions, hashtags, own chat bubbles, focus ring |
| `like` | `#FF3040` | `#FF3040` | filled heart, unread dot |
| `zap` | `#F59E0B` | `#FBBF24` | zap icon and amounts |
| `destructive` | `#DC2626` | `#F87171` | delete, errors |

The brand gradient (`#F59E0B → #EC4899 → #7C3AED`, 45°) is used **only** for the LUMINA wordmark/app icon and the
story-style avatar ring. Nowhere else.

### Typography

- Font: **Inter** on both platforms (web: self-hosted / Google Fonts; Android: bundled `res/font`).
- Scale: wordmark 24/600 · title 16/600 · body 14/400 · username 14/600 · caption & meta 12/400 · count 14/600.
- Respect user font scaling (rem on web, sp on Android).

### Icons

- **Lucide** on both platforms: `lucide-react` on web, and the same Lucide SVGs converted to Compose `ImageVector`s in
  `core/designsystem` on Android. Don't use Material Icons on Android.
- 24 in the bars and post action row, 1.75 stroke. Active/selected means the same glyph filled.
- Mapping: Home `house` · Discover `search` · Create `square-plus` · Notifications `heart` · Messages `send` ·
  Comment `message-circle` · Repost `repeat-2` · Zap `zap` · Bookmark `bookmark` · More `ellipsis` · Settings `settings`.

### Shape, spacing, components

- Spacing scale in multiples of 4. Screen gutter 16, post header padding 12.
- Radius: feed photos 0 (edge-to-edge in the column), cards/inputs/buttons 8, sheets 16 (top corners), avatars and
  pills fully round.
- Avatars: 32 in the post header, 26 in the bottom bar, 86 in the profile header, 44 in lists.
- Profile and Discover grids: 3 columns, square, 2 gap.
- Bottom sheets (not centered dialogs) for post actions, zap amounts and share. They look and behave the same on both
  platforms.
- Motion: 150–250 ms ease-out. Like heart pop, double-tap heart burst, carousel snap.

### Design principles

- **Clean and calm.** Photos are the hero. Chrome is minimal, neutral and thin, with generous whitespace and hairline
  dividers. No heavy shadows, and no gradients except the brand gradient above.
- Never hardcode colors or sizes in components. Always use the tokens.
- Every async view has a **skeleton** matching its final layout, an **empty state**, and an **error state** with retry.
- Accessibility: every image has alt text / content description (from `imeta` `alt` or a sensible fallback), visible
  focus indicators (`accent`), touch targets of at least 44 px / 48 dp, labelled icon buttons, and support for system
  font scaling. Text contrast meets WCAG AA in both themes.

## Security (all platforms)

- Treat every event field as untrusted input. Only allow `https` URLs from events for images, avatars, banners and
  links; never render event content as HTML.
- Filter by `authors` whenever trust is implied (replaceable/addressable events, user-owned lists).
- Validate every incoming event with a type guard / parser before use.

## Definition of Done (any platform)

1. The app's checks pass (see its `AGENTS.md`).
2. The feature works against real relays logged in (with nsec **and** a remote signer) and degrades gracefully when
   logged out.
3. Loading, empty and error states are implemented.
4. It looks right on a small phone and on a large screen, in light and dark mode, and matches the other platform's
   version of the same screen.
5. Events it produces render correctly in the other LUMINA app(s).
