# Architecture

LUMINA Android is a multi-module Kotlin + Jetpack Compose app. Every Nostr operation goes through
[Quartz](https://github.com/vitorpamplona/quartz) `2.15.1` (`com.vitorpamplona.quartz:quartz`), the only Nostr SDK.

## Modules

```
app/                  Application, MainActivity, nav graph (type-safe routes), bottom bar, deep links, manifest
core/designsystem     Tokens (colors, Inter, spacing, shapes, motion), Lucide ImageVectors, base components
core/nostr            Shared NostrClient + NostrNetwork facade, kinds, defaults, event parsers/validators,
                      NIP-19 links, safe content tokenizer
core/signer           AccountManager (multi-account), Keystore SecureStore, signer factory
                      (internal / NIP-55 Amber / NIP-46 bunker + nostrconnect), SignerMonitor + TrackedSigner
core/data             EventCache (Quartz SQLiteEventStore per account), repositories, SessionManager,
                      Publisher (outbox), PublishWorker (WorkManager), SettingsRepository (DataStore)
core/media            ImageProcessor (orientation, crop, resize, re-encode = EXIF/GPS strip, sha256, dim,
                      blurhash), Blurhash codec, BlossomClient (BUD-02 upload, failover, fallback URLs)
core/wallet           LnurlClient (LUD-06/16), NwcRepository (NIP-47 transport), ZapService (NIP-57)
core/ui               Nostr-aware shared UI: PostCard, carousel, image w/ blurhash + Blossom fallback,
                      RichText, avatars/names (NIP-05), sheets (zap/menu/report/raw/engagement), QR,
                      PostActionsViewModel (shared like/repost/zap/bookmark/delete/report/mute)
feature/auth          Login: Amber, bunker, nostrconnect, nsec, new account
feature/feed          Home (following feed)
feature/discover      Explore grid, hashtag + user search
feature/post          Post detail + threaded NIP-22 comments, full-screen media viewer
feature/create        Pick / camera / share intake → edit (reorder, crop, alt) → details → upload → publish
feature/profile       Profile (own + others), edit profile, followers / following lists
feature/messages      NIP-17 conversations + chat
feature/notifications Grouped notifications + background worker with system notifications
feature/settings      Relays, DM relays, media servers, wallet, appearance, content, account
```

Dependency direction: `feature/* → core/ui → core/{designsystem,data,media,wallet} → core/signer → core/nostr`.
Features never depend on each other; the app module wires navigation through lambdas
(`PostNavigation`, `onOpen…` callbacks).

## Data flow

UI → ViewModel (`StateFlow<UiState>`) → repositories → Quartz (`NostrClient` + `SQLiteEventStore`).

- **One `NostrClient`** (Hilt singleton) on an app-scoped coroutine scope. `NostrNetwork` wraps it:
  - `query(relays, filters)` → `fetchAllWithHooks`, dedupes by id, **verifies every signature**
    (`Event.verify()`), bounded by an idle window and a wall clock.
  - `stream(relays, filters)` → `client.subscribe(...)` as a cold `Flow`; the REQ is `unsubscribe`d in
    `awaitClose`, so cancelling the collecting ViewModel scope closes it (no leaked REQs).
  - `publish(event, relays)` → `publishAndConfirmDetailed`, returns relays that answered `OK true`.
- The client disconnects when the process goes to the background and reconnects in the foreground
  (`ProcessLifecycleOwner`).
- **Cache first.** Repositories read the account's `EventCache` immediately, then refresh from relays and
  insert verified events. Gift wraps are cached encrypted; decrypted DMs stay in memory only.
- **Outbox model** (`RelayRepository`):
  - The user's NIP-65 list (kind 10002) is loaded at session start; app defaults are used when none exists.
  - Other authors' 10002/10050 are discovered from indexer relays (`purplepag.es`, `user.kindpag.es`, …)
    and cached in memory + store.
  - Following feed: each author's write relays (≤2) + the user's read relays, capped at 14 relays.
  - Profiles / a user's posts: their write relays + our read relays.
  - Publishing (`Publisher`): the user's write relays + the read relays of every `p`-tagged user.
  - DMs: the recipient's kind 10050 relays (fallback: their read relays, then defaults).
- **Counts**: kinds 7 / 6 / 16 / 1111 / 9735 for a batch of posts in one combined REQ, split client-side
  (`PostRepository.stats`). Comments are counted by root `E` tag.
- **Sessions** (`SessionManager`): on account switch every per-account state is reset, then the user's relay
  lists, follows, mute list, bookmarks and profile are reloaded and the notification/DM watchers restart.
  NIP-42 AUTH is answered with the active signer (`RelayAuthenticator`); remote signers are never prompted for
  silent re-auth.
- **Publishing that must survive process death**: a new post is signed in the foreground (the signer may
  need approval), then `PublishScheduler` hands the signed event to `PublishWorker` (WorkManager, network
  constraint, exponential backoff).

## Signing

All signing goes through Quartz's `NostrSigner`. `AccountManager` builds the right implementation and wraps it in
`TrackedSigner`, which routes every call through `SignerMonitor`:

- `pending` drives the global "Waiting for your signer…" banner (after 700 ms).
- Exceptions are classified into `SignerFailure` (`REJECTED`, `TIMED_OUT`, `UNREACHABLE`, `NOT_FOUND`,
  `NO_SIGNER`) and every publish path maps them to a snackbar via `toUiMessage()`. Remote calls time out after 90 s.
- Logged-out actions call `AccountManager.requestLogin()` / `requireSigner()`, which opens the login flow.

See [signers-and-security.md](signers-and-security.md).

## UI layer

- Each screen: `XxxRoute` (wires `hiltViewModel()`), stateless `XxxScreen(state, …)`, `XxxViewModel`.
  UI states model loading (skeletons matching the final layout), empty, error (retry) and content.
- `PostActionsViewModel` + `PostActionsHost` are shared by every screen that shows posts: stats, optimistic
  like / repost / bookmark (rolled back with a message on failure), zap sheet, menu, report, raw event, delete,
  mute.
- Leaf composables (`ProfileAvatar`, `UserName`, `PostImageView`) reach singletons through a Hilt
  `EntryPoint` and fall back to placeholders in previews.
- Navigation Compose with `@Serializable` routes (`app/navigation/Routes.kt`). The bottom bar is shown on every
  window size except full-screen flows (create, chat, media viewer, login). Content sits in a centered 470 dp
  column (`ContentColumn`).
