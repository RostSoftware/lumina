# Features

Every feature from the root `AGENTS.md` table, as implemented on Android.

## Navigation shell (`app/`)
- Bottom bar on all window sizes: Home · Discover · Create · Notifications (red unread dot) · Profile (avatar).
  Tabs keep their back stacks (`saveState`/`restoreState`). Hidden on Create, Chat, Media viewer and Login.
- Centered 470 dp content column, edge-to-edge with correct insets, predictive back (`enableOnBackInvokedCallback`).
- Global "Waiting for your signer…" pill; logged-out actions that need a signer open Login.
- Deep links: `nostr:` URIs (npub, nprofile, note, nevent, naddr*), `https://lumina.rocks/<nip19>` (verified App
  Links), `/discover?t=…`, `/messages/<npub>`. Notification taps open the post, profile, Messages or Notifications.
  (*naddr is parsed but there are no addressable picture screens, so it shows "can't be opened".)
- Share sheet: images shared from other apps open Create (content:// only, up to 10).
- Splash screen, adaptive icon with the brand gradient (+ monochrome), light/dark/system theme.

## Home — following feed (`feature/feed`)
Kind 20 from the kind 3 contacts using the outbox model. Cache first, then relays. Infinite scroll with `until`
cursors, pull-to-refresh, a live "new posts" pill (avatars of new authors). Empty states: logged out → log in /
Discover; follows nobody → Discover; no posts. Messages icon with the unread-DM dot.

## Post card (`core/ui`)
Header (avatar, name, NIP-05 badge, location, "…"), swipeable carousel with dots and an n/N counter, aspect ratio
from `dim` (no layout shift), blurhash/skeleton placeholder, Blossom fallback by `x` hash, double-tap heart burst,
tap → full-screen viewer, content-warning blur with tap-to-reveal (or always shown via settings). Action row: like
(filled red + pop), comment, repost, zap, share, bookmark. Like count (→ likers list), zap total + top zappers,
caption with links/mentions/hashtags/custom emoji ("more"), "View all N comments", relative time.

"…" menu: copy link, share, go to profile, view raw event, mute/unmute author, report (NIP-56), delete own post (NIP-09, with confirmation).

## Discover (`feature/discover`)
3-column square grid of global kind 20, infinite scroll, pull-to-refresh, new-posts pill. Search: accounts (NIP-50 +
npub/NIP-05 lookup + cached matches) and hashtags (`#t` grid). Hashtag deep links open directly.

## Post detail (`feature/post`)
Full post plus NIP-22 comments, threaded two levels (replies at any depth grouped under the top-level comment),
reply, @mention autocomplete (inserts `nostr:npub…` and adds a `p` tag), delete own comments, likes / reposts / zaps lists sheet.
Media viewer: pinch-zoom, pan, double-tap zoom, swipe between images, alt text.

## Create (`feature/create`)
Photo Picker multi-select (≤10), camera capture, share intake → reorder, remove, crop (Original/1:1/4:5/16:9 with
pan+zoom), per-image alt → caption, hashtags, tag people, location, content warning. Images are processed on the device
(orientation applied, ≤2048 px, re-encoded, which strips EXIF/GPS) and uploaded to the user's Blossom servers with
per-file progress and retry. Publish stays disabled until every upload has succeeded. The kind 20 is signed in the foreground and then published
by WorkManager, so it survives process death. The draft survives rotation and process death.

## Profile (`feature/profile`)
Header: avatar (gradient ring), banner, counts (posts loaded, followers ≤500+, following), name + NIP-05 badge, bio
(rich text), website, lightning address. Own profile: Edit profile, Share profile, settings icon. Others: Follow/
Following (newest kind 3 fetched first, optimistic with rollback), Message, Zap, menu (copy npub, share, mute,
report). Tabs Posts / Reposts / Tagged as 3-column grids. Edit profile: all kind 0 fields, avatar/banner upload
to Blossom. Unknown fields are kept. Followers/Following lists with follow buttons.

## Messages (`feature/messages`)
NIP-17 inbox (gift wraps on the user's kind 10050 relays) plus legacy NIP-04 kind 4 (labelled), decrypted off the main
thread and kept in memory only. Conversation list with last message, time and unread dot. New message via user search. Chat:
bubbles (own = accent), day separators, sending/failed state with tap-to-retry, mark-as-read. Sends gift wraps to the
recipient's 10050 relays and a copy to our own.

## Notifications (`feature/notifications`)
Grouped like Instagram ("alice and 4 others liked your post"): likes, comments, reposts, zaps (amount), mentions
(tagged in a post), new followers. Post thumbnail on the right, "New" vs "Earlier" sections, infinite scroll,
pull-to-refresh. The unread badge is driven by the per-account "last seen" timestamp. The background worker (15 min)
posts system notifications in per-type channels (likes, comments, reposts, zaps, mentions, follows, messages).
Message notifications contain no sender or content.

## Settings (`feature/settings`)
- **Relays:** NIP-65 read/write editor and the DM inbox relay list (10050).
- **Media servers:** Blossom 10063. Add, remove, reorder and test servers; toggle "also use LUMINA defaults".
- **Wallet:** NWC connect (paste or scan), balance, disconnect, default zap amount.
- **Appearance:** light/dark/system.
- **Content & safety:** muted users/words/hashtags, sensitive-content toggle, system notifications toggle.
- **Account:** switch or add accounts, npub with copy and QR, signer info, back up key (nsec accounts only, behind
  confirmation and with screenshots blocked), log out. Logging out wipes that account's keys, cache and settings.

## Login (`feature/auth`)
Signer apps (NIP-55, one button per installed app) → bunker (paste/scan) → nostrconnect (QR + open in signer) →
nsec → create account (name → key backup). See [signers-and-security.md](signers-and-security.md).

## Zaps (`core/wallet`)
Zap sheet with presets (21, 100, 500, 1k, 5k, 10k), a custom amount and an optional comment. LNURL is resolved from lud16/lud06, and a kind 9734 request
is signed with Quartz (not published) and sent to the callback. The invoice amount is validated, then paid via NWC. With no wallet
connected, the invoice opens in an external Lightning app (`lightning:`).
