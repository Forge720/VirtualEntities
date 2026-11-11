package net.plexmc.virtualentities.api;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

/** Stable consumer-facing controller for one logical virtual entity. */
public interface VirtualEntityHandle {
    UUID getId();
    Set<Integer> getEntityIds();
    Location getLocation();
    OperationResult teleport(Location location);
    OperationResult setViewers(Collection<? extends Player> players);
    OperationResult addViewer(Player player);
    OperationResult removeViewer(Player player);
    OperationResult playAnimation(String animationId);
    OperationResult stopAnimation(String animationId);
    OperationResult setScale(double scale);
    OperationResult remove();
    boolean isRemoved();
}
