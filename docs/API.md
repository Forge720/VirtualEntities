# Public API

The service is registered through Bukkit:

```java
RegisteredServiceProvider<VirtualEntityService> registration =
    Bukkit.getServicesManager().getRegistration(VirtualEntityService.class);
VirtualEntityService service = registration == null ? null : registration.getProvider();
```

Create an immutable `VirtualEntitySpec` with `VirtualEntitySpec.builder(...)`, then call `service.spawn(spec)`. A successful `SpawnResult` returns a `VirtualEntityHandle`.

The handle supports location changes, viewer mutation, scale, animation, and idempotent removal. Use `AudiencePolicy.player(...)`, `players(...)`, or `world()`. Use `SourceType.VANILLA`, `MODELENGINE`, or `MYTHICMOBS`.

All mutations must run on the Paper primary thread. Failure results expose a category and message; missing optional providers, unsupported definitions, invalid specifications, terminated handles, and wrong-thread calls are reported explicitly.

Interaction consumers may register a `VirtualInteractionListener` through the service. VirtualEntities only routes authorized interactions; consumer plugins own gameplay behavior.

