# Nostr protocol (Android)

LUMINA Android reads and writes **exactly** the events documented in
[`lumina-web/docs/nostr-protocol.md`](../../lumina-web/docs/nostr-protocol.md). That file is the cross-platform
contract; this page maps it to the Android code. No app-private kinds or tags exist.

| Event | Written by | Read by |
| --- | --- | --- |
| Kind 20 picture (NIP-68) | `feature/create/PictureTags.build` (title, imeta…, `m`, `x`, `t`, `p`, `location`, `content-warning`, `client`) | `core/nostr/model/PictureParser` (validation identical to web `parsePicturePost`) |
| Like kind 7 `+` (`e`,`p`,`k=20`) / unlike kind 5 (`k=7`) | `InteractionRepository.like/unlike` | `ReactionParser`, `PostRepository.stats` |
| Repost kind 16 (`k=20`, JSON content) / undo kind 5 (`k=16`) | `InteractionRepository.repost/unrepost` | `PostRepository`, `FeedRepository.reposts` (embedded JSON verified, else fetched by id) |
| Comment kind 1111 (root `E/K/P`, parent `e/k/p`, `p` for mentions, `t` for hashtags) | `InteractionRepository.comment` | `CommentParser`, `PostRepository.comments` |
| Zap request kind 9734 (signed, sent to LNURL, not published) | `ZapService` via Quartz `ZapRequestEvent.build/sign` | — |
| Zap receipt kind 9735 | — | `ZapParser` (bolt11 amount via Quartz `LnInvoiceUtil`, fallback `amount`; sender from `P` or embedded request) |
| Delete kind 5 (`e`, `k`) | `InteractionRepository.deletePost/deleteComment` | stats/comments ignore deleted ids |
| Report kind 1984 (`e`+type, `p`+type) | `InteractionRepository.report` | — |
| Bookmarks kind 10003 (`e`) | `ListsRepository.toggleBookmark` (newest first, other tags preserved) | `ListsRepository` |
| Mute list kind 10000 (public `p`/`word`/`t`, private NIP-44/04 items read + preserved) | `ListsRepository.mute*/unmute*` | `ListsRepository` → `MuteList.hides` everywhere |
| Profile kind 0 | `ProfileRepository.updateProfile` (newest from relays, unknown fields kept) | `ProfileParser` (https-only picture/banner, website upgraded to https) |
| Follows kind 3 | `ContactRepository.follow/unfollow` (newest from relays first, `content` and tags preserved) | `ContactRepository` |
| Relays kind 10002 | Settings → Relays | `RelayRepository`, `RelayListParser` |
| DM relays kind 10050 | Settings → Relays | `RelayRepository` |
| Blossom servers kind 10063 | Settings → Media servers | `RelayRepository` → `BlossomClient.servers()` |
| DMs: kind 14 → seal 13 → gift wrap 1059 (one per recipient + self) | `DmRepository.send` via Quartz `NIP17Factory` | `DmRepository` (unwrap, **verify `seal.pubkey == rumor.pubkey`**, 1:1 only) |
| Legacy DM kind 4 | never written | `DmRepository` (both directions, labelled "legacy") |
| Blossom auth kind 24242 (`t=upload`, `x`, expiration +10 min) | `BlossomClient` via Quartz `BlossomAuthorizationEvent` | — |
| NWC 23194/23195 | `NwcRepository` via Quartz `Nip47Client` | `NwcRepository` |
| NIP-46 24133 | `AccountManager` / `NostrConnectSession` via Quartz `NostrSignerRemote` | same |
| NIP-42 auth 22242 | `SessionManager` via Quartz `RelayAuthenticator` | — |

## Queries

- **Following feed:** `{kinds:[20], authors:[…], until, limit:20}` per outbox relay group.
- **Discover:** `{kinds:[20], "#t"?:[tag], until, limit:40}` on read relays (+ relay.nostr.band for hashtags).
- **Stats (one combined query per ≤100 posts):** `{kinds:[7,6,16,1111,9735], "#e":[ids]}` + `{kinds:[1111], "#E":[ids]}`.
- **Notifications:** `{kinds:[7,6,16,1111,9735,3,20], "#p":[me], limit:100, until}`. Own events are ignored, and so are
  reactions/reposts/comments whose `k`/`K` is not `20`. Targets that are not the user's kind 20 posts are hidden.
- **Search:** NIP-50 `search` on `relay.ditto.pub`, `relay.nostr.band`, `search.nos.today`.

## Defaults (same as web)

App relays: `relay.ditto.pub` (rw), `relay.dreamith.to` (rw), `relay.primal.net` (w), `nos.lol` (w). Indexers for
discovering others' metadata/relay lists: `purplepag.es`, `user.kindpag.es`, `relay.damus.io`, `relay.primal.net`.
Blossom: `blossom.ditto.pub`, `blossom.dreamith.to`, `blossom.primal.net`. Zap presets 21 / 100 / 500 / 1k / 5k / 10k.
