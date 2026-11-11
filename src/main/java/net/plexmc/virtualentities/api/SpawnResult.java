package net.plexmc.virtualentities.api;

import java.util.Objects;

/** Result of a spawn request. */
public record SpawnResult(VirtualEntityHandle handle, FailureReason failureReason, String message) {
    public SpawnResult {
        Objects.requireNonNull(failureReason, "failureReason");
        Objects.requireNonNull(message, "message");
        if (handle != null && failureReason != FailureReason.NONE) {
            throw new IllegalArgumentException("Successful spawns must use FailureReason.NONE");
        }
        if (handle == null && failureReason == FailureReason.NONE) {
            throw new IllegalArgumentException("Failed spawns must provide a failure reason");
        }
    }

    public boolean success() {
        return handle != null;
    }

    public static SpawnResult success(VirtualEntityHandle handle) {
        return new SpawnResult(Objects.requireNonNull(handle, "handle"), FailureReason.NONE, "ok");
    }

    public static SpawnResult failure(FailureReason reason, String message) {
        return new SpawnResult(null, reason, message);
    }
}
