# PunishBridge (repo: DaisyPunish)

**One typed, non-blocking API for every Minecraft punishment plugin.** Write moderation logic
once; PunishBridge routes it to whatever the server runs (LiteBans, AdvancedBan, LibertyBans,
EssentialsX, or Paper's built-in ban lists) and never pretends a backend failure means
"not punished." Kotlin / Java 21, group `io.github.daisycatts`, current line **v2.0.0**.
Apache-2.0 (kept permissive so it can ship inside proprietary products).

## Build & test
- `./gradlew build` (multi-module; `build-logic/` holds convention plugins incl. the shared
  `punishbridge.publishing` convention).
- Publish for local consumers (e.g. [[DaisyFilter]]): `./gradlew publishToMavenLocal -PreleaseVersion=2.0.0`.
- `./gradlew test` / `check` for the unit + facade + validator suites.

## Layout
`core` (the typed bridge API + backend adapters + correlation tracking) · `build-logic`
(convention plugins) · `config` · `docs` (integration guide — see `docs/CONSUMING.md`) ·
`LICENSES/` (per-dependency license texts).

## Conventions / gotchas
- Public API: it's a **library other plugins depend on** — treat breaking changes as semver-major
  and keep the Java facade usable from Java callers, not just Kotlin.
- Adapters must report honest outcomes (Success/Accepted/Unsupported/Rejected/Unavailable/Failed);
  never silently swallow a backend failure.
- Downstream consumers shade + relocate it; don't add heavy transitive deps.
