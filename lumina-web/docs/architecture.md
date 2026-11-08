# Architecture

## Stack

| Concern | Choice |
| --- | --- |
| UI | React 19, TypeScript (no `any`), Tailwind CSS 4, shadcn/ui primitives (Radix), Lucide icons |
| Nostr | [Nostrify](https://nostrify.dev) (`NPool`, `NRelay1`, NIP-46 / NIP-07 / nsec signers) + `nostr-tools` helpers |
| Data | TanStack Query (all relay reads are queries / infinite queries; writes are mutations) |
| Routing | React Router 7 (`BrowserRouter`), lazy-loaded secondary screens |
| Media | Blossom (BUD-01/02/03) uploads via `XMLHttpRequest` (for progress), `blurhash`, `@noble/hashes` |
| Payments | NIP-47 Nostr Wallet Connect via `@getalby/sdk` (`NWCClient`), WebLN fallback, LNURL-pay |
| Font | Inter Variable, self-hosted via `@fontsource-variable/inter` |

## Folder layout

```
src/
├── App.tsx                 providers (Unhead, AppProvider, QueryClient, NostrLogin, NostrProvider, NostrSync, Tooltip, Toaster)
├── AppRouter.tsx           routes; wraps everything in LoginDialogProvider and the AppShell layout
├── index.css               design tokens (CSS variables → Tailwind theme)
├── components/
│   ├── layout/             AppShell (bottom bar visibility), BottomNav, TopBar + IconButton
│   ├── auth/               LoginDialog (all login methods), LoginDialogProvider (requireLogin())
│   ├── common/             BottomSheet, UserAvatar, UserName/Nip05Badge, RichText, Icons, Wordmark,
│   │                       States (Empty/Error/Spinner), InfiniteTrigger, PullToRefresh
│   ├── post/               PostCard, PostHeader, PostActions, MediaCarousel, ProgressiveImage, MediaViewer,
│   │                       SensitiveOverlay, PostGrid, Comments, NewPostsPill, sheets (menu, share, zap, report, raw)
│   ├── profile/            FollowButton, UserListSheet
│   ├── create/             CropEditor
│   ├── settings/           SettingsRow/Section, UrlListEditor
│   └── ui/                 shadcn/ui primitives (restyled to LUMINA tokens)
├── contexts/               AppContext (config type), LoginDialogContext
├── hooks/                  all data access (see below)
├── lib/                    pure logic: parsing, crypto, formatting, uploads (no React)
├── pages/                  one file per screen; pages/settings/* for settings sub-screens
└── test/                   TestApp wrapper + vitest setup
```

## Provider tree

```
UnheadProvider → AppProvider (theme, relays, blossom servers, showSensitive; persisted in localStorage)
  → QueryClientProvider → NostrLoginProvider (logins in localStorage "nostr:login")
    → NostrProvider (NPool; routes REQs to read relays, EVENTs to write relays, answers NIP-42 AUTH)
      → NostrSync (pulls the user's kind 10002 + kind 10063 into AppContext after login)
      → TooltipProvider → Toaster + AppRouter
          → BrowserRouter → LoginDialogProvider → AppShell → page
```

`LoginDialogProvider` exposes `useLoginDialog()` with `openLogin()` and `requireLogin()`; every action that needs a
signer calls `requireLogin()` first, which opens the login dialog for logged-out users.

## Routing

| Path | Screen | Bottom bar |
| --- | --- | --- |
| `/` | Home (Following feed) | ✔ |
| `/discover`, `/discover?t=<tag>` | Discover grid / hashtag | ✔ |
| `/create` | Create post | hidden (full screen) |
| `/notifications` | Notifications | ✔ |
| `/messages` | Conversation list | ✔ |
| `/messages/:npub` | Chat thread | hidden (full screen) |
| `/profile` | Redirects to own `/npub…` (or opens login) | ✔ |
| `/settings`, `/settings/{profile,relays,media,wallet,appearance,content,account}` | Settings | ✔ |
| `/open?uri=nostr:…` | Target of the registered `nostr:` protocol handler | — |
| `/remoteloginsuccess` | Callback page for mobile signer apps (nostrconnect) | ✔ |
| `/:nip19` | `npub`/`nprofile` → profile, `note`/`nevent` → post, `naddr` → resolved; `nostr:` prefix is stripped | ✔ |

The media viewer is a portal overlay that covers the bottom bar.

## Data layer (hooks)

All relay access goes through hooks in `src/hooks/`. Query keys start with `'nostr'` so a relay-list change can
invalidate everything at once (see `NostrProvider`).

| Hook | Purpose |
| --- | --- |
| `useFeed.ts` → `usePictureFeed(spec)` | Infinite feed (`until` cursor) for `following` / `discover` (+hashtag) / `author` / `tagged`. Validates every event with `parsePicturePost`, dedupes, filters mutes and locally deleted posts |
| `useFeed.ts` → `useNewPostsCount`, `useRefreshFeed` | 30 s poll with `since` for the "new posts" pill; resets a feed to page 1 |
| `useFeed.ts` → `useRepostFeed`, `useEventById`, `useAddressableEvent` | Profile reposts tab (kind 16 with embedded / fetched originals), single event loads with relay hints |
| `usePostStats.ts` | **Batched** interaction counts — see below |
| `usePostActions.ts` | Like / unlike, repost / undo, comment, delete (NIP-09), report (NIP-56) — optimistic with rollback |
| `useContacts.ts` | Kind 3: `useContacts`, `useFollowing`, `useIsFollowing`, `useFollow` (fetches latest kind 3 first) |
| `useMutes.ts` | Kind 10000 (public + decrypted private items), `useMuteMutation` |
| `useBookmarks.ts` | Kind 10003 bookmarks |
| `useNotifications.ts` | Notification feed, grouping, unread dot, "last seen" per account |
| `useMessages.ts` | NIP-17 + NIP-04 inbox, send, DM relays (kind 10050), cheap unread indicator |
| `useWallet.ts`, `useZap.ts` | NWC wallet per account, NIP-57 zap flow |
| `useSettingsLists.ts` | Save + publish kind 10002 / 10063 / 10050 |
| `useCount.ts` | NIP-45 COUNT with fallback to capped fetch (post / follower counts) |
| `useAuthor.ts`, `useNip05.ts` | Kind 0 (URLs sanitised at parse time), NIP-05 verification / resolution |
| `useNostrPublish.ts` | `signWithFeedback()` (remote signer UX) and a generic publish mutation |
| `useAccountValue.ts` | `useSyncExternalStore`-backed per-account localStorage values |
| `useLogout.ts` | Logout that wipes the account's keys, storage and caches |

### One combined query for interactions

`usePostStats` never queries per post directly. Every `usePostInteractions(id)` call registers the id with a
`StatsBatcher` (one per `NPool`), which waits 60 ms and sends **one** REQ for all collected ids:

```
[{ kinds: [7, 6, 16, 9735, 5], "#e": ids }, { kinds: [1111], "#E": ids }]
```

Results are split client-side per post (reactions/reposts/zaps by their last `e` tag, comments by root `E`). Kind 5
deletions by the interaction's own author remove it (un-like / un-repost). A whole feed page therefore costs one
round trip for all of its like/comment/repost/zap counts.

### Caching

- Default `staleTime` 60 s, `gcTime` ∞, `retry` 1; feeds 2 min; profiles 5 min; events by id ∞.
- Decrypted DMs live in an **in-memory** `Map` (`useMessages.ts`), keyed by account; never persisted.
- Optimistic updates write directly into the `['nostr','post-stats',id]`, `['nostr','contacts',pubkey]` and
  `['nostr','bookmarks',pubkey]` caches.
