# PunishBridge 2 implementation status

Updated: 2026-06-20

## Current state

The Java 16 PunishBridge 1 implementation has been removed and replaced by a Java 21/Kotlin 2.4 multi-module rewrite.

Implemented modules:

- `punishbridge-api`: typed domain model, capabilities, outcomes, events, validation, coroutine API, and Java `CompletionStage` facade.
- `punishbridge-paper`: lifecycle, owned executor, timeout handling, target serialization, deterministic provider selection, service discovery, and Paper/vanilla bans and kicks.
- `punishbridge-provider-litebans`: API queries/events and validated console-command writes.
- `punishbridge-provider-advancedban`: native create/delete/query/event integration.
- `punishbridge-provider-essentialsx`: native mute integration and Paper ban/kick delegation.
- `punishbridge-provider-libertybans`: native drafter/revoker/selector/Omnibus integration.
- `punishbridge-testkit`: reusable fake bridge.
- `punishbridge-bom`: aligned dependency versions.
- `samples/embedded-paper`: shaded and relocated sample with merged service descriptors.

Documentation, module-license metadata, Maven Central/JitPack publication setup, GitHub build/release workflows, Detekt, ktlint, Dokka, reproducible archives, and binary API validation are present.

## Verified commands

Run from the repository root with Java 21:

```shell
./gradlew clean build apiCheck --no-daemon
./gradlew publishToMavenLocal -PreleaseVersion=2.0.0-rc.1 --no-daemon
```

The final clean verification passed 113 tasks, including all compilations, tests, Detekt, ktlint, Dokka generation, ABI validation, and the shaded sample.

The standard configuration-cache-enabled build also passed and a second run reused the cache successfully.

Dependency locks are committed per module and `gradle/verification-metadata.xml` contains SHA-256 checksums. Paper's changing snapshot required an explicit verified checksum entry for timestamped build `20260511.115010-91`; a deliberate Paper snapshot upgrade must update that checksum and the lock state together.

Local publication was also verified at version `2.0.0-rc.1`. The BOM and all seven publishable library artifacts produced POM, Gradle metadata, binary, sources, and Dokka documentation artifacts under `~/.m2/repository/io/github/daisycatts`.

The shaded sample was manually inspected. Its relocated service descriptor points to the relocated LiteBans factory:

```text
META-INF/services/io.github.daisycatts.sample.libs.punishbridge.paper.PaperPunishmentProviderFactory
io.github.daisycatts.sample.libs.punishbridge.provider.litebans.LiteBansProviderFactory
```

## Important design invariants

- Never return `false` for a provider failure. Use `BridgeOutcome.Failed` or `Unavailable`.
- Do not use provider private internals, reflection, or raw provider database writes.
- Provider APIs remain `compileOnly` and must not be shaded.
- Dedicated providers outrank EssentialsX; vanilla is the final fallback. Multiple dedicated providers require explicit selection.
- Keep AdvancedBan/EssentialsX adapters GPL-3.0 and LibertyBans adapter AGPL-3.0 as separate artifacts.
- Use `InetAddress.hostAddress`, not `InetAddress.toString()`.
- Preserve explicit console/system actor types; never restore the old fake console UUID.
- All Paper mutations and command dispatch must run on the server thread. Database-backed work must not.
- The bridge owns and closes its executor and provider listeners.

## Remaining release work

These items require external state or real server binaries rather than more library implementation:

1. Boot the sample on a real Paper 1.21.11 server and test vanilla ban, unban, query, and kick behavior.
2. Smoke-test one provider at a time using LiteBans, AdvancedBan 2.3.0, EssentialsX 2.22.0, and LibertyBans 1.1.3. LiteBans requires a legally obtained commercial plugin JAR.
3. Test EssentialsX coexisting with each dedicated provider.
4. Configure Maven Central namespace/credentials and in-memory signing secrets in GitHub Actions.
5. Publish `v2.0.0-rc.1`, integrate it into the chat-filter plugin, and canary it before promoting the same commit to `v2.0.0`.

The sample provides a `runServer` task preconfigured for Paper 1.21.11. It prepares the test server and sample plugin, but interactive moderation checks still require a test player/provider setup.

## Known provider limitations

- LiteBans has no supported public write API. Writes return `Accepted` after documented commands are dispatched; application is confirmed only when its event arrives. Cross-instance and wildcard-removal events are incomplete by LiteBans design.
- Vanilla cannot mute or warn and exposes no reliable external ban-command event.
- EssentialsX external event fidelity is authoritative for mutes only; delegated Paper bans/kicks are bridge-only.
- Provider event guarantees are local to the running server unless the provider itself synchronizes them.
- Folia, Velocity, BungeeCord, runtime provider switching, and bridge-owned punishment storage are intentionally out of scope for 2.0.

## Useful entry points

- Public API: `punishbridge-api/src/main/kotlin/io/github/daisycatts/punishbridge/PunishmentBridge.kt`
- Domain model: `punishbridge-api/src/main/kotlin/io/github/daisycatts/punishbridge/Model.kt`
- Paper bootstrap: `punishbridge-paper/src/main/kotlin/io/github/daisycatts/punishbridge/paper/PaperPunishmentBridge.kt`
- Provider SPI: `punishbridge-paper/src/main/kotlin/io/github/daisycatts/punishbridge/paper/ProviderSpi.kt`
- Consumer sample: `samples/embedded-paper/src/main/kotlin/io/github/daisycatts/sample/EmbeddedSamplePlugin.kt`
- Migration guide: `docs/MIGRATION.md`
- Compatibility details: `docs/COMPATIBILITY.md`
- Real-server checklist: `docs/SMOKE_TESTS.md`
