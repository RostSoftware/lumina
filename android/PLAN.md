# LUMINA Android — Implementation Plan

Goal: a fully functional, polished native Android app with feature parity with the root `/AGENTS.md` spec, built on
Kotlin + Jetpack Compose + [Quartz](https://github.com/vitorpamplona/quartz).

Status legend: `[ ]` todo · `[~]` in progress · `[x]` done. Update this file in the same commit as the work.

---

## Phase 0 — Project setup

- [ ] Gradle project in `android/` (Kotlin DSL, version catalog `gradle/libs.versions.toml`, Gradle wrapper committed)
- [ ] Modules: `app`, `core/{nostr,signer,data,media,wallet,designsystem}`, `feature/*`
- [ ] Dependencies: Quartz (`com.vitorpamplona.quartz:quartz`, latest Maven Central release), Compose BOM, Material 3,
      Hilt, Navigation Compose, Coil 3, Room, DataStore, WorkManager, OkHttp, kotlinx-serialization, CameraX
- [ ] `minSdk 26`, latest `compileSdk`/`targetSdk`, Java 17
- [ ] CI: GitHub Actions job that runs `./gradlew lint testDebugUnitTest assembleDebug` on changes in `android/**`
- [ ] App icon, splash screen, app name "LUMINA"

## Phase 1 — Foundations

- [ ] **Design system:** color tokens (light/dark, matching web), typography, shapes, spacing; shared components:
      `Avatar`, `PostImage` (aspect ratio from `dim`, blurhash placeholder, Blossom fallback), `Skeleton`, `EmptyState`,
      `ErrorState`, `LuminaTopBar`, `LuminaBottomBar`
- [ ] **Navigation shell:** bottom bar (Home · Discover · Create · Notifications · Profile), nav rail on wide screens,
      type-safe routes, hidden bar on full-screen flows, edge-to-edge + predictive back
- [ ] **Nostr core:** singleton `NostrClient`, default bootstrap relays, NIP-65 relay list loading, outbox routing,
      lifecycle-aware subscriptions, NIP-42 auth
- [ ] **Cache:** Room schema for events + profiles + relay lists; cache-first repositories
- [ ] **Event models/validation:** kind 20 parser (`imeta`, `title`, `t`, `p`, `content-warning`), profile metadata,
      contact list, reactions, reposts, comments, zap receipts
- [ ] **Safe content renderer:** caption/bio with links, `nostr:` mentions, hashtags, custom emoji

## Phase 2 — Accounts & login

- [ ] Account store (multi-account, Keystore-encrypted secrets, DataStore metadata)
- [ ] **Amber (NIP-55)** via `NostrSignerExternal`: detection, `get_public_key`, permission request, content-provider
      background signing + intent fallback
- [ ] **NIP-46 bunker** (`bunker://` paste or QR scan) via Quartz `nip46RemoteSigner`
- [ ] **NIP-46 nostrconnect** (`nostrconnect://` QR + "open in signer")
- [ ] nsec import + new account creation (generate key, set name/avatar, back-up-key flow)
- [ ] Signer state UX: pending / rejected / timeout / unreachable
- [ ] Account switcher + logout (wipes secrets and the account's cache)
- [ ] Logged-out browsing mode, with a login prompt on any signed action

## Phase 3 — Viewing

- [ ] **Following feed:** kind 20 from contacts, infinite scroll (`until`), pull-to-refresh, "new posts" pill,
      empty state → Discover
- [ ] **Post card:** header (avatar, name, NIP-05, time, "…"), image carousel with dots, double-tap-to-like heart burst,
      action row (like, comment, repost, zap, share, bookmark), counts, caption, "view all N comments"
- [ ] **Discover:** 3-column grid, hashtag search, user search (NIP-50 where relays support it)
- [ ] **Post detail:** full post + threaded NIP-22 comments + reactions / reposts / zaps lists
- [ ] **Profile:** header, stats, bio, follow/unfollow (fresh kind 3 before mutate), message, zap; tabs
      Posts / Reposts / Tagged; 3-column grid
- [ ] Content warnings (blur + reveal), mute list filtering everywhere
- [ ] Full-screen media viewer with pinch-zoom

## Phase 4 — Interactions & publishing

- [ ] Like (kind 7), unlike via delete (kind 5), optimistic update + rollback
- [ ] Repost (kind 16, `k`=20)
- [ ] Comment / reply (kind 1111) with mentions autocomplete
- [ ] Bookmark (kind 10003), mute (kind 10000), report (kind 1984), delete own post (kind 5)
- [ ] Share: `nevent` link + Android share sheet; deep link handling for `nostr:` / NIP-19
- [ ] Edit own profile (kind 0) incl. avatar/banner upload

## Phase 5 — Create post

- [ ] Photo Picker (multi-select) + CameraX capture + share-sheet intake
- [ ] Reorder, crop/aspect ratio, per-image alt text
- [ ] Caption, hashtags, tag people, location, content warning
- [ ] Blossom upload (BUD-01/02 with kind 24242 auth) to the user's kind 10063 servers with default fallback;
      EXIF/GPS stripping; compute `dim`, `blurhash`, `x`, `m`; per-file progress
- [ ] Publish kind 20 via WorkManager (survives process death, retries), outbox relays

## Phase 6 — Zaps & wallet

- [ ] NWC connect (paste/scan `nostr+walletconnect://`), stored encrypted
- [ ] Zap sheet with preset amounts + comment; LNURL resolution from `lud16`; NIP-57 zap request → NWC `pay_invoice`
- [ ] Zap receipts (kind 9735) display, totals, top zappers
- [ ] Fallback: open external Lightning wallet via `lightning:` intent when no NWC

## Phase 7 — Messages

- [ ] NIP-17 send/receive (gift wrap → seal → kind 14) via Quartz `nip17Dm` / `nip59Giftwrap`
- [ ] Read legacy NIP-04 kind 4
- [ ] Conversation list (last message, unread), chat screen (bubbles, timestamps, sending/failed state)
- [ ] Publish to the recipient's kind 10050 relays; manage own DM relays
- [ ] Decryption happens off the main thread, and decrypted content is never written unencrypted to disk

## Phase 8 — Notifications

- [ ] In-app notifications tab: likes, comments, reposts, zaps, mentions, new followers; Instagram-style grouping with
      thumbnails
- [ ] Unread badge from per-account "last seen"
- [ ] System notifications (channels per type) via WorkManager periodic sync; tapping a notification deep-links to the
      relevant screen

## Phase 9 — Settings

- [ ] Relays (NIP-65 read/write), DM relays (10050)
- [ ] Media servers (Blossom 10063): add/remove/reorder/test
- [ ] Wallet (NWC), default zap amount
- [ ] Appearance: light/dark/system
- [ ] Content: muted users/words, sensitive-content toggle
- [ ] Account: npub copy + QR, signer info, back up key (internal signer only), log out

## Phase 10 — Polish & release

- [ ] Skeleton, empty and error states audited on every screen
- [ ] Accessibility pass (TalkBack, content descriptions, font scaling, touch targets)
- [ ] Adaptive layouts (tablet/foldable) verified
- [ ] Performance: baseline profile, image memory, list stability, no leaked subscriptions
- [ ] Cross-client check: posts, comments, zaps, DMs created on Android render correctly on LUMINA web, and vice versa
- [ ] R8/minify config, signed release build, Play Store listing assets

---

## Open questions / decisions

- Application id `com.rostsoftware.lumina`: confirm before the first release.
- Single-module vs multi-module to start: the plan assumes multi-module. Collapse into `app/` if it slows early progress.
- Quartz NIP-46 API maturity: verify `nip46RemoteSigner` covers bunker + nostrconnect, and extend in `core/signer`
  if not.
- Push notifications: periodic WorkManager polling first. Revisit later (e.g. a notification relay/server).

## Quartz gaps / local extensions

_Track anything we had to add on top of Quartz here (and consider upstreaming)._

- (none yet)
