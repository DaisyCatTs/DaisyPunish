# Compatibility and operational contract

PunishBridge 2.0 compiles against Paper 1.21.11 on Java 21 and these provider APIs: LiteBans API 0.6.1, AdvancedBan 2.3.0, LibertyBans 1.1.3, and EssentialsX 2.22.0.

Provider APIs are `compileOnly`. Server owners must install the actual provider plugin. PunishBridge performs no database migration and stores no moderation data.

All database-backed work runs outside the Paper main thread. Paper mutations and console-command dispatch return to the main thread. Operations for the same target are serialized and have a five-second default timeout.

The bridge queries fresh provider state rather than maintaining a cache. Address logs are avoided by default. LiteBans commands are constructed only after request validation and do not accept control characters.

Folia, Velocity, BungeeCord, runtime provider switching, and bridge-owned mute enforcement are not supported in 2.0.
