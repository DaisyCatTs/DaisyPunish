# Using PunishBridge in a Paper plugin

A self-contained integration guide for consuming **PunishBridge 2.0** from another plugin
(e.g. a chat-filter plugin). It is written to be handed directly to a developer — human or
AI coding agent — working in the **consumer** repository. You do not need access to the
PunishBridge source to follow it; the full public API is described below.

PunishBridge is a storage-free, typed, non-blocking bridge between a Paper plugin and the
punishment system installed on the server (LiteBans, AdvancedBan, LibertyBans, EssentialsX,
or Paper's native ban lists). Your plugin talks to **one** API; PunishBridge picks the right
backend at runtime.

---

## Contents
1. [Requirements](#requirements)
2. [Add the dependency](#1-add-the-dependency)
3. [Shade it into your plugin](#2-shade-it-into-your-plugin)
4. [plugin.yml](#3-pluginyml)
5. [Create and close the bridge](#4-create-and-close-the-bridge)
6. [Issue, revoke, and query punishments](#5-issue-revoke-and-query-punishments)
7. [Full chat-filter example (Kotlin)](#6-full-chat-filter-example-kotlin)
8. [Java usage](#7-java-usage)
9. [Listening to events](#8-listening-to-events)
10. [API reference](#9-api-reference)
11. [Provider capability matrix](#10-provider-capability-matrix)
12. [Rules and gotchas](#11-rules-and-gotchas-read-this)

---

## Requirements

- **Java 21**
- **Paper 1.21.11** (`api-version: '1.21'`)
- **Kotlin 2.4.0** and **kotlinx-coroutines 1.11.0** — the library is Kotlin and exposes a
  coroutine API. Match these versions in the consumer to avoid duplicate-runtime issues.
  (A Java-only consumer can use the `CompletionStage` facade — see [Java usage](#7-java-usage).)

---

## 1. Add the dependency

PunishBridge artifacts use group `io.github.daisycatts`. Pick **one** source.

### A. Local Maven (recommended when both repos are on the same machine)

First publish the library locally from the **PunishBridge** repo (re-run after any change):

```powershell
cd C:\Users\Daisy\Desktop\DaisyPunish
.\gradlew publishToMavenLocal -PreleaseVersion=2.0.0
```

Then in the **consumer** `build.gradle.kts`:

```kotlin
repositories {
    mavenLocal()
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")

    implementation("io.github.daisycatts:punishbridge-paper:2.0.0")
    // Add ONLY the provider adapters you want to support (see licensing note):
    implementation("io.github.daisycatts:punishbridge-provider-litebans:2.0.0")
    implementation("io.github.daisycatts:punishbridge-provider-advancedban:2.0.0")
    implementation("io.github.daisycatts:punishbridge-provider-essentialsx:2.0.0")
    // implementation("io.github.daisycatts:punishbridge-provider-libertybans:2.0.0") // AGPL

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
}
```

> If you omit `-PreleaseVersion`, the local version is `2.0.0-SNAPSHOT`; use that coordinate instead.

### B. JitPack (when the library is pushed/tagged on GitHub)

```kotlin
repositories {
    maven("https://jitpack.io")
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    implementation("com.github.DaisyCatTs.DaisyPunish:punishbridge-paper:v2.0.0")
    implementation("com.github.DaisyCatTs.DaisyPunish:punishbridge-provider-litebans:v2.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
}
```

Use the same tag (e.g. `v2.0.0`, or a commit hash) on every module so versions stay aligned.

> **Licensing:** `punishbridge-paper`, `-api`, `-testkit`, `-provider-litebans` are **Apache-2.0**.
> `-provider-advancedban` and `-provider-essentialsx` are **GPL-3.0**; `-provider-libertybans` is
> **AGPL-3.0**. Bundling a GPL/AGPL adapter affects your plugin's license obligations.

---

## 2. Shade it into your plugin

PunishBridge is **not** a standalone server plugin — it must be bundled (shaded) into your jar.

```kotlin
plugins {
    kotlin("jvm") version "2.4.0"
    id("com.gradleup.shadow") version "9.4.2"
}

tasks.shadowJar {
    mergeServiceFiles()  // MANDATORY — merges provider auto-discovery descriptors
    relocate("io.github.daisycatts.punishbridge", "com.example.chatfilter.libs.punishbridge")
}

tasks.build { dependsOn(tasks.shadowJar) }
```

Rules (these are how runtime provider discovery works — don't skip them):

- **`mergeServiceFiles()` is required.** Each adapter ships a `META-INF/services/…ProviderFactory`
  file; without merging, only one provider is discovered.
- **Relocate `io.github.daisycatts.punishbridge`** (recommended) to avoid clashing with another
  plugin that also bundles PunishBridge. Shadow rewrites the service descriptors to match.
- **Do NOT shade** `paper-api` or any provider API (LiteBans/AdvancedBan/LibertyBans/EssentialsX) —
  they are `compileOnly` and supplied by the server at runtime.
- **Do NOT relocate** Kotlin or kotlinx-coroutines.

---

## 3. plugin.yml

```yaml
name: YourChatFilter
version: '1.0.0'
main: com.example.chatfilter.FilterPlugin
api-version: '1.21'
softdepend: [LiteBans, AdvancedBan, LibertyBans, Essentials]
```

`softdepend` makes the punishment plugins load **before** yours, so PunishBridge can detect them
when you build the bridge in `onEnable`.

---

## 4. Create and close the bridge

Build it once in `onEnable`, close it in `onDisable`. `build()` returns a sealed
`BridgeStartResult` you must branch on.

```kotlin
import io.github.daisycatts.punishbridge.PunishmentBridge
import io.github.daisycatts.punishbridge.paper.BridgeStartResult
import io.github.daisycatts.punishbridge.paper.PaperPunishmentBridge

private lateinit var punishments: PunishmentBridge

override fun onEnable() {
    punishments = when (val r = PaperPunishmentBridge.builder(this)
        .currentServer(server.name)         // used for per-server scoping/queries
        // .provider("litebans")            // force a provider (needed if >1 dedicated is installed)
        // .operationTimeout(Duration.ofSeconds(5))  // default 5s
        .build()) {
        is BridgeStartResult.Ready       -> r.bridge
        is BridgeStartResult.Conflict    -> return disable("Multiple providers; choose one: ${r.providerIds}")
        is BridgeStartResult.Unavailable -> return disable(r.reason)
        is BridgeStartResult.Failed      -> return disable("${r.message}: ${r.cause}")
    }
    logger.info("PunishBridge ready: ${punishments.provider.displayName}")
}

override fun onDisable() {
    if (::punishments.isInitialized) punishments.close()
}
```

There is always at least the **vanilla** fallback (bans + kicks), so `Unavailable` only happens
if the plugin is disabled or you forced a provider that isn't installed.

---

## 5. Issue, revoke, and query punishments

All operations are `suspend` and return a `BridgeOutcome<T>`. Call them from a coroutine; the
bridge handles its own threading (database work off-thread, Bukkit work on the main thread).

```kotlin
import io.github.daisycatts.punishbridge.*
import java.time.Duration

suspend fun mute(uuid: java.util.UUID, name: String): BridgeOutcome<OperationReceipt> =
    punishments.issue(
        PunishmentRequest(
            kind = PunishmentKind.MUTE,
            target = PunishmentTarget.Player(uuid, name),
            actor = PunishmentActor.System("chat-filter"),
            reason = "Repeated spam",
            duration = PunishmentDuration.Temporary(Duration.ofMinutes(10)),
        ),
    )

suspend fun isMuted(uuid: java.util.UUID, name: String): Boolean =
    when (val o = punishments.isMuted(PunishmentTarget.Player(uuid, name))) {
        is BridgeOutcome.Success -> o.value
        else -> false  // Unsupported/Unavailable/Failed -> we genuinely don't know; choose a safe default
    }
```

**Always handle all six outcomes** (see the `when` in the full example below). A `Success(false)`
means "confirmed not punished." `Unsupported`/`Unavailable`/`Failed` mean "we couldn't determine
it" — never treat those as "not punished."

---

## 6. Full chat-filter example (Kotlin)

```kotlin
package com.example.chatfilter

import io.github.daisycatts.punishbridge.*
import io.github.daisycatts.punishbridge.paper.BridgeStartResult
import io.github.daisycatts.punishbridge.paper.PaperPunishmentBridge
import io.papermc.paper.event.player.AsyncChatEvent
import kotlinx.coroutines.*
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.plugin.java.JavaPlugin
import java.time.Duration
import java.util.logging.Level

class FilterPlugin : JavaPlugin(), Listener {
    private lateinit var punishments: PunishmentBridge
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onEnable() {
        punishments = when (val r = PaperPunishmentBridge.builder(this).currentServer(server.name).build()) {
            is BridgeStartResult.Ready       -> r.bridge
            is BridgeStartResult.Conflict    -> return disable("Choose a provider: ${r.providerIds}")
            is BridgeStartResult.Unavailable -> return disable(r.reason)
            is BridgeStartResult.Failed      -> return disable("${r.message}: ${r.cause}")
        }
        logger.info("PunishBridge ready: ${punishments.provider.displayName}")
        server.pluginManager.registerEvents(this, this)
    }

    @EventHandler(ignoreCancelled = true)
    fun onChat(event: AsyncChatEvent) {
        val text = PlainTextComponentSerializer.plainText().serialize(event.message())
        if (!isProhibited(text)) return

        event.isCancelled = true            // block the message now (synchronous)
        val player = event.player

        scope.launch {                       // punish asynchronously
            val outcome = punishments.issue(
                PunishmentRequest(
                    kind = PunishmentKind.MUTE,
                    target = PunishmentTarget.Player(player.uniqueId, player.name),
                    actor = PunishmentActor.System("chat-filter"),
                    reason = "Filtered: prohibited content",
                    duration = PunishmentDuration.Temporary(Duration.ofMinutes(10)),
                ),
            )
            when (outcome) {
                is BridgeOutcome.Success     -> {}                          // applied
                is BridgeOutcome.Accepted    -> {}                          // dispatched (e.g. LiteBans); confirmed via event
                is BridgeOutcome.Unsupported -> logger.warning("Active provider can't mute (e.g. vanilla)")
                is BridgeOutcome.Rejected    -> logger.warning("Rejected: ${outcome.reason}")
                is BridgeOutcome.Unavailable -> logger.warning("Unavailable: ${outcome.reason}")
                is BridgeOutcome.Failed      -> logger.log(Level.SEVERE, outcome.message, outcome.cause)
            }
        }
    }

    override fun onDisable() {
        scope.cancel()
        if (::punishments.isInitialized) punishments.close()
    }

    private fun isProhibited(message: String): Boolean = false // <-- your filter logic
    private fun disable(reason: String) { logger.severe(reason); server.pluginManager.disablePlugin(this) }
}
```

---

## 7. Java usage

Wrap a ready bridge in `JavaPunishmentBridge`; operations return `CompletionStage`.

```java
import io.github.daisycatts.punishbridge.*;
import java.time.Duration;

JavaPunishmentBridge pb = new JavaPunishmentBridge(bridge); // 'bridge' from BridgeStartResult.Ready

pb.issue(new PunishmentRequest(
        PunishmentKind.MUTE,
        new PunishmentTarget.Player(uuid, name),
        new PunishmentActor.System("chat-filter"),
        "Filtered: prohibited content",
        new PunishmentDuration.Temporary(Duration.ofMinutes(10)),
        PunishmentScope.CurrentServer.INSTANCE
)).thenAccept(outcome -> {
    if (outcome instanceof BridgeOutcome.Success || outcome instanceof BridgeOutcome.Accepted) {
        // ok
    } // else handle Unsupported / Rejected / Unavailable / Failed
});

// pb.close(); in onDisable
```

`CompletionStage` callbacks run **off** the main thread — re-enter with
`Bukkit.getScheduler().runTask(plugin, …)` before calling any Bukkit API from them.

---

## 8. Listening to events

`bridge.events` is a `Flow<BridgeEvent>`; the Java facade offers `subscribe(Consumer<BridgeEvent>)`
returning an `AutoCloseable`. Events fire for both bridge-issued and externally-issued punishments
(e.g. a staff member running `/ban` directly), where the provider supports it.

```kotlin
scope.launch {
    punishments.events.collect { event ->
        when (event) {
            is BridgeEvent.PunishmentApplied  -> logger.info("Applied ${event.punishment.kind} by ${event.origin}")
            is BridgeEvent.PunishmentRevoked  -> logger.info("Revoked ${event.punishment?.kind}")
            is BridgeEvent.OperationAccepted  -> {}
            is BridgeEvent.ProviderUnavailable-> logger.warning(event.reason)
        }
    }
}
```

Event callbacks run off the main thread; re-enter the scheduler before touching Bukkit.

---

## 9. API reference

All types are in package `io.github.daisycatts.punishbridge` (builder/start-result in
`io.github.daisycatts.punishbridge.paper`).

**`PunishmentBridge`** (`AutoCloseable`)
```kotlin
val provider: ProviderDescriptor
val capabilities: ProviderCapabilities
val events: Flow<BridgeEvent>
suspend fun issue(request: PunishmentRequest): BridgeOutcome<OperationReceipt>
suspend fun revoke(request: RevocationRequest): BridgeOutcome<RevocationReceipt>
suspend fun findActive(query: PunishmentQuery): BridgeOutcome<List<PunishmentRecord>>
suspend fun isMuted(target: PunishmentTarget): BridgeOutcome<Boolean>
suspend fun isBanned(target: PunishmentTarget): BridgeOutcome<Boolean>
fun close()
```

**`PaperPunishmentBridge.builder(plugin)`** → `Builder`:
`provider(id: String?)`, `currentServer(name: String)`, `operationTimeout(Duration)`,
`clock(Clock)`, `build(): BridgeStartResult`.

**`BridgeStartResult`** = `Ready(bridge)` | `Conflict(providerIds: List<String>)` |
`Unavailable(reason: String)` | `Failed(providerId: String?, message: String, cause: Throwable)`.

**`BridgeOutcome<T>`** =
`Success(value: T)` | `Accepted(receipt: OperationReceipt)` | `Unsupported(capability: CapabilityRequest)` |
`Rejected(reason: String)` | `Unavailable(reason: String)` | `Failed(providerId, message, cause: Throwable?)`.
(`Accepted` = command dispatched but not yet confirmed; currently only LiteBans writes.)

**Model**
```kotlin
enum PunishmentKind { BAN, MUTE, WARNING, KICK }

sealed PunishmentTarget:
    Player(uuid: UUID, username: String)
    Address(address: InetAddress)
    PlayerAndAddress(uuid: UUID, username: String, address: InetAddress)

sealed PunishmentActor:
    Console                      // object
    Player(uuid: UUID, username: String)
    System(componentName: String)

sealed PunishmentDuration:
    Permanent                    // object
    Temporary(duration: Duration)

sealed PunishmentScope:
    CurrentServer                // object
    Global                       // object
    NamedServer(name: String)

PunishmentRequest(
    kind: PunishmentKind,
    target: PunishmentTarget,
    actor: PunishmentActor = PunishmentActor.Console,
    reason: String,
    duration: PunishmentDuration = PunishmentDuration.Permanent,
    scope: PunishmentScope = PunishmentScope.CurrentServer,
)

sealed RevocationSelector:
    ByReference(reference: PunishmentReference)
    ByTarget(target: PunishmentTarget, kind: PunishmentKind, scope: PunishmentScope = CurrentServer)
RevocationRequest(selector: RevocationSelector, actor: PunishmentActor = Console, reason: String? = null)

PunishmentQuery(target: PunishmentTarget, kinds: Set<PunishmentKind>, scope: PunishmentScope = CurrentServer)

PunishmentReference(providerId: String, externalId: String)
OperationReceipt(providerId, correlationId: UUID, status: ReceiptStatus, reference: PunishmentReference?)
RevocationReceipt(providerId, correlationId: UUID, revokedCount: Int?)
PunishmentRecord(providerId, reference: PunishmentReference?, kind, target, actor: PunishmentActor?,
                 reason: String?, startsAt: Instant?, expiresAt: Instant?, scope: PunishmentScope?, fidelity: DataFidelity)
enum ReceiptStatus { APPLIED, ACCEPTED }
enum DataFidelity { COMPLETE, PARTIAL }
```

**Capability introspection** (check before acting, to avoid `Unsupported`):
```kotlin
val canMute = punishments.capabilities.supports(
    CapabilityRequest(PunishmentOperation.ISSUE, PunishmentKind.MUTE, TargetKind.PLAYER),
)
// PunishmentOperation { ISSUE, REVOKE, QUERY, OBSERVE_EXTERNAL }
// TargetKind { PLAYER, ADDRESS, PLAYER_AND_ADDRESS }
```

**Validation** (an invalid request returns `BridgeOutcome.Rejected`):
usernames must match `^[A-Za-z0-9_]{1,16}$`; `reason` is required, non-blank, ≤255 chars, no
control characters; kicks cannot have a duration; a temporary duration must be positive.

---

## 10. Provider capability matrix

| Provider | Ban | Mute | Warning | Kick | External events |
| --- | --- | --- | --- | --- | --- |
| LiteBans | full (writes `Accepted`) | full (writes `Accepted`) | issue/revoke/query | issue | partial, local |
| AdvancedBan | full | full | full | issue | authoritative, local |
| LibertyBans | full | full | full | issue | authoritative, local |
| EssentialsX | native/Paper | full | unsupported | Paper | mute authoritative; ban/kick bridge-only |
| Vanilla Paper | full | unsupported | unsupported | issue | bridge-only |

Selection: a dedicated provider (LiteBans/AdvancedBan/LibertyBans) outranks EssentialsX, which
outranks vanilla. If two dedicated providers are installed you get `BridgeStartResult.Conflict`
unless you call `.provider("<id>")` (ids: `litebans`, `advancedban`, `libertybans`, `essentialsx`, `vanilla`).

---

## 11. Rules and gotchas (read this)

- **PunishBridge issues punishments; it does not enforce mutes.** The installed provider enforces
  the mute (blocks chat). With no provider, **vanilla has no mute**, so mute returns `Unsupported`.
  Your filter should still cancel the message itself; the mute is the follow-up sanction.
- **`Accepted` ≠ `Success`.** LiteBans writes go through console commands, so a successful write is
  `BridgeOutcome.Accepted` (dispatched); the matching `PunishmentApplied` event arrives when LiteBans
  confirms it. Treat `Accepted` as "issued."
- **Never interpret `Unsupported`/`Unavailable`/`Failed` as "not punished."** Only `Success`/`Accepted`
  (writes) and `Success` with data (queries) are authoritative.
- **Threading:** calling `issue/revoke/findActive/isMuted/isBanned` from the main thread or any
  coroutine is safe — the bridge does database work off-thread and Bukkit work on the main thread.
  But **`events` collection and Java `subscribe`/`CompletionStage` callbacks run off the main thread** —
  re-enter via `server.scheduler.runTask(plugin) { … }` before touching the Bukkit API.
- **Operations on the same target are serialized** and have a 5-second default timeout
  (configurable via `operationTimeout`).
- **Records are best-effort** (`fidelity = PARTIAL` and sometimes `username = "unknown"`) when a
  provider doesn't expose full data. Don't rely on `record.target.username` for display.
- **Keep Kotlin/coroutines versions aligned** with the library (Kotlin 2.4.0, coroutines 1.11.0).
- **Testing:** `io.github.daisycatts:punishbridge-testkit` provides `FakePunishmentBridge` with
  settable `issueHandler`/`revokeHandler`/`queryHandler` and an `emit(event)` method, so you can
  unit-test your plugin without a server.
