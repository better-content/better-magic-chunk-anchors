# Better Magic Chunk Anchors

Pack-owned Forge 1.20.1 mod providing seven magic-gated, power-buffered chunk anchors. Each active anchor keeps its centered 3x3 chunk area fully ticking and exposes an operator registry/teleport command.

Build the runtime artifact with `gradle assemble`; the staged `build/libs/better-magic-chunk-anchors-0.1.0.jar` is the reobfuscated jar.

## Canonical identity

- Repository and Gradle project: `better-magic-chunk-anchors`
- Mod ID and resource namespace: `better_magic_chunk_anchors`
- Maven group: `com.bettercontent`
- Runtime artifact: `build/libs/better-magic-chunk-anchors-<version>.jar`

The canonical identity is a clean break. Legacy mod IDs, resource namespaces, configuration paths, commands, network channels, and saved-data keys are not migrated or aliased.

## Verification

Run `./gradlew verifyFast` for deterministic checks and the staged runtime artifact. Run `./gradlew verifyFull` for runtime changes; it additionally executes all ten production GameTests.

Seven generated cases exercise each variant's real block entity and public power boundary: Forge energy/fluid/item capabilities, Ars Source, PneumaticCraft air, Goety player interaction, and Create kinetic speed input. They verify acceptance, resource rejection or simulation where applicable, configured payment, and depletion. Kinetic coverage checks the anchor's speed threshold and buffer; it does not claim a complete external Create network fixture.

The remaining tests cover the actual Ars SourceManager payment, nine fully ticking Forge tickets across acquisition, starvation, redstone disable/re-enable and removal, and saved ownership/block identity round-trips. Ticket assertions inspect Forge's owned records by mod ID and block position, independently of chunks held open by the GameTest framework. Lifecycle coverage uses the shared ticket controller through a Flux anchor; ownership serialization is a save/load round-trip within one server process.

Each server run uses a fresh `build/gametest/<run-token>/` directory and retains its world, logs, and `execution.json`. The reviewed `gametest/profiles/full.txt` must exactly match runtime discovery and successful completion. Missing, stale, empty, partial, duplicate, or failed evidence fails the task. Preserve failed run directories for diagnosis; `clean` removes build evidence.
