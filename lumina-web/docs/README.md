# LUMINA Web — Documentation

LUMINA is an Instagram-style Nostr client built around **kind 20 picture posts (NIP-68)**. This folder documents the
web app in `lumina-web/`. The product spec lives in the repository root `AGENTS.md`; these docs describe how the web
app implements it.

| Document | What's in it |
| --- | --- |
| [architecture.md](architecture.md) | Stack, folder layout, provider tree, routing, data flow and caching |
| [features.md](features.md) | Every screen and feature, and how it behaves (logged in / logged out) |
| [nostr-protocol.md](nostr-protocol.md) | Exact events, kinds and tags LUMINA reads and writes — the interop contract for other platforms |
| [design-system.md](design-system.md) | Tokens, typography, icons, layout rules and shared components |
| [security.md](security.md) | Key handling, signer states, URL sanitising, CSP, DMs, zaps |
| [development.md](development.md) | Setup, scripts, testing, verification, deployment |
| [known-limitations.md](known-limitations.md) | Trade-offs and gaps worth knowing before changing things |

## Quick start

```bash
cd lumina-web
npm install
npm run dev      # http://localhost:8080
npm run test     # tsc + eslint + vitest + production build
```
