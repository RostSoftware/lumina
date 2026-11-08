# Nostr protocol contract

Everything LUMINA web **writes** must render in the Android app and vice versa. No app-private kinds or tags are used,
so there is no `NIP.md`. Parsers live in `src/lib/`.

## Kind 20 picture post (NIP-68) — written by `CreatePage.tsx`

```jsonc
{
  "kind": 20,
  "content": "Sunset over the bay #sunset",           // caption (may be empty)
  "tags": [
    ["title", "Sunset over the bay"],                 // first caption line without hashtags, ≤ 80 chars, or "Photo"
    ["imeta",                                         // one per image, in display order
      "url https://blossom.example/<sha256>.jpg",
      "m image/jpeg",                                 // image/jpeg, or image/webp for PNG/GIF/WebP sources
      "dim 2048x1536",
      "x <sha256 hex of the uploaded file>",
      "size 482113",
      "blurhash LKO2?U%2Tw=w]~RBVZRi};RPxuwH",
      "alt A red sky over water"],                    // only when the user wrote alt text
    ["m", "image/jpeg"],                              // NIP-68 filterable media types (deduped)
    ["x", "<sha256>"],                                // NIP-68 filterable hashes (one per image)
    ["t", "sunset"],                                  // lowercased hashtags (caption + manual)
    ["p", "<pubkey>"],                                // tagged people
    ["location", "San Francisco"],                    // optional
    ["g", "9q8yy"],                                   // optional geohash, precision 5
    ["content-warning", "reason"],                    // optional; reason may be omitted
    ["client", "LUMINA"]                              // added on https deployments
  ]
}
```

**Reading/validation** (`lib/picture.ts` → `parsePicturePost`): kind must be 20; at least one `imeta` whose `url`
is `https:` and whose `m` is an image mime type (a missing `m` is tolerated only if the URL has an image extension).
Invalid events are dropped silently. `dim` is only used when it matches `^\d+x\d+$`; `x` only when 64-hex; blurhash
is charset-checked. `fallback` entries are sanitised (https only).

## Interactions

| Action | Event |
| --- | --- |
| Like | kind 7, `content "+"`, tags `["e", post.id]`, `["p", post.pubkey]`, `["k", "20"]` |
| Unlike | kind 5, `["e", reaction.id]`, `["k", "7"]` |
| Repost | kind 16, `content` = JSON of the post, `["e", id, ""]`, `["p", author]`, `["k", "20"]` |
| Undo repost | kind 5, `["e", repost.id]`, `["k", "16"]` |
| Comment on post | kind 1111, `["E", root.id, "", root.pubkey]`, `["K","20"]`, `["P", root.pubkey]`, `["e", root.id, "", root.pubkey]`, `["k","20"]`, `["p", root.pubkey]` (+ `p` for `nostr:npub` mentions) |
| Reply to comment | kind 1111, same `E/K/P`, parent `["e", comment.id, "", comment.pubkey]`, `["k","1111"]`, `["p", comment.pubkey]` |
| Zap request | kind 9734 via `nip57.makeZapRequest` (`e`, `p`, `k`, `amount`, `relays`), signed, **not published**, sent to the LNURL callback |
| Delete post | kind 5, `["e", id]`, `["k", "20"]` |
| Report | kind 1984, `["e", id, type]` (posts), `["p", pubkey, type]`, content = optional details |
| Bookmark | kind 10003, `["e", id]` added/removed; other tags and `content` preserved |
| Mute | kind 10000 public tags `p` / `word` / `t`; private (encrypted) items are read and preserved, removing a private item re-encrypts the private part with NIP-44 |
| Follow | kind 3 `p` tags; **the latest kind 3 is fetched from relays first**, `content` and other tags preserved |

Counts are read with one combined query per batch (see [architecture.md](architecture.md)). Likes = distinct authors
of non-`-` reactions; reposts = distinct reposters (kinds 6 and 16); comments = kind 1111 with root `E`; zaps =
kind 9735 receipts, amount from the bolt11 (fallback: zap request `amount`), sender from the embedded zap request.

## Profiles & lists

| Kind | Use |
| --- | --- |
| 0 | Profile. Edit always starts from the newest kind 0 on relays and keeps unknown fields. `picture`, `banner`, `website` are sanitised when read |
| 3 | Following list / followers (`#p` query, distinct authors) / new-follower notifications |
| 10002 | NIP-65 relays (`r` tags, `read`/`write` markers). Loaded at login by `NostrSync`, published from Settings |
| 10050 | NIP-17 DM inbox relays (`relay` tags) |
| 10063 | BUD-03 Blossom servers (`server` tags) |

## Direct messages (NIP-17 / NIP-59) — `lib/dm.ts`

- Rumor: unsigned kind 14, `["p", recipient]`, id computed.
- Seal: kind 13 signed by the sender, content = `signer.nip44.encrypt(recipient, rumor)`, created_at randomised up to 2 days back, no tags.
- Gift wrap: kind 1059 signed by a fresh random key, content NIP-44 encrypted to the recipient, `["p", recipient]`, randomised created_at.
- One wrap per recipient **and** one to self.
- Unwrapping verifies the seal signature and requires `seal.pubkey === rumor.pubkey`.
- Legacy NIP-04 kind 4 is read (both directions) and labelled; LUMINA never sends NIP-04.
- Interop with `nostr-tools` `nip17`/`nip59` was verified in both directions.

## Notifications query

```
{ kinds: [7, 6, 16, 1111, 9735, 3, 20], "#p": [me], limit: 100, until }
```

Own events are ignored; reactions/reposts/comments whose `k`/`K` is not `20` are ignored; targets that turn out
not to be kind 20 are hidden.

## Blossom uploads — `lib/blossom.ts`

`PUT <server>/upload` with headers `Authorization: Nostr <base64 kind 24242>` (`t=upload`, `x=<sha256>`,
`expiration` +10 min), `Content-Type`, `X-SHA-256`. Servers are tried in order: user's kind 10063 list, merged
with the app defaults (`blossom.ditto.pub`, `blossom.dreamith.to`, `blossom.primal.net`) when that toggle is on.

## Relays

Default relays (`lib/appRelays.ts`): `relay.ditto.pub` (rw), `relay.dreamith.to` (rw), `relay.primal.net` (w),
`nos.lol` (w). Reads go to all read relays, writes to all write relays. NIP-42 AUTH is answered with the active
signer. Event-id hints from `nevent` are queried in addition to the pool.
