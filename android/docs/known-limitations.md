# Known limitations and trade-offs

- **Not yet verified on a device in this change.** The app builds, lint and unit tests pass, but end-to-end
  flows (Amber, bunker, uploads, zaps, DMs) still need a manual pass against real relays per the Definition of Done.
- **Counts are approximate.** The profile post count is the number of posts loaded so far ("40+" while more pages
  exist). Followers come from a `#p` query on discovery relays, capped at 500 ("500+").
- **Outbox routing is heuristic:** at most 2 write relays per author plus the user's read relays, capped at 14
  relays per feed request. Authors without a published NIP-65 list are read from the user's read relays only.
- **Group DMs** (more than two participants) are decrypted but not shown, the same as web. The unread-DM dot compares gift-wrap ids
  (gift wrap timestamps are randomised), so it can't tell your own wraps sent from other devices apart from incoming ones.
- **Remote signers and DMs:** each gift wrap needs two NIP-44 decrypt round trips. Large inboxes load slowly with
  bunkers. Decrypted messages live only in memory for the session.
- **Background notifications** poll every 15 minutes (WorkManager minimum). In a cold process the mute list may not
  be loaded yet, so a muted user could still trigger a system notification. Message notifications never show
  content. On the first run they only record a baseline.
- **Camera capture** hands off to the system camera app (`ActivityResultContracts.TakePicture`) instead of an in-app
  CameraX viewfinder. The QR scanner library declares `CAMERA`, so the app asks for camera permission first.
- **No geohash (`g` tag)** on new posts. Only the location name is written (web adds `g` from browser geolocation).
- **Animated GIF/WebP** are re-encoded to a still WebP (first frame), the same as web.
- **naddr** links are parsed but have no screen (LUMINA has no addressable picture events).
- **App Links** need `assetlinks.json` with the release certificate fingerprint hosted on `lumina.rocks`.
- **Zap receipts** are not checked against the recipient's LNURL `nostrPubkey` when totals are counted, the same as web.
