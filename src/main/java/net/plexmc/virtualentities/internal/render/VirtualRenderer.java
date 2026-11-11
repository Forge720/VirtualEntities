package net.plexmc.virtualentities.internal.render;

import net.plexmc.virtualentities.api.AnimationSpec;
import net.plexmc.virtualentities.api.OperationResult;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Set;

/** Provider-neutral renderer contract. Implementations are server-thread only. */
public interface VirtualRenderer {
    String providerName();

    OperationResult show(Player player);
    OperationResult hide(Player player);
    OperationResult updateLocation(Location location);
    OperationResult playAnimation(AnimationSpec animation);
    OperationResult stopAnimation(String animationId);
    default OperationResult updateScale(double scale) {
        return OperationResult.failure(net.plexmc.virtualentities.api.FailureReason.UNSUPPORTED_OPERATION,
                "This renderer does not support scale mutation");
    }
    default Set<Integer> entityIds() {
        return Set.of();
    }
    default OperationResult sendTransform(Iterable<Player> viewers) {
        return OperationResult.ok();
    }
    default void sendUpdatedState(Iterable<Player> viewers) {
    }
    void dispose();
}
