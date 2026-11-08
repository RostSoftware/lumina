# Security

## Keys and signers

| Login | Where the key lives |
| --- | --- |
| Signer app / bunker (NIP-46) | In the remote signer. LUMINA stores only the ephemeral client key + bunker pubkey + relays (in `localStorage` key `nostr:login`) |
| Extension (NIP-07) | In the extension |
| nsec | **Plaintext in `localStorage`** (`nostr:login`). The UI warns about this on login, account creation and in Settings |

Login order in the UI prefers external signers. LUMINA never asks for a private key except in the explicit nsec
option.

### Remote-signer states

`signWithFeedback()` (`hooks/useNostrPublish.ts`) wraps every signature:

- After 0.8 s without a result for a non-nsec signer, a toast says "Waiting for your signer… approve the request".
- Timeout: 10 s (nsec) / 120 s (remote). Blossom auth and DM sealing use the same pattern (`lib/signer.ts`).
- Errors are classified into **rejected**, **unreachable**, **unsupported** or unknown, and shown with a specific
  title. Buttons show pending text and re-enable on failure; nothing fails silently.

## Logout

`useLogout()` removes the login (keys / bunker secret), deletes every `localStorage` key under
`lumina:<pubkey>:` (NWC wallet URI, default zap, notification "last seen", DM read markers), clears the in-memory
decrypted-DM cache for that account and clears the whole TanStack Query cache.

## Secrets that must never be logged or persisted elsewhere

nsec, bunker secrets, NWC URIs and decrypted DMs. The code contains no `console.log` of these. Decrypted DMs exist
only in memory. The NWC URI input is a password field.

## Untrusted event data

- **No HTML from events.** No `dangerouslySetInnerHTML` anywhere. `RichText` tokenises text into React nodes; the
  raw-event view renders JSON as text.
- **URLs**: `sanitizeUrl()` (https only) is applied at the parse layer — `parsePicturePost` (image + fallback URLs),
  `parseMetadata` (picture, banner, website). Links in text are matched by an `https://` regex. Custom emoji images
  must be `https://`. `<img>` tags use `referrerPolicy="no-referrer"`.
- **Validation**: kind 20 is validated before rendering; DM seals are signature-checked and must match the rumor
  author; repost-embedded events are signature-verified; zap receipts are parsed defensively.
- **Trust filtering**: replaceable / user-owned lists (kinds 0, 3, 10000, 10002, 10003, 10050, 10063) are always
  queried with `authors`.

## CSP (`index.html`)

`default-src 'none'; script-src 'self'; style-src 'self' 'unsafe-inline'; font-src 'self'; connect-src 'self' blob:
https: wss:; img-src 'self' data: blob: https:; media-src 'self' https:` …  Do not relax `script-src`. Fonts are
self-hosted and Vite is configured never to inline them as `data:` URIs (`vite.config.ts`).

## Uploads

Images are always re-encoded through a canvas before upload, which **drops EXIF/GPS** and all other metadata. The
optional location area is a 5-character geohash (~±2.4 km) and requires explicit user action and browser
permission.

## Zaps

- LNURL endpoints and callbacks must be HTTPS (`lib/lnurlPay.ts`).
- The amount must be within the endpoint's `minSendable`/`maxSendable`.
- The returned invoice is **decoded and must charge exactly the approved amount** (`assertInvoiceAmount`) and must
  commit to our zap request or the LNURL metadata (`invoiceCommitsTo`) before any wallet sees it.
- `payWithNwc` re-checks the amount right before paying.

## Relay AUTH

`NostrProvider` answers NIP-42 challenges with the active signer only.
