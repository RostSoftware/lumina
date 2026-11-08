# Known limitations and trade-offs

- **Counts are approximate.** Followers / posts use NIP-45 COUNT where the first read relays support it; otherwise
  they are counted from a capped fetch (shown with "+" when capped). Follower counts only see relays in the user's
  read list.
- **Outbox model not used for feeds.** Feeds read from the user's read relays (plus `nevent` relay hints for single
  posts). Authors who only publish to other relays may be missing.
- **New-follower notifications** are derived from kind 3 lists that tag the user; since kind 3 is replaceable, an
  existing follower who edits their list shows up again (deduped per person).
- **Zap receipts are not verified** against the recipient's LNURL `nostrPubkey` when counting totals.
- **DM decryption with remote signers** needs two NIP-44 decrypt round trips per message; the first load of a large
  inbox can be slow and may prompt in some signers. Results are cached in memory for the session only; messages
  that fail to decrypt are skipped for the session. Group DMs (more than two participants) are not supported in
  the UI; only 1:1 conversations are shown.
- **Unread-DM dot** compares gift-wrap ids seen on the Messages screen, so it does not decrypt anything (no signer
  prompts on Home), but it can't tell your own sent wraps apart from incoming ones arriving on other devices.
- **Muting** writes public items only. Private (encrypted) items created by other clients are read and preserved.
- **Image formats**: HEIC only works in browsers that can decode it (Safari). PNG/GIF/WebP sources are re-encoded to
  WebP (first frame only for animated GIFs), everything else to JPEG.
- **Pull-to-refresh** is touch-only; on desktop use the "new posts" pill or reload.
- Some image hosts send `Cross-Origin-Resource-Policy: same-origin`; those images can't be shown by any web client
  and render as "Image unavailable" (after trying Blossom fallbacks by hash).
