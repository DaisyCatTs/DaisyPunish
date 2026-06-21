# PunishBridge

**One typed, non-blocking API for every Minecraft punishment plugin.**

Write your moderation logic once. PunishBridge routes it to whatever the server actually runs —
LiteBans, AdvancedBan, LibertyBans, EssentialsX, or Paper's built-in ban lists — and never
pretends a backend failure means "not punished."

[![Build](https://github.com/DaisyCatTs/DaisyPunish/actions/workflows/build.yml/badge.svg)](https://github.com/DaisyCatTs/DaisyPunish/actions/workflows/build.yml)
[![JitPack](https://jitpack.io/v/DaisyCatTs/DaisyPunish.svg)](https://jitpack.io/#DaisyCatTs/DaisyPunish)
[![License](https://img.shields.io/badge/core-Apache--2.0-blue.svg)](LICENSE)
![Java](https://img.shields.io/badge/Java-21-orange.svg)
![Kotlin](https://img.shields.io/badge/Kotlin-2.4-7F52FF.svg)
![Paper](https://img.shields.io/badge/Paper-1.21.11-lightgrey.svg)

---

## Why PunishBridge

- **One API, five backends.** Ban, mute, warn, and kick through a single interface; the right
  provider is detected and selected at runtime.
- **It never lies.** Provider failures surface as explicit `Unavailable` / `Failed` outcomes —
  never a silent `false` that looks like "not punished."
- **Non-blocking by design.** A Kotlin coroutine API plus a Java `CompletionStage` facade.
  Database work runs off-thread; Bukkit calls go back to the main thread automatically.
- **Fully typed.** Sealed targets, actors, durations, and scopes — no nullable-field guesswork,
  no fake console UUIDs.
- **Capability-aware.** Ask `capabilities.supports(...)` before you act, so vanilla never gets a
  mute request it can't honor.
- **Live, not cached.** Storage-free: queries read fresh provider state. No database, no migrations.
- **Event stream.** Observe punishments issued through the bridge *and* externally (e.g. a staff
  `/ban`), where the provider exposes it.
- **Safe by construction.** Per-target serialization, operation timeouts, owned executor and
  listeners that shut down cleanly.
- **Testable.** Ships a `FakePunishmentBridge` so you can unit-test your plugin with no server.

## Requirements

- Java 21 · Paper 1.21.11
- Kotlin 2.4 and kotlinx-coroutines 1.11 (bundle them if your plugin doesn't already)

## Install

### JitPack (easiest — no account or signing needed)

```kotlin
repositories {
    maven("https://jitpack.io")
}

dependencies {
    implementation("com.github.DaisyCatTs.DaisyPunish:punishbridge-paper:v2.0.0")
    implementation("com.github.DaisyCatTs.DaisyPunish:punishbridge-provider-litebans:v2.0.0")
    // add only the adapters you want to support
}
```

Use the same version (a release tag like `v2.0.0`, or a commit hash) on every module so they stay
aligned. Provider APIs are `compileOnly` — the server supplies the real plugin at runtime.

### Maven Central

```kotlin
dependencies {
    implementation(platform("io.github.daisycatts:punishbridge-bom:2.0.0"))
    implementation("io.github.daisycatts:punishbridge-paper")
    implementation("io.github.daisycatts:punishbridge-provider-litebans")
}
```

> Adapter licensing: `punishbridge-paper`/`-api`/`-testkit`/`-provider-litebans` are **Apache-2.0**;
> `-provider-advancedban`/`-provider-essentialsx` are **GPL-3.0**; `-provider-libertybans` is
> **AGPL-3.0**. See [module licensing](LICENSES/README.md) before bundling a GPL/AGPL adapter.

## Quick start (Kotlin)

```kotlin
private lateinit var punishments: PunishmentBridge

override fun onEnable() {
    punishments = when (val result = PaperPunishmentBridge.builder(this).currentServer(server.name).build()) {
        is BridgeStartResult.Ready       -> result.bridge
        is BridgeStartResult.Conflict    -> error("Pick a provider: ${result.providerIds}")
        is BridgeStartResult.Unavailable -> error(result.reason)
        is BridgeStartResult.Failed      -> throw result.cause
    }
}

suspend fun mute(player: Player): BridgeOutcome<OperationReceipt> =
    punishments.issue(
        PunishmentRequest(
            kind = PunishmentKind.MUTE,
            target = PunishmentTarget.Player(player.uniqueId, player.name),
            actor = PunishmentActor.System("chat-filter"),
            reason = "Repeated spam",
            duration = PunishmentDuration.Temporary(Duration.ofMinutes(10)),
        ),
    )

override fun onDisable() = punishments.close()
```

Handle every outcome — a successful query returns `Success(false)` for "not punished," while
`Unsupported` / `Unavailable` / `Failed` mean "couldn't determine it":

```kotlin
when (val outcome = punishments.issue(request)) {
    is BridgeOutcome.Success, is BridgeOutcome.Accepted -> { /* done */ }
    is BridgeOutcome.Unsupported -> log.warn("Active provider can't do that (e.g. vanilla mute)")
    is BridgeOutcome.Rejected    -> log.warn(outcome.reason)
    is BridgeOutcome.Unavailable -> log.warn(outcome.reason)
    is BridgeOutcome.Failed      -> log.error(outcome.message, outcome.cause)
}
```

## Java usage

Wrap a ready bridge in `JavaPunishmentBridge`; operations return `CompletionStage` and `subscribe`
returns an `AutoCloseable`. Callbacks and events run **off** the main thread — hop back with
`Bukkit.getScheduler().runTask(plugin, …)` before touching the Bukkit API.

## Shading

PunishBridge is bundled into your plugin (it isn't a standalone server plugin):

```kotlin
tasks.shadowJar {
    mergeServiceFiles() // required — merges provider auto-discovery descriptors
    relocate("io.github.daisycatts.punishbridge", "$group.libs.punishbridge")
}
```

Do not shade Paper or provider APIs. Do not relocate Kotlin or coroutines. Declare the providers as
soft dependencies so they load first:

```yaml
softdepend: [LiteBans, AdvancedBan, LibertyBans, Essentials]
```

## Provider & capability coverage

| Provider | Ban | Mute | Warning | Kick | External events |
| --- | --- | --- | --- | --- | --- |
| LiteBans | full (writes command-accepted) | full (writes command-accepted) | issue/revoke/query | issue | partial, local |
| AdvancedBan | full | full | full | issue | authoritative, local |
| LibertyBans | full | full | full | issue | authoritative, local |
| EssentialsX | native/Paper | full | unsupported | Paper | mute authoritative; ban/kick bridge-only |
| Vanilla Paper | full | unsupported | unsupported | issue | bridge-only |

Dedicated providers outrank EssentialsX, which outranks vanilla. Two dedicated providers installed at
once require an explicit `.provider("<id>")`. PunishBridge reports these limits through capabilities
and never intercepts commands to manufacture events.

## Documentation

- **[Consuming guide](docs/CONSUMING.md)** — full integration walkthrough + API reference
- [Migration from v1](docs/MIGRATION.md)
- [Compatibility & operational contract](docs/COMPATIBILITY.md)
- [Sample embedded plugin](samples/embedded-paper)

## License

Apache-2.0 core; provider adapters carry their upstream licenses — see [LICENSES](LICENSES/README.md).
