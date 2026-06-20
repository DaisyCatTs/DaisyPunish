# Migrating from PunishBridge 1

Version 2 intentionally removes the `rs.jamie` API.

| Version 1 | Version 2 |
| --- | --- |
| `new PunishBridge(plugin); start()` | `PaperPunishmentBridge.builder(plugin).build()` |
| `CompletableFuture<Boolean>` | Suspending `BridgeOutcome<Boolean>` or the Java `CompletionStage` facade |
| Nullable UUID/address record fields | Sealed `PunishmentTarget` |
| Fake UUID for console | `PunishmentActor.Console` |
| Epoch `Long` values | `Instant` and `Duration` |
| Arbitrary last provider wins | Dedicated/fallback/system selection with conflict reporting |
| Listener list | `Flow<BridgeEvent>` or Java subscriptions |

There is no compatibility shim. Callers must handle unsupported, unavailable, rejected, and failed outcomes explicitly and must close the bridge during plugin shutdown.
