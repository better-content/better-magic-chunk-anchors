# Better Magic Chunk Anchors

Forge 1.20.1 / Java 17; mod ID `better_magic_chunk_anchors`.

## Local verification

- Deterministic: `./gradlew verifyFast`.
- Runtime/GameTest changes: `./gradlew verifyFull` with canonical Root-qualified/stamped
  `BC_CUSTOM_MOD_JAR_DIR` containing Compat0.1.9 and Gameplay Notices1.0.0. Native execution
  fails closed without this binding; pure `verifyFast` remains unbound. No sibling/history fallback.
  The native-only provider receipt and read-only Mixin post-apply witness bind loaded source metadata
  and the actual Registrate production transaction merge. This does not authorize execution or
  replace the providers' owning gates, the original twelve GameTests, or historical-cause review.
- Stage: `./gradlew stageRuntimeJar`, `build/libs/better-magic-chunk-anchors-<version>.jar`.

## Shared authority

Read [workspace policy](../../better-content-modpack/docs/policies/workspace.md),
[testing](../../better-content-modpack/docs/testing.md) and
[disposal](../../better-content-modpack/docs/policies/generated-data.md).
Docs-only changes use the shared documentation check and `git diff --check`.
