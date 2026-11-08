# LUMINA Android — documentation

Native Android client for LUMINA (Kotlin, Jetpack Compose, Hilt, [Quartz](https://github.com/vitorpamplona/quartz)).
The product spec is the root [`AGENTS.md`](../../AGENTS.md); Android rules are in [`../AGENTS.md`](../AGENTS.md).

| Document | Contents |
| --- | --- |
| [architecture.md](architecture.md) | Modules, data flow, relay/outbox strategy, caching, signing pipeline, UI pattern |
| [features.md](features.md) | Every screen and what it does, mapped to the spec |
| [nostr-protocol.md](nostr-protocol.md) | Events LUMINA Android reads and writes (identical to web) and where the code lives |
| [signers-and-security.md](signers-and-security.md) | Login methods, remote-signing states, multi-account, secret storage, untrusted input |
| [design-system.md](design-system.md) | Tokens, Inter, Lucide icon generation, layout rules, light/dark theme |
| [development.md](development.md) | Requirements, commands, CI, testing against real relays, App Links, release |
| [known-limitations.md](known-limitations.md) | Trade-offs and gaps |

Progress and the Quartz gap list live in [`../PLAN.md`](../PLAN.md).
