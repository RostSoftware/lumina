# Features

Logged-out users can browse Home (welcome state), Discover, profiles and posts. Every action that needs a signer
(like, comment, repost, zap, follow, bookmark, mute, report, create, messages, notifications) opens the login dialog.

## Login & accounts — `components/auth/LoginDialog.tsx`

Order prefers external signers:

1. **Signer app (NIP-46 nostrconnect)**: LUMINA generates a `nostrconnect://` URI (name "LUMINA", app relays) and
   shows it as a **QR code**, an **Open signer app** link (Amber on Android, nsec.app, …) and **Copy link**. Live status:
   "waiting for your signer…" → "fetching your account…". Errors show a retry button. On mobile a callback to
   `/remoteloginsuccess` is included.
2. **Bunker URI**: paste `bunker://…`, shows "approve in your signer", 60 s timeout, rejection message.
3. **Browser extension (NIP-07)**: disabled with an explanation when no `window.nostr` is present.
4. **Secret key (nsec)**: with a prominent warning that it is stored unencrypted in the browser.
5. **Create a new account**: generates a key, requires download/copy + confirmation, then an optional name/bio
   (kind 0).

Multiple accounts: Settings → Accounts & keys lists all logins, switches the active one, adds accounts and logs
out individual accounts (see [security.md](security.md) for what logout wipes).

## Home — `pages/HomePage.tsx`

- Top bar: LUMINA wordmark (left), messages paper-plane (right) with a red dot when new DMs exist.
- Following feed: kind 20 from the user's kind 3 follows (plus the user's own posts), newest first, infinite scroll
  with `until` cursors, pull-to-refresh (touch), "N new posts" pill (30 s poll) that scrolls up and refreshes.
- Empty states: logged out ("Welcome to LUMINA"), follows nobody, followed people have no photos — all link to Discover.
- Post card: header (avatar 32, name + NIP-05 badge, age, location, "…" menu), carousel, action row
  (like · comment · repost · zap · share ……… bookmark, carousel dots centered), like count, zap total, caption with
  "more", "View all N comments".

## Media — `components/post/*`

- Aspect ratio from the first image's `dim` (clamped 4:5…1.91:1) → no layout shift.
- Blurhash painted on a canvas while loading (skeleton pulse when no blurhash).
- Fallbacks: `imeta fallback` URLs, then every configured Blossom server at `/<sha256>.<ext>` when `x` is present.
- Carousel: CSS scroll-snap swipe, dots, counter badge, prev/next buttons on hover for pointer devices.
- **Double-tap to like** with a heart-burst animation (never un-likes). Single tap opens the full-screen viewer
  (keyboard ← → Esc).
- `content-warning`: blurred behind a "See photo" overlay unless Settings → "Show sensitive content" is on. Grid tiles
  show a blurred tile with an eye-off icon.

## Discover — `pages/DiscoverPage.tsx`

- Search field accepts `#hashtag`, `npub/nprofile/note/nevent/naddr` (navigates), or `name@domain` (NIP-05 → profile).
- Suggested hashtag chips; hashtag header when filtered (`?t=` in the URL so it's linkable).
- 3-column square grid, 2 px gaps, multi-image icon, infinite scroll, pull-to-refresh, new-posts pill.

## Post detail — `pages/PostPage.tsx` (`/nevent1…`, `/note1…`)

- Full post with follow button in the header, likes (tap → likers sheet), reposts, zap total + top zappers
  (tap → zappers sheet with amounts), custom emoji reactions (NIP-30 `emoji` tags), title, caption, hashtags,
  tagged people, location, full date.
- Threaded NIP-22 comments (replies nested, long threads collapsed), "Reply" sets the parent, sticky composer above
  the bottom bar. Comment icon focuses the composer (also when arriving from a feed card).

## Post "…" menu — `PostMenuSheet.tsx`

Save/unsave (kind 10003), Share (native share, copy link, copy `nostr:nevent`, QR), Copy link, Go to post, View raw
event (JSON as text), Mute account (kind 10000), Report (NIP-56 reasons), Delete own post (NIP-09, confirmed).

## Zaps — `ZapSheet.tsx`, `useZap.ts`, `useWallet.ts`

Bottom sheet with presets (21 · 100 · 500 · 1K · 5K · 10K, default preselected), custom amount and comment. Payment:
NWC wallet → WebLN → invoice QR with copy / "open wallet". Shows which wallet will pay and links to wallet settings.
Profile zaps work from the profile header.

## Profile — `pages/ProfilePage.tsx` (`/npub1…`, `/nprofile1…`)

- Header: avatar 86 with brand-gradient story ring, posts / followers / following counts (followers & following open
  user lists), display name + NIP-05 badge, NIP-05, bio (links / mentions / hashtags rendered), website,
  lightning address.
- Own: Edit profile, Share profile (QR + npub), settings gear in the top bar.
- Others: Follow/Following, Message, Zap; "…" menu with QR, copy npub, copy link, mute, report.
- Tabs: Posts (kind 20 by author) · Reposts (kind 16 `k=20`) · Tagged (kind 20 `#p`).

## Create — `pages/CreatePage.tsx`

1. **Select & edit**: pick multiple images or take a photo (`capture="environment"`), up to 10. Per image: crop
   aspect (Original / 1:1 / 4:5 / 16:9), zoom, drag-to-pan, alt text; reorder (← →) and remove.
2. **Details**: uploads start immediately. Each image is re-encoded through a canvas (strips EXIF/GPS, applies
   crop, caps the long edge at 2048 px), then `dim`, `blurhash`, `x` (sha256) and `m` are computed and the blob
   is uploaded to the first Blossom server that accepts it, with **per-file progress** and per-file retry.
   Caption, hashtags (auto-extracted from the caption + manual), location text, optional approximate area
   (geohash precision 5 ≈ ±2.4 km, only with explicit permission), tagged people (npub / NIP-05), content warning.
3. **Share** stays disabled until every upload succeeded for the current crop. Going back and changing a crop
   re-uploads only that image.

## Notifications — `pages/NotificationsPage.tsx`

Events that `#p`-tag the user: likes (7), reposts (6/16), comments / replies / mentions (1111), zaps (9735, with
amount), tags in posts (20), new followers (3). Grouped like Instagram ("alice, bob and 4 others liked your post")
per post, followers per day, sectioned Today / This week / This month / Earlier, post thumbnail on the right,
Follow button on follower rows. Newer-than-last-visit rows are tinted. Opening the screen updates the per-account
"last seen", which drives the red dot on the heart icon (60 s poll).

## Messages — `pages/MessagesPage.tsx`, `pages/ChatPage.tsx`

- NIP-17 private DMs (gift-wrapped, NIP-44) plus read support for legacy NIP-04 kind 4 (marked "NIP-04").
- Conversation list with last message, time and unread dot; "New message" sheet (npub / NIP-05 / follows).
- Chat: bubbles (own = accent, theirs = surface-muted), timestamps every 15 min, sending / failed state with retry
  and a signer-specific error, Enter to send, Shift+Enter newline.
- Sending publishes the recipient's wrap to their kind 10050 relays (fallback: their NIP-65 read relays, then our
  write relays) and a self-copy to our own inbox relays.

## Settings — `pages/settings/*`

| Screen | What it does |
| --- | --- |
| Edit profile | Loads the latest kind 0 from relays, preserves unknown fields, avatar / banner upload (EXIF stripped), name, username, bio, website, NIP-05, lightning address |
| Accounts & keys | npub + QR + copy, signer type (+ warning for nsec), switch / add / log out accounts |
| Relays | NIP-65 list with read/write toggles, defaults, save & publish kind 10002; DM inbox relays (kind 10050) |
| Media servers | Blossom list: add, remove, reorder, test (latency), toggle app defaults, publish kind 10063 |
| Wallet | Connect / disconnect NWC (validated with `get_info`, must allow `pay_invoice`), alias + balance, default zap amount |
| Appearance | Light / Dark / System (default System, follows OS changes live) |
| Muted & sensitive | Show-sensitive toggle, muted words & #hashtags, muted accounts (unmute) |
| Open nostr: links | Registers LUMINA as the browser's `nostr:` protocol handler (`/open?uri=%s`) |

## Deep links

`/npub1…`, `/nprofile1…`, `/note1…`, `/nevent1…` (relay hints are used), `/naddr1…` (kind 20 redirects to the post;
other kinds show "not supported"), and `nostr:`-prefixed variants all resolve. `nsec1…` and unknown strings are 404.
