# Contributing

Thanks for helping improve PunishBridge. This guide covers the local workflow and the conventions
the codebase follows.

## Prerequisites

- **JDK 21** (the Gradle toolchain targets Java 21).
- The Gradle wrapper (`./gradlew`) — no separate Gradle install needed.

## Everyday commands

```bash
./gradlew clean build      # compile, run tests, Detekt, ktlint, Dokka, and ABI validation
./gradlew test             # tests only
./gradlew ktlintFormat     # auto-format all Kotlin (run before committing)
./gradlew ktlintCheck      # verify formatting without changing files
./gradlew apiCheck         # verify the public API has not changed
./gradlew publishToMavenLocal -PreleaseVersion=2.0.0   # install to ~/.m2 for local consumers
```

Always run `./gradlew build apiCheck` before opening a change.

## Public API changes

The `punishbridge-api` module is ABI-validated. If you intentionally change its public surface, run
`./gradlew apiDump` and commit the updated `punishbridge-api/api/punishbridge-api.api` in the same
change, and call out the break in your PR description. Avoid breaking the public API otherwise.

## Project layout

| Module | Purpose |
| --- | --- |
| `punishbridge-api` | Public API: model, capabilities, outcomes, events, validation, Java facade |
| `punishbridge-paper` | Paper runtime, provider selection, vanilla provider, provider SPI |
| `punishbridge-provider-*` | One adapter module per punishment plugin |
| `punishbridge-testkit` | `FakePunishmentBridge` for consumer tests |
| `punishbridge-bom` | Version alignment |
| `samples/embedded-paper` | Shaded sample plugin |
| `build-logic` | Convention plugins shared by every module |

Shared build configuration lives in `build-logic` as precompiled convention plugins
(`punishbridge.kotlin-library`, `punishbridge.published`); module build files stay tiny.

## Writing a provider adapter

- Put it in its own `punishbridge-provider-<name>` module and declare its license in the module
  `build.gradle.kts` POM block.
- Depend on the provider's API as `compileOnly` — never bundle it.
- Extend `AbstractPaperProvider`; use `runProviderOperation`, the `CorrelationTracker`, and
  `emitApplied`/`emitRevoked` rather than re-implementing that plumbing.
- Register the factory under `META-INF/services/io.github.daisycatts.punishbridge.paper.PaperPunishmentProviderFactory`.

## Design invariants

- **Never signal a provider failure as a successful `false`.** Use `BridgeOutcome.Failed` /
  `Unavailable` so callers can tell "not punished" from "couldn't tell".
- Provider APIs are `compileOnly` and must not be shaded; do not use reflection against provider
  internals or raw database writes.
- Dedicated providers outrank EssentialsX; vanilla is the final fallback. Multiple dedicated
  providers require explicit selection.
- All Paper mutations and console-command dispatch run on the server thread; database-backed work
  must not.
- Use `InetAddress.hostAddress`, never `InetAddress.toString()`.
- The bridge owns and closes its executor and provider listeners.

## Style

Code is formatted by ktlint (official style) and checked by Detekt with `allWarningsAsErrors`. Keep
functions small, prefer immutability and null-safety, and avoid `!!`. Run `./gradlew ktlintFormat`
before pushing.
