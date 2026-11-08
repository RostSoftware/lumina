# Development

## Requirements

Node 20+ (tested with Node 26) and npm.

## Scripts

| Command | What it does |
| --- | --- |
| `npm run dev` | Vite dev server on port 8080 |
| `npm run build` | Production build into `dist/` (+ `404.html` copy for static hosts) |
| `npm run test` | `tsc --noEmit` → `eslint` → `vitest run` → production build. **Must pass before committing** |

## Tests

Vitest + Testing Library in jsdom (`src/test/setup.ts`, `src/test/TestApp.tsx`). Node ≥ 25 exposes an
experimental global `localStorage` that is `undefined` without `--localstorage-file` and shadows jsdom's; the setup
file installs an in-memory `Storage` in that case.

Per the web `AGENTS.md`, new test files are only added when requested. Logic that is worth unit testing is kept
pure in `src/lib/` (picture parsing, notifications grouping, DM wrap/unwrap, mute filtering, bolt11, geohash).

## Manual verification checklist

1. Logged out: Home welcome state, Discover grid loads from relays, open a post, open a profile, any action opens login.
2. Log in with a **remote signer** (nsec.app or Amber via "Signer app" QR/link, or a `bunker://`) and with an **nsec**.
3. Like / double-tap like / unlike, comment + reply, repost, bookmark, follow / unfollow (check the follow count does not drop).
4. Create a post with 2+ images, crop, alt text, hashtags, content warning; confirm it renders on another NIP-68 client.
5. Zap with NWC connected, and without (invoice QR).
6. Notifications red dot appears after activity and clears after opening the screen.
7. Send a DM to another NIP-17 client and receive a reply.
8. Settings: relays, DM relays, media servers (test), wallet, theme, mutes, account switching, logout.
9. Check a ~360 px phone and a wide desktop window, in light and dark mode.

## Deployment

Static SPA. Serve `dist/` with a fallback to `index.html` (a `404.html` copy and `public/_redirects` are included
for Netlify/GitHub-Pages-style hosts). HTTPS is required for `crypto.subtle`, `navigator.share`, clipboard and the
`nostr:` protocol handler.

## Adding things

- New screen: page in `src/pages/`, route in `AppRouter.tsx` above `/:nip19`, decide whether it is full screen
  (`AppShell.isFullScreen`).
- New relay read: a hook in `src/hooks/` with a query key starting with `'nostr'`, `AbortSignal.any([signal, timeout])`,
  validation of events, and skeleton / empty / error states in the UI.
- New write: use `signWithFeedback` + `toastError`; make it optimistic if it's a toggle.
- Product behaviour changes must also go into the root `AGENTS.md` (cross-platform spec).
