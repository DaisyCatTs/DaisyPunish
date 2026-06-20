# PunishBridge 2

PunishBridge is a storage-free Kotlin bridge between Paper plugins and common punishment systems. It exposes typed, non-blocking issue, revoke, query, and event APIs without treating provider failures as “not punished”.

## Requirements

- Java 21
- Paper 1.21.11
- Kotlin 2.4 and kotlinx-coroutines 1.11 when not already supplied by the consuming plugin

Supported providers are LiteBans, AdvancedBan, LibertyBans, EssentialsX, and Paper's native ban lists. Dedicated punishment providers take precedence over EssentialsX; vanilla is the final fallback. Multiple dedicated providers require an explicit provider ID.

## Dependency

### JitPack

```kotlin
repositories {
    maven("https://jitpack.io")
}

dependencies {
    implementation("com.github.DaisyCatTs.DaisyPunish:punishbridge-paper:v2.0.0")
    implementation("com.github.DaisyCatTs.DaisyPunish:punishbridge-provider-litebans:v2.0.0")
}
```

JitPack builds the tagged commit on first request, so no Maven Central account or signing is required. Use the same coordinate version on every module (a release tag such as `v2.0.0`, or a commit hash) so their versions stay aligned. Provider APIs stay `compileOnly`, so the server still supplies LiteBans, AdvancedBan, LibertyBans, or EssentialsX at runtime.

### Maven Central

```kotlin
dependencies {
    implementation(platform("io.github.daisycatts:punishbridge-bom:2.0.0"))
    implementation("io.github.daisycatts:punishbridge-paper")
    implementation("io.github.daisycatts:punishbridge-provider-litebans")
}
```

Choose only the provider adapter modules you need. See [module licensing](LICENSES/README.md) before embedding GPL or AGPL adapters.

## Kotlin usage

```kotlin
private lateinit var punishments: PunishmentBridge

override fun onEnable() {
    punishments = when (val result = PaperPunishmentBridge.builder(this)
        .currentServer(server.name)
        .build()) {
        is BridgeStartResult.Ready -> result.bridge
        is BridgeStartResult.Conflict -> error("Select one of ${result.providerIds}")
        is BridgeStartResult.Unavailable -> error(result.reason)
        is BridgeStartResult.Failed -> throw result.cause
    }
}

suspend fun mute(player: Player): BridgeOutcome<OperationReceipt> = punishments.issue(
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

Always handle `Unavailable` and `Failed` separately from a successful `false` query.

## Java usage

Wrap a ready bridge in `JavaPunishmentBridge`. Operations return `CompletionStage`; `subscribe` returns an `AutoCloseable` event subscription.

`CompletionStage` callbacks and `subscribe` listeners run on a background thread, not the Paper main thread. Re-enter the main thread with `Bukkit.getScheduler().runTask(plugin, …)` before touching any Bukkit API from them.

## Shading

```kotlin
tasks.shadowJar {
    mergeServiceFiles()
    relocate("io.github.daisycatts.punishbridge", "$group.libs.punishbridge")
}
```

Do not shade Paper or provider APIs. Do not relocate Kotlin or coroutines. Add these entries to the consuming plugin's `plugin.yml` so provider classes are visible at enable time:

```yaml
softdepend: [LiteBans, AdvancedBan, LibertyBans, Essentials]
```

The bridge must be created in `onEnable` and closed in `onDisable`.

## Capability and event coverage

| Provider | Ban | Mute | Warning | Kick | External events |
| --- | --- | --- | --- | --- | --- |
| LiteBans | Full; writes command-accepted | Full; writes command-accepted | Issue/revoke/query | Issue | Partial local |
| AdvancedBan | Full | Full | Full | Issue | Authoritative local |
| LibertyBans | Full | Full | Full | Issue | Authoritative local |
| EssentialsX | Native/Paper | Full | Unsupported | Paper | Mute authoritative; ban/kick bridge-only |
| Vanilla Paper | Full | Unsupported | Unsupported | Issue | Bridge-only |

LiteBans does not emit every cross-instance or wildcard-removal event. PunishBridge reports these limitations through provider capabilities and never intercepts commands to manufacture events.

See the [consuming guide](docs/CONSUMING.md) for full integration steps, [migration guidance](docs/MIGRATION.md), [compatibility details](docs/COMPATIBILITY.md), and the [sample embedded plugin](samples/embedded-paper).
