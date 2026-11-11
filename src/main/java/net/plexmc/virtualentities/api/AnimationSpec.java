package net.plexmc.virtualentities.api;

import java.util.Objects;

/** Initial or named ModelEngine animation parameters. */
public record AnimationSpec(String id, double speed, double transitionSeconds, double blendSeconds, boolean loop) {
    public AnimationSpec {
        Objects.requireNonNull(id, "id");
        id = id.trim();
        if (id.isEmpty()) {
            throw new IllegalArgumentException("Animation id cannot be empty");
        }
        if (!Double.isFinite(speed) || speed <= 0.0) {
            throw new IllegalArgumentException("Animation speed must be finite and positive");
        }
        if (!Double.isFinite(transitionSeconds) || transitionSeconds < 0.0) {
            throw new IllegalArgumentException("Animation transition must be finite and non-negative");
        }
        if (!Double.isFinite(blendSeconds) || blendSeconds < 0.0) {
            throw new IllegalArgumentException("Animation blend must be finite and non-negative");
        }
    }

    public static AnimationSpec loop(String id) {
        return new AnimationSpec(id, 1.0, 0.0, 0.0, true);
    }

    public static AnimationSpec once(String id) {
        return new AnimationSpec(id, 1.0, 0.0, 0.0, false);
    }
}
