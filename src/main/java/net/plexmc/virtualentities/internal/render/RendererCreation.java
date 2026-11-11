package net.plexmc.virtualentities.internal.render;

import net.plexmc.virtualentities.api.FailureReason;

public record RendererCreation(VirtualRenderer renderer, FailureReason failureReason, String message) {
    public static RendererCreation success(VirtualRenderer renderer) {
        return new RendererCreation(renderer, FailureReason.NONE, "ok");
    }

    public static RendererCreation failure(FailureReason reason, String message) {
        return new RendererCreation(null, reason, message);
    }

    public boolean success() {
        return renderer != null;
    }
}
