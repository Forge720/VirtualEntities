# VirtualEntities.jar

VirtualEntities is a Paper 1.21.11 / Java 21 infrastructure plugin for logical, packet-only visual entities. It exposes a small Bukkit service API and keeps NMS, MythicMobs, and ModelEngine types behind internal adapters.

## Current v0.1 scope

- Native NMS visuals for `armor_stand`, `zombie`, `skeleton`, `creeper`, `spider`, `villager`, `item_display`, `block_display`, `text_display`, and `marker`.
- Optional native player-model and text/display support, including profile textures, equipment, pose, display scale, and interaction-bound metadata where the selected source supports it.
- ModelEngine 4 Dummy-based rendering with forced per-player viewers, transforms, named animations, and idempotent cleanup.
- MythicMobs definition lookup without spawning an ActiveMob, executing skills, or creating a world entity. A supported `model{mid=...}` association is delegated to ModelEngine; otherwise the configured base entity type is rendered through the native adapter.
- `PLAYER`, `PLAYERS`, and `WORLD` audience policies with distance/chunk eligibility checks.
- Optional interaction routing through Paper's unknown-entity event, scoped to rendered viewers and owner-registered listeners.
- Centralized lifecycle ticking, expiration, viewer reconciliation, owner cleanup, and provider-shutdown cleanup.

The native renderer constructs transient NMS objects only to serialize valid packets. It never adds those objects to a world or Bukkit entity registry. ModelEngine owns its own Dummy/model packet implementation; this plugin does not duplicate bone rendering.

## Build

```text
./gradlew clean build
```

The wrapper uses Gradle 9.9.0 and targets Paper's 1.21.11 development bundle and Java 21. ModelEngine and Mythic-Dist are `compileOnly`; they must be installed separately on a production server when their adapters are used. `reobfJar` produces the deployable runtime artifact at `build/libs/VirtualEntities-0.1.0-SNAPSHOT-reobf.jar`. See `docs/USAGE.md` for the concise administrator/AI guide.

## Installation

1. Run the build with JDK 21.
2. Copy the generated JAR to a Paper 1.21.11 server's `plugins` directory.
3. Install ModelEngine 4.1.x only when MODELENGINE or model-backed MYTHICMOBS requests are needed. The live validation used the official free ModelEngine R4.1.1 runtime.
4. Install MythicMobs 5.x only when MYTHICMOBS requests are needed. The live validation used the official free MythicMobs 5.14.0 runtime.
5. Start the server and check `/virtualentities status`.

Without either optional provider, the native VANILLA source remains available. A request requiring a missing provider returns a descriptive failure rather than silently substituting another renderer.

## Public API example

```java
RegisteredServiceProvider<VirtualEntityService> registration =
        Bukkit.getServicesManager().getRegistration(VirtualEntityService.class);
if (registration == null) {
    return;
}

VirtualEntityService service = registration.getProvider();
VirtualEntitySpec spec = VirtualEntitySpec.builder(
                SourceType.MODELENGINE, "prison_guard", location, owningPlugin)
        .audience(AudiencePolicy.player(player))
        .initialAnimation(AnimationSpec.loop("idle"))
        .expiresAfter(Duration.ofSeconds(8))
        .build();

SpawnResult result = service.spawn(spec);
if (result.success()) {
    VirtualEntityHandle handle = result.handle();
    handle.teleport(nextLocation);
    handle.playAnimation("attack");
    handle.remove();
}
```

All public mutations are synchronous and must run on the Paper primary thread. `SpawnResult` and `OperationResult` expose a stable failure category and message. Removing an unknown ID is intentionally idempotent.

## Configuration

`config.yml` controls only infrastructure behavior:

- `default-render-distance`: used when a spec does not explicitly set a render distance.
- `audience-reconciliation-interval-ticks`: periodic eligibility reconciliation interval.
- `max-active-entities`: logical instance limit.
- `max-updates-per-tick`: dirty-transform update budget.
- `debug`: adapter/lifecycle diagnostics.
- `adapters.modelengine` and `adapters.mythicmobs`: capability switches.

Administrative commands require `virtualentities.admin`:

- `/virtualentities status`
- `/virtualentities reload`
- `/virtualentities test [supported-vanilla-type]`
- `/virtualentities remove <logical-uuid>`

## Validation status

The implementation has been compiled, reobfuscated, started on Paper 1.21.11, and exercised with two offline 1.21.11 protocol clients. Native packet rendering, private-viewer isolation, protocol-ID coexistence, explicit removal, expiration, the ModelEngine Dummy adapter, and the MythicMobs visual-definition path passed the live smoke suite. ViaVersion and ViaBackwards 5.12.2 were installed in the test server even though the clients already spoke 1.21.11.

The live provider test used ModelEngine's built-in `internal_fire` model and MythicMobs Free's `SkeletalMinion` definition. The test clients are protocol clients rather than a graphical Minecraft launcher, so the evidence proves login, packet delivery, registry state, viewer isolation, and teardown; it does not claim pixel-level inspection of a rendered screen. A custom MCModels asset, resource-pack appearance, named custom animation playback, and load/performance benchmark remain separate validation gates. See `VALIDATION.md` for the exact results and limitations.
