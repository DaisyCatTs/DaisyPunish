# Provider smoke-test checklist

Run each provider on a clean Paper 1.21.11 test server. Never reuse a production punishment database.

Start the embedded test server with:

```shell
./gradlew :samples:embedded-paper:runServer
```

For each provider:

1. Install only that provider and EssentialsX where the coexistence row requires it.
2. Confirm startup logs show the expected provider ID/version and no linkage errors.
3. Issue permanent and temporary player bans, then query and revoke them.
4. Issue permanent and temporary mutes, then query and revoke them where supported.
5. Issue a warning and kick where supported.
6. Exercise a player+address punishment and confirm the normalized address has no slash prefix.
7. Repeat issue/revoke through the provider's own admin command and verify declared external events.
8. Stop the server and confirm no PunishBridge executor or listener reports work after shutdown.

## Selection matrix

| Installed plugins | Expected selection |
| --- | --- |
| None | `vanilla` |
| EssentialsX | `essentialsx` |
| LiteBans + EssentialsX | `litebans` |
| AdvancedBan + EssentialsX | `advancedban` |
| LibertyBans + EssentialsX | `libertybans` |
| Two dedicated providers | Startup conflict unless explicitly selected |

## LiteBans-specific checks

Use a legally obtained LiteBans server JAR.

- A write returns `BridgeOutcome.Accepted`, not `Success`.
- `OperationAccepted` is emitted immediately after command dispatch.
- A matching `PunishmentApplied` event follows when LiteBans confirms the entry.
- Permanent and second-based temporary duration command formats are accepted.
- Player, concrete IP, and composite targets use the correct command.
- CR/LF and other control characters are rejected before dispatch.
- Global, current, and named query scopes match the LiteBans server-scope configuration supplied to the bridge builder.
- Document that wildcard removals and events originating on another LiteBans instance may not be observed.

## Vanilla-specific checks

- Profile bans appear in Paper's profile ban list.
- Address bans use the typed IP ban list.
- Composite bans create and revoke both entries.
- Offline kicks return a typed failed outcome.
- Mute and warning requests return `Unsupported`.

Record provider version, Paper build, database backend, operation outcome, and event sequence for every failed case before changing adapter code.
