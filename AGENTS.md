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

## Navigation & Layout

- **Bottom navigation bar** (Instagram-style), five icons, no labels:
  **Home · Discover (search) · Create (+) · Notifications (heart) · Profile (avatar)**.
  Active tab uses the filled icon variant. Respect system safe areas / insets.
- Messages is reached from the paper-plane icon at the top-right of Home. Settings from the profile header.
- Home top bar: "LUMINA" wordmark left, messages icon right.
- Hide the bottom bar on full-screen flows (create post, chat thread, media viewer).
- Large screens (desktop web, tablets, foldables): a centered ~470dp feed column; on very wide layouts the bottom bar
  becomes a side navigation rail. Never stretch photos across the full width of a large screen.
- Logged-out users can browse Discover, profiles and posts; any action that needs a signer opens the login flow.

## Design Principles (all platforms)

- **Clean and calm.** Photos are the hero; chrome is minimal, neutral and thin. Generous whitespace, hairline dividers,
  no heavy shadows or gradients except on the brand mark.
- One sans-serif type family with a clear hierarchy (≈14 body, semibold usernames).
- Consistent outline icon set (filled variant for active/selected).
- Full light and dark themes driven by design tokens — never hardcode colors in components. Web and Android share the
  same palette values.
- Every async view has a **skeleton** matching its final layout, an **empty state**, and an **error state** with retry.
- Subtle, fast micro-interactions (< 250 ms): like-heart pop, press feedback, smooth carousel.
- Accessibility: every image has alt text / content description (from `imeta` `alt` or a sensible fallback), visible
  focus indicators, ≥ 44–48 px/dp touch targets, labelled icon buttons, supports system font scaling.

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
4. It looks right on a small phone and on a large screen, in light and dark mode.
5. Events it produces render correctly in the other LUMINA app(s).
