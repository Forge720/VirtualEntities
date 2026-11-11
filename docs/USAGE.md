# VirtualEntities — Admin and AI Usage Guide

## Requirements

- Paper 1.21.11
- Java 21
- Optional: ModelEngine 4.1.x for `MODELENGINE` and model-backed `MYTHICMOBS`
- Optional: MythicMobs 5.x for `MYTHICMOBS`

## Build

```bash
./gradlew clean build
```

The wrapper uses Gradle 9.9.0. The deployable runtime JAR is:

```text
build/libs/VirtualEntities-0.1.0-SNAPSHOT-reobf.jar
```

Gradle must be launched with a Java 21 JDK. If it is installed elsewhere:

```bash
JAVA_HOME=/path/to/jdk-21 /path/to/jdk-21/bin/java -version
JAVA_HOME=/path/to/jdk-21 ./gradlew clean build
```

## Install

Copy the reobfuscated JAR to `plugins/` on Paper 1.21.11 and restart. Install optional providers before VirtualEntities when their APIs are used. Verify with:

```text
/virtualentities status
```

## Admin commands

Permission: `virtualentities.admin` (default: operator)

```text
/virtualentities status
/virtualentities reload
/virtualentities test <supported-vanilla-type>
/virtualentities remove <logical-uuid>
```

Supported test types include `armor_stand`, `zombie`, `skeleton`, `creeper`, `spider`, `villager`, `item_display`, `block_display`, `text_display`, `marker`, and `interaction`.

## Configuration

`plugins/VirtualEntities/config.yml`:

- `default-render-distance`: default viewer range.
- `audience-reconciliation-interval-ticks`: viewer reconciliation interval.
- `max-active-entities`: logical entity limit.
- `max-updates-per-tick`: transform update budget.
- `debug`: lifecycle/provider logging.
- `adapters.modelengine` / `adapters.mythicmobs`: capability switches.

## AI developer API contract

Obtain `VirtualEntityService` from Bukkit’s service manager. Build an immutable `VirtualEntitySpec`, call `spawn`, and control the returned `VirtualEntityHandle`. Public mutations are primary-thread-only. Use `AudiencePolicy.player`, `players`, or `world`; choose `SourceType.VANILLA`, `MODELENGINE`, or `MYTHICMOBS`.

VirtualEntities owns packet rendering, viewers, transforms, expiration, cleanup, and provider adapters. Consumer plugins own gameplay logic. Missing optional providers return explicit failure results; they are never silently substituted.

## Verification

Check the server log for successful enablement and use `/virtualentities status`. The supplied validation report records the exact live Paper 1.21.11 smoke tests and remaining unverified provider/performance gates.

