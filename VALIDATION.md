# VirtualEntities v0.1 Validation Report

Validation date: 2026-11-04 UTC

This report separates checks actually executed from requirements that still need a graphical client, a downloadable custom asset, or a load-test environment. It is evidence for the tested core paths, not an assertion that every acceptance criterion is complete.

## Test environment

| Component | Version / detail |
|---|---|
| Server | Paper 1.21.11-132, Minecraft protocol 1.21.11 (774) |
| Java | OpenJDK 21 |
| Compatibility plugins | ViaVersion 5.12.2 and ViaBackwards 5.12.2 |
| Optional provider runtimes | ModelEngine Free R4.1.1; MythicMobs Free 5.14.0 |
| Clients | Two independent offline 1.21.11 protocol clients, `VE_A` and `VE_B` |
| Server mode | Offline mode, loopback-only test server |
| Native test world | Clean Paper world; ordinary mob spawning disabled during provider attribution |

## Build and packaging

| Check | Result | Evidence |
|---|---|---|
| Paperweight compile | PASS | `clean build` completed against the Paper 1.21.11 development bundle, Java 21, ModelEngine R4.1.0 compile-only API, and Mythic-Dist 5.12.1 compile-only API. |
| Automated tests | PASS | 7 JUnit tests, 0 failures, 0 errors, 0 skipped. |
| Public API Javadoc | PASS | `javadoc` and `javadocJar` completed. |
| Runtime remapping | PASS | `reobfJar` completed; the tested JAR is the remapped output. |
| Optional dependency packaging | PASS | The deployable JAR contains no provider implementation classes or bundled NMS implementation. |
| Clean startup without providers | PASS | Paper loaded VirtualEntities with native capability available and provider capabilities disabled. |
| Provider startup | PASS | Both official free provider runtimes enabled together with VirtualEntities. |

## Native two-client live test

The harness connected both clients, issued commands through the server, and inspected clientbound packets plus server-side registry/world state.

| Check | Result | Observed evidence |
|---|---|---|
| Both offline clients connected | PASS | Both logged into Paper 1.21.11. |
| Adapter status | PASS | `active=0 vanilla=true modelengine=true mythicmobs=true`. |
| Private viewer delivery | PASS | `VE_A` received one virtual armor-stand spawn packet. |
| Private viewer isolation | PASS | `VE_B` received zero spawn packets for that entity. |
| Packet-only world state | PASS | `/kill @e[type=armor_stand]` returned `argument.entity.notfound.entity`; no Bukkit armor stand existed. |
| Logical registration | PASS | Registry reported one active logical instance. |
| Explicit removal | PASS | `/virtualentities remove <uuid>` returned `Removed.` and the client received destruction packets. |
| Second supported type | PASS | Zombie spawn packet received with protocol type `150`. |
| Expiration | PASS | Expiring entity produced destruction packets and registry returned to `active=0`. |
| Concurrent protocol identities | PASS | Native virtual IDs `1000000000` and `999999999` were distinct and coexisted with player entity IDs. |

## ModelEngine live test

The test used the built-in ModelEngine model `internal_fire`, so it did not depend on a third-party resource-pack download.

| Check | Result | Observed evidence |
|---|---|---|
| ModelEngine capability detection | PASS | VirtualEntities reported `modelengine=true`. |
| Model registry resolution | PASS | `internal_fire` was present in the live ModelEngine registry. |
| Dummy construction and attachment | PASS | Public API spawn succeeded after using ModelEngine-owned Dummy identity allocation. |
| No world entity | PASS | World entity count delta was `0`. |
| Client packet rendering | PASS | ModelEngine emitted 8 model entity packet streams to the authorized client. |
| Private audience | PASS | The control client received 0 packets for the model instance. |
| Transform path | PASS | Teleport and scale mutations completed in the live smoke harness. |
| Cleanup | PASS | Explicit removal succeeded and emitted teardown packets for 11 ModelEngine entity IDs. |

This verifies the ModelEngine virtual-Dummy integration and viewer authorization path. It does not by itself prove the appearance of a custom third-party model on a graphical client or named animation playback for an external asset.

## MythicMobs live test

The test used MythicMobs Free's registered `SkeletalMinion` definition, which resolves to a supported skeleton visual in this v0.1 boundary.

| Check | Result | Observed evidence |
|---|---|---|
| MythicMobs capability detection | PASS | VirtualEntities reported `mythicmobs=true`. |
| Definition lookup | PASS | `SkeletalMinion` resolved through the live MythicMobs API. |
| No ActiveMob spawn | PASS | The harness observed no world entity delta and no ActiveMob creation path. |
| Native visual output | PASS | One private spawn-like packet was received by the authorized client. |
| Private audience | PASS | The control client received 0 spawn-like packets. |
| Removal | PASS | Visual removal succeeded without a second logical instance. |
| AI/skills/drops boundary | PASS | The test did not invoke MythicMobs spawn, AI, skills, combat, or drops. |

## MCModels / custom asset investigation

The free [Chlly Guy](https://mcmodels.net/products/13029/chlly-guy) package was located and its public product page identified it as a free ModelEngine setup with Model BBMODEL and MythicMobs setup. The package download endpoint returned HTTP 403 from MCModels FlowGuard in this environment. I did not bypass that protection, create an account, or claim that the asset was loaded. Therefore custom-asset pixel/animation validation remains open.

## Mature test-and-fix loop used

1. Compile, unit-test, generate Javadocs, reobfuscate, and hash the deployable JAR.
2. Start a clean Paper server without optional providers and verify native capability, command status, and shutdown.
3. Start a separate offline server with both free provider runtimes plus ViaVersion/ViaBackwards.
4. Connect two protocol clients and record packet counts, protocol IDs, logical registry counts, and Bukkit world-entity deltas.
5. Exercise private viewer delivery, viewer isolation, explicit removal, a second entity type, and expiration.
6. Exercise ModelEngine model lookup, Dummy creation, transform, private viewing, and teardown.
7. Exercise MythicMobs definition resolution, native visual delegation, private viewing, and teardown.
8. When a test failed, fix the implementation or fixture and rerun the affected suite. The real provider defect found in this loop was ModelEngine Dummy initialization: the explicit integer constructor did not initialize R4.1.x tracked data correctly. The renderer now uses the ModelEngine-owned UUID constructor, then the complete build and provider suite were rerun successfully.
9. Preserve the remaining gates explicitly rather than marking graphical custom-asset and performance tests as passed.

## Not executed / remaining production gates

- Pixel-level inspection with a real graphical Minecraft launcher.
- The Chlly Guy MCModels package and its resource pack, because the download was blocked by MCModels FlowGuard.
- Named custom animation playback against a third-party asset.
- Client chunk unload/reload, reconnect ghost-entity, cross-world, and large-scale audience-transition scenarios.
- Packet-volume, tick-cost, memory-retention, and multi-entity load benchmarks.

The tested native and provider smoke paths are green, but these remaining gates mean this report should not be read as a blanket production-readiness certificate for every v0.1 acceptance item.

## Environment limitations

Paper and the provider plugins emitted unrelated update/authentication lookup warnings because external Mojang/Paper endpoints were unavailable from the test runtime. The server still enabled all plugins and completed the tests. ModelEngine also emitted skin lookup warnings for the offline test names; those are unrelated to the `internal_fire` model path.

## Artifact identity

The final deployable file is `VirtualEntities.jar`, copied from the successful Paperweight-reobfuscated output.

```text
cfe4b8dbf815d0008d28c393bbb5fd5ff777f093ca01c494f6f5cd072815c0f5  VirtualEntities.jar
```
