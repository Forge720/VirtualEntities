package net.plexmc.virtualentities.api;

/** Observable provider status for diagnostics. */
public record AdapterStatus(boolean vanilla, boolean modelEngine, boolean mythicMobs, String details) {
}
