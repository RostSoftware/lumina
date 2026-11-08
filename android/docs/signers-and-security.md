# Login, signers & security

## Login methods

| Method | How | Code |
| --- | --- | --- |
| **Amber / NIP-55** (shown first when installed, otherwise a "Get Amber" link) | `ExternalSignerLogin.createIntent(permissions)` → result parsed with `ExternalSignerLogin.parseResult` → `NostrSignerExternal(pubkey, packageName, contentResolver)`. Background requests use Amber's content provider. Requests that need approval launch Amber through `ExternalSignerBridge` (registered by `MainActivity`). | `feature/auth`, `core/signer/AccountManager`, `ExternalSignerBridge` |
| **NIP-46 bunker** | Paste or scan `bunker://…`. LUMINA creates a fresh client keypair, then runs `NostrSignerRemote.connect()` + `getPublicKey()`. | `AccountManager.loginWithBunker` |
| **NIP-46 nostrconnect** | LUMINA generates a client keypair and a secret and shows `nostrconnect://…` as a QR code with an "Open in signer" button. It subscribes to kind 24133 on the URI relays (the app write relays, same as web) and accepts the `connect` response that echoes the secret (or `ack`). The response's author is the remote signer. | `NostrConnectSession` (Quartz gap #5), `AccountManager.completeNostrConnect` |
| **nsec / new account** | `NostrSignerInternal(KeyPair)`. The private key is encrypted with an Android Keystore AES-256-GCM key. New accounts set a name and then see a key backup step. | `AccountManager.loginWithNsec` / `createAccount` |

Requested permissions (NIP-46 `perms` and the NIP-55 permission list): `sign_event` for kinds
0, 3, 5, 7, 13, 14, 16, 20, 1111, 1984, 9734, 10000, 10002, 10003, 10050, 10063, 22242, 24242 plus NIP-04/NIP-44 encrypt/decrypt
(`Nip46Permissions`).

## Remote signing states

Every signer call goes through `TrackedSigner` → `SignerMonitor`:

- **Pending**: a global pill ("Waiting for your signer…") appears after 700 ms. Buttons show progress and
  are disabled.
- **Rejected / timed out (90 s) / unreachable / signer app missing**: classified into `SignerFailure` and shown
  as a snackbar on the screen that asked. Optimistic changes (like, repost, follow, bookmark, mute) roll back.
- Decryption failures (e.g. DMs not addressed to us) are silent; they don't spam errors.

## Multiple accounts

`AccountManager` keeps a list of accounts (DataStore, non-secret metadata) and the active one. Switching swaps the
signer. `EventCache` opens that account's SQLite database, and `SessionManager` resets and reloads all
per-account state. Settings are keyed per account (theme is device-wide).

**Logout** removes the account and:
- wipes its secrets from `SecureStore` (nsec, NIP-46 client key, NWC URI),
- deletes its event database (`events_<pubkey>.db` + WAL/SHM),
- removes its per-account preferences,
- clears in-memory decrypted DMs (`DmRepository.reset`).

## Secret storage

`SecureStore` keeps an AES-256-GCM key in the Android Keystore and stores only ciphertexts, in a private
SharedPreferences file. The app sets `allowBackup="false"`, and `data_extraction_rules.xml` excludes everything from cloud
backup and device transfer. Secrets (nsec, bunker keys, NWC URIs, decrypted DMs) are never logged or sent anywhere.
The nsec can only be exported through Settings → Account → Back up key, behind a confirmation.

## Untrusted input

- Every event from relays is signature-verified before use or caching (`NostrNetwork`), and parsed by a validator
  (`PictureParser`, `ProfileParser`, `CommentParser`, `ZapParser`, `RelayListParser`). Invalid kind 20 events
  are dropped silently.
- Only `https` URLs are loaded or linked (`UrlSafety`). Text is rendered by `RichText` from `ContentTokenizer`
  tokens and is never interpreted as HTML. There is no WebView.
- The share-sheet intake accepts only `content://` URIs. Deep links never route `nsec` strings.
- Lists the user owns (kind 0/3/10000/10002/10003/10050/10063) are queried with `authors = [me]`. Mutations
  always fetch the newest version from relays first and preserve unknown tags and content.
- Uploaded images are re-encoded, which strips EXIF/GPS/XMP metadata.
