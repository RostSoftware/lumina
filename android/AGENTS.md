# LUMINA Android — Agent Guide

This folder contains the native Android app for LUMINA. **Read the root `/AGENTS.md` first.** It defines the product,
the feature set, login methods, design principles and Definition of Done that this app must meet. This file only covers
how to build it on Android. Implementation order and progress are tracked in [`PLAN.md`](./PLAN.md). Keep it updated
as you work.

## Tech Stack

- **Kotlin** (latest stable) with coroutines + `Flow`. Java 17 toolchain.
- **Jetpack Compose** + **Material 3** for all UI. No XML layouts and no Fragments.
- **[Quartz](https://github.com/vitorpamplona/quartz)** (`com.vitorpamplona.quartz:quartz`, Maven Central) is the
  **only** Nostr SDK. It handles events, filters, the relay client (`NostrClient`), signers, NIP-17, NIP-57, NIP-47,
  Blossom and more. Don't add a second Nostr library, and don't hand-roll crypto or event serialization that Quartz
  already provides. If Quartz lacks something, add a small, well-named extension in `core/nostr` and note it in `PLAN.md`.
- **OkHttp** for WebSockets (`BasicOkHttpWebSocket.Builder`) and HTTP (Blossom uploads, LNURL, NIP-05).
- **Hilt** for dependency injection.
- **Navigation Compose** with type-safe (`@Serializable`) routes.
- **Coil 3** for image loading, with a blurhash placeholder and Blossom fallback.
- **Room** for the local event cache. **DataStore** for preferences.
- **Android Keystore** encrypts secrets: nsec, bunker client keys and NWC URIs.
- **WorkManager** for uploads/publishing that must survive process death, and for notification polling.
- **CameraX** / Photo Picker for media capture and selection.
- `minSdk 26` (Quartz requires it), `targetSdk`/`compileSdk` at the latest stable API.
- Dependency versions live in `gradle/libs.versions.toml` (version catalog). Don't put inline version strings in build
  files.

## Project Structure

```
android/
├── AGENTS.md / PLAN.md
├── settings.gradle.kts, build.gradle.kts, gradle/libs.versions.toml
├── app/                    ← Application, MainActivity, nav graph, DI wiring
├── core/
│   ├── nostr/              ← Quartz wrapper: NostrClient singleton, relay mgmt, outbox routing, subscriptions
│   ├── signer/             ← account + signer abstraction (internal, NIP-55 Amber, NIP-46 bunker/nostrconnect)
│   ├── data/               ← repositories, Room cache, DataStore
│   ├── media/              ← Blossom upload, EXIF stripping, blurhash, sha256
│   ├── wallet/             ← NWC (NIP-47) + zaps (NIP-57)
│   └── designsystem/       ← theme, color/typography tokens, shared components (Avatar, PostImage, Skeleton…)
└── feature/
    ├── auth/ feed/ discover/ post/ create/ profile/ messages/ notifications/ settings/
```

- Package root: `rocks.lumina.app` (application id `rocks.lumina.app`, from the domain `lumina.rocks`).
- Each `feature/*` module has `XxxScreen` (stateless composable), `XxxViewModel` (exposes a `StateFlow<XxxUiState>`),
  and `XxxRoute` (wires the ViewModel to the screen). Features depend on `core/*` only, never on each other.

## Architecture Rules

- **Unidirectional data flow:** UI → ViewModel events → repositories → Quartz/Room. UI state is an immutable data
  class or sealed interface with `Loading / Content / Empty / Error`.
- **One shared `NostrClient`** (Hilt `@Singleton`) on an app-scoped `CoroutineScope`. Subscriptions are tied to
  ViewModel/lifecycle scope and **must be closed** when the screen leaves. Leaked REQs are bugs.
- **Outbox model:** read an author's posts from their NIP-65 write relays, and publish to the user's write relays plus
  tagged users' read relays. DMs go to kind 10050 relays.
- **Cache first:** render from Room immediately and update from relays. Verify signatures (Quartz does this) before
  inserting into the cache.
- Use `fetchAll` / `subscribeAsFlow` for paging with `until` cursors. Combine kinds into one filter where possible
  (e.g. counts for kinds 7/16/1111/9735).
- No work on the main thread. Use `Dispatchers.IO` for disk/network and `Dispatchers.Default` for crypto/blurhash.

## Login & Signers

All signing goes through Quartz's `NostrSigner` interface, so features never know which signer is active.

| Method | Implementation |
| --- | --- |
| **Amber / NIP-55** | `NostrSignerExternal(pubKey, packageName, contentResolver)`. Detect installed signers via the `nostrsigner:` intent. Request `get_public_key` with the permissions LUMINA needs, then use the content provider for background signing and fall back to activity intents when the user must approve. |
| **NIP-46 bunker** | Paste/scan a `bunker://` URI. Use Quartz's `nip46RemoteSigner`. Persist the client keypair + secret in Keystore-backed storage. |
| **NIP-46 nostrconnect** | Generate a `nostrconnect://` URI, show it as a QR code + "Open in signer" button, and wait for the connect response. |
| **nsec / new account** | `NostrSignerInternal(KeyPair)`. Key encrypted with the Android Keystore and never exported except through an explicit "Back up key" flow behind a confirmation. |

- Amber is the first and most prominent option on the login screen when it is installed. If it isn't, offer a
  "Get Amber" link.
- Signing can be pending, rejected or timed out. Surface each state and never block the UI thread waiting on it.
- Multi-account: every account has its own signer, cache partition and settings. Switching accounts swaps the
  `NostrSigner` and restarts subscriptions.

## Android-Specific Product Requirements

- Edge-to-edge with correct insets. Predictive back. Dynamic color is **off** (LUMINA uses its own palette, which must
  match the web tokens).
- Deep links: `nostr:` scheme and NIP-19 strings route to the profile or post screens. Register an intent filter for
  `nostr:` and verified App Links for `https://lumina.rocks` (host `assetlinks.json` on the web app).
- Share sheet target: receiving images from other apps opens the Create flow.
- System notifications for DMs, mentions, comments and zaps (POST_NOTIFICATIONS permission, per-type channels).
- Adaptive layout: bottom bar on compact width, navigation rail on medium/expanded, feed column capped at ~470dp.
- Strip EXIF/GPS on device before upload. Use the system Photo Picker and avoid broad storage permissions.

## Security

- Never log keys, bunker secrets, NWC URIs or decrypted DM content. Release builds strip debug logs.
- Treat event content as untrusted. Only load `https` media URLs, and render text with a safe parser (links, mentions,
  hashtags, custom emoji). No `WebView` for event content.
- `android:allowBackup="false"` for secret storage (or exclude it via backup rules).

## Commands

Run from `android/`:

```bash
./gradlew assembleDebug           # build
./gradlew installDebug            # install on a connected device/emulator
./gradlew testDebugUnitTest       # unit tests
./gradlew lint                    # Android lint
./gradlew lint testDebugUnitTest assembleDebug   # must pass before every commit
```

## Testing

- Unit test ViewModels, repositories, event parsing/validation (kind 20 `imeta`, NIP-22 tags, zap receipts) and signer
  state machines with JUnit + Turbine + fakes. Don't hit real relays in unit tests.
- Compose UI tests for critical flows (login, like, post) where practical.

## Conventions

- Kotlin official code style. Explicit API visibility in `core/*`.
- No `!!`. Model absence with nullable types / sealed results.
- Strings in `strings.xml` (ready for localization), not hardcoded in composables.
- Every composable screen has `@Preview`s for light and dark mode using fake UI state.
- Commit messages prefixed with `android:`.
