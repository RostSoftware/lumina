# LUMINA Android — Implementation Plan

Goal: a fully functional, polished native Android app with feature parity with the root `/AGENTS.md` spec, built on
Kotlin + Jetpack Compose + [Quartz](https://github.com/vitorpamplona/quartz).

Status legend: `[ ]` todo · `[~]` in progress · `[x]` done. Update this file in the same commit as the work.

**Status (2026-11-08):** all features implemented; `./gradlew lint testDebugUnitTest assembleDebug` passes.
Remaining work is device verification, parity and polish items marked below. Documentation: [`docs/`](docs/README.md).

---

## Phase 0 — Project setup

- [x] Gradle project in `android/` (Kotlin DSL, version catalog `gradle/libs.versions.toml`, Gradle wrapper committed)
- [x] Modules: `app`, `core/{nostr,signer,data,media,wallet,designsystem,ui}`, `feature/*` (`core/ui` holds Nostr-aware shared UI)
- [x] Dependencies: Quartz (`com.vitorpamplona.quartz:quartz`, latest Maven Central release), Compose BOM, Material 3,
      Hilt, Navigation Compose, Coil 3, DataStore, WorkManager, OkHttp, kotlinx-serialization, CameraX
- [x] `minSdk 26`, latest `compileSdk`/`targetSdk`, Java 17
- [x] CI: GitHub Actions job that runs `./gradlew lint testDebugUnitTest assembleDebug` on changes in `android/**`
- [x] App icon, splash screen, app name "LUMINA", application id `rocks.lumina.app`

## Phase 1 — Foundations

- [x] **Design system:** tokens from the root `AGENTS.md` "Shared Design System" (colors, Inter, Lucide
      `ImageVector`s, shapes, spacing); shared components:
      `Avatar`, `PostImage` (aspect ratio from `dim`, blurhash placeholder, Blossom fallback), `Skeleton`, `EmptyState`,
      `ErrorState`, `LuminaTopBar`, `LuminaBottomBar`
- [x] **Navigation shell:** bottom bar (Home · Discover · Create · Notifications · Profile) on all window sizes,
      centered 470dp content column,
      type-safe routes, hidden bar on full-screen flows, edge-to-edge + predictive back
- [x] **Nostr core:** singleton `NostrClient`, default bootstrap relays, NIP-65 relay list loading, outbox routing,
      lifecycle-aware subscriptions, NIP-42 auth
- [x] **Cache:** Quartz `SQLiteEventStore` (one database per account) for events, profiles and relay lists;
      cache-first repositories
- [x] **Event models/validation:** kind 20 parser (`imeta`, `title`, `t`, `p`, `content-warning`), profile metadata,
      contact list, reactions, reposts, comments, zap receipts
- [x] **Safe content renderer:** caption/bio with links, `nostr:` mentions, hashtags, custom emoji

## Phase 2 — Accounts & login

- [x] Account store (multi-account, Keystore-encrypted secrets, DataStore metadata)
- [x] **Amber (NIP-55)** via `NostrSignerExternal`: detection, `get_public_key`, permission request, content-provider
      background signing + intent fallback
- [x] **NIP-46 bunker** (`bunker://` paste or QR scan) via Quartz `nip46RemoteSigner`
- [x] **NIP-46 nostrconnect** (`nostrconnect://` QR + "open in signer"). Needs the handshake glue (see gaps)
- [x] nsec import + new account creation (generate key, set name/avatar, back-up-key flow)
- [x] Signer state UX: pending / rejected / timeout / unreachable
- [x] Account switcher + logout (wipes secrets and the account's cache)
- [x] Logged-out browsing mode, with a login prompt on any signed action

## Phase 3 — Viewing

- [x] **Following feed:** kind 20 from contacts, infinite scroll (`until`), pull-to-refresh, "new posts" pill,
      empty state → Discover
- [x] **Post card:** header (avatar, name, NIP-05, time, "…"), image carousel with dots, double-tap-to-like heart burst,
      action row (like, comment, repost, zap, share, bookmark), counts, caption, "view all N comments"
- [x] **Discover:** 3-column grid, hashtag search, user search (NIP-50 where relays support it)
- [x] **Post detail:** full post + threaded NIP-22 comments + reactions / reposts / zaps lists
- [x] **Profile:** header, stats, bio, follow/unfollow (fresh kind 3 before mutate), message, zap; tabs
      Posts / Reposts / Tagged; 3-column grid
- [x] Content warnings (blur + reveal), mute list filtering everywhere
- [x] Full-screen media viewer with pinch-zoom

## Phase 4 — Interactions & publishing

- [x] Like (kind 7), unlike via delete (kind 5), optimistic update + rollback
- [x] Repost (kind 16, `k`=20)
- [x] Comment / reply (kind 1111) with mentions autocomplete
- [x] Bookmark (kind 10003), mute (kind 10000), report (kind 1984), delete own post (kind 5)
- [x] Share: `nevent` link + Android share sheet; deep link handling for `nostr:` / NIP-19
- [x] Edit own profile (kind 0) incl. avatar/banner upload

## Phase 5 — Create post

- [x] Photo Picker (multi-select) + camera capture (system camera via `TakePicture`, not an in-app CameraX viewfinder) + share-sheet intake
- [x] Reorder, crop/aspect ratio, per-image alt text
- [x] Caption, hashtags, tag people, location, content warning
- [x] Blossom upload (BUD-01/02 with kind 24242 auth). HTTP client is our own (see gaps) to the user's kind 10063 servers with default fallback;
      EXIF/GPS stripping; compute `dim`, `blurhash`, `x`, `m`; per-file progress
- [x] Publish kind 20 via WorkManager (survives process death, retries), outbox relays

## Phase 6 — Zaps & wallet

- [x] NWC connect (paste/scan `nostr+walletconnect://`), stored encrypted
- [x] Zap sheet with preset amounts + comment; LNURL resolution from `lud16`; NIP-57 zap request → NWC `pay_invoice`
- [x] Zap receipts (kind 9735) display, totals, top zappers
- [x] Fallback: open external Lightning wallet via `lightning:` intent when no NWC

## Phase 7 — Messages

- [x] NIP-17 send/receive (gift wrap → seal → kind 14) via Quartz `nip17Dm` / `nip59Giftwrap`
- [x] Read legacy NIP-04 kind 4
- [x] Conversation list (last message, unread), chat screen (bubbles, timestamps, sending/failed state)
- [x] Publish to the recipient's kind 10050 relays; manage own DM relays
- [x] Decryption happens off the main thread, and decrypted content is never written unencrypted to disk

## Phase 8 — Notifications

- [x] In-app notifications tab: likes, comments, reposts, zaps, mentions, new followers; Instagram-style grouping with
      thumbnails
- [x] Unread badge from per-account "last seen"
- [x] System notifications (channels per type) via WorkManager periodic sync; tapping a notification deep-links to the
      relevant screen

## Phase 9 — Settings

- [x] Relays (NIP-65 read/write), DM relays (10050)
- [x] Media servers (Blossom 10063): add/remove/reorder/test
- [x] Wallet (NWC), default zap amount
- [x] Appearance: light/dark/system
- [x] Content: muted users/words, sensitive-content toggle
- [x] Account: npub copy + QR, signer info, back up key (internal signer only), log out

## Phase 10 — Polish & release

- [x] Skeleton, empty and error states on every screen
- [~] Accessibility: labelled icon buttons, alt text from `imeta`, 48 dp targets, sp fonts, focus ring. A TalkBack walkthrough on a device is still pending
- [~] Large screens: bottom bar + centered 470 dp column implemented everywhere; not yet verified on a tablet/foldable
- [ ] Side-by-side visual parity check against LUMINA web for every screen (light + dark) — needs a device pass
- [~] Performance: subscriptions are scoped to ViewModels and closed on cancel; Coil memory/disk caches tuned. Baseline profile not added yet
- [ ] Cross-client check: posts, comments, zaps, DMs created on Android render correctly on LUMINA web, and vice versa — needs a device pass against real relays
- [~] R8/minify config done (`app/proguard-rules.pro`); release signing config and Play Store assets still to do

---

## Open questions / decisions

- Multi-module, as planned. A `core/ui` module was added for shared Nostr-aware composables (post card, sheets, PostActionsViewModel).
- Quartz NIP-46: bunker login is covered by `NostrSignerRemote.fromBunkerUri`. The nostrconnect handshake is our own
  code (gap #5).
- Push notifications: periodic WorkManager polling first. Revisit later (e.g. a notification relay/server).

## Quartz gaps / local extensions

Checked against Quartz `main` @ `24fa80d` (2026-11-07). Quartz covers every Nostr protocol piece we need: kind 20
`PictureEvent`, NIP-22/25/18/09/56/36/51/65/50/05/42, NIP-17 + NIP-04 DMs, NIP-57 zap requests + receipt validation,
NIP-47 request/response models, NIP-46 remote signer (bunker), NIP-55 Amber (`NostrSignerExternal`,
`IsExternalSignerInstalled`, `ExternalSignerLogin`), and an SQLite event store. These pieces are missing and must be built
in LUMINA. Keep each one behind an interface, and consider upstreaming it to Quartz.

| # | Gap | Quartz provides | We build | Module | Effort | Status |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | **Blossom HTTP client** | `BlossomAuthorizationEvent` (kind 24242 upload/get/list/delete auth), `BlossomServersEvent` (10063), `BlossomUploadResult` parsing, `BlossomServerUrl` | OkHttp `PUT /upload` with auth header, progress callbacks, `HEAD`/`GET` by sha256 for fallback, server-list failover, optional `PUT /mirror` | `core/media` | M | [x] |
| 2 | **Blurhash encode/decode** | Only `BlurhashTag` (the field) | Encoder at upload time + decoder for placeholders (small library or ~150 lines), integrated with Coil | `core/media`, `core/designsystem` | S | [x] |
| 3 | **LNURL-pay HTTP** | `Lud06` decode, `LnInvoiceUtil` (bolt11 parse/amount), `LnurlEndpointResolver` *interface* | OkHttp resolver: `lud16` → `.well-known/lnurlp`, call `callback` with `amount` + `nostr=` zap request, return/validate invoice amount | `core/wallet` | S | [x] |
| 4 | **NWC transport** | `Nip47Client` builds `NwcRequestEvent`s, decrypts responses/notifications, provides filters | Publish requests to the wallet relay via `NostrClient`, subscribe `responseFilter`, await with timeout, map errors | `core/wallet` | S | [x] |
| 5 | **nostrconnect handshake (client-initiated NIP-46)** | `NostrConnectURI.buildNostrConnect` / parsing, `NostrSignerRemote` (works once remote pubkey is known) | Generate ephemeral keypair + secret → build URI → subscribe kind 24133 on the URI relays → verify `connect` response secret → create `NostrSignerRemote` with the signer's pubkey → `getPublicKey()` | `core/signer` | S–M | [x] |
| 6 | **Metadata (EXIF/GPS) stripping** | `BuzzMediaSanitizer` strips JPEG/PNG metadata, but it is internal to another feature and its API may change | LUMINA `MediaSanitizer`: strip JPEG/PNG (adapt that approach), and re-encode HEIC/WebP and other formats through `Bitmap`; apply orientation before stripping | `core/media` | S | [x] |

Where the gaps live: Blossom → `core/media/BlossomClient.kt`; blurhash → `core/media/Blurhash.kt`; LNURL →
`core/wallet/LnurlClient.kt`; NWC transport → `core/wallet/NwcRepository.kt`; nostrconnect →
`core/signer/NostrConnectSession.kt`; metadata stripping → `core/media/ImageProcessor.kt` (re-encode via `Bitmap`).

Plain Android work (not Quartz's job): image dimensions (`dim`), crop/resize, CameraX, Photo Picker, system notifications.
