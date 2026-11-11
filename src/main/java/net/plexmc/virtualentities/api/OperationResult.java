package net.plexmc.virtualentities.api;

import java.util.Objects;

/** Result of a public virtual-entity mutation. */
public record OperationResult(boolean success, FailureReason failureReason, String message) {
    public OperationResult {
        Objects.requireNonNull(failureReason, "failureReason");
        Objects.requireNonNull(message, "message");
        if (success && failureReason != FailureReason.NONE) {
            throw new IllegalArgumentException("Successful results must use FailureReason.NONE");
        }
        if (!success && failureReason == FailureReason.NONE) {
            throw new IllegalArgumentException("Failed results must provide a failure reason");
        }
    }

    public static OperationResult ok() {
        return new OperationResult(true, FailureReason.NONE, "ok");
    }

    public static OperationResult failure(FailureReason reason, String message) {
        return new OperationResult(false, reason, message);
    }
}
