# Development

## Requirements

- JDK 17+ (CI uses 21). On macOS with Homebrew: `export JAVA_HOME=/opt/homebrew/opt/openjdk@21`.
- Android SDK with platform 37 (`local.properties` → `sdk.dir=…`; Android Studio writes it).
- Gradle is provided by the wrapper (9.8.1). AGP 9.4.1 with built-in Kotlin 2.4.21, KSP 2.3.13, Hilt 2.60.1.

All versions live in `gradle/libs.versions.toml`.

## Commands (from `android/`)

```bash
./gradlew assembleDebug                         # build
./gradlew installDebug                          # install on a device/emulator
./gradlew testDebugUnitTest                     # unit tests (all modules)
./gradlew lint                                  # Android lint
./gradlew lint testDebugUnitTest assembleDebug  # must pass before every commit (also run by CI)
```

CI: `.github/workflows/android.yml` runs the same command on changes under `android/**` and uploads the debug APK.

## Conventions

- Module and screen structure: see [architecture.md](architecture.md).
- `core/*` uses Kotlin explicit API mode. Features don't need to.
- No `!!`. Strings go in each module's `strings.xml`, prefixed with the module name in features (`feed_`, `post_`, …).
- Every screen has light and dark `@Preview`s with fake state.
- Commit messages are prefixed with `android:`.

## Testing against real relays

1. Install the debug build and log in with Amber, a bunker (e.g. nsec.app), or an nsec.
2. Create a post. It's uploaded to your Blossom servers (or the app defaults) and published as kind 20. It
   should appear on LUMINA web (`lumina-web`) for the same account, and vice versa.
3. Deep links: `adb shell am start -a android.intent.action.VIEW -d "nostr:npub1…"`.
4. App Links: host `/.well-known/assetlinks.json` on `lumina.rocks` with the release signing certificate
   fingerprint and the package `rocks.lumina.app` so `https://lumina.rocks/<nip19>` opens the app directly.

## Release

`./gradlew assembleRelease` builds a minified (R8) APK/AAB. Configure your own signing config (keystores are
git-ignored) before publishing.
