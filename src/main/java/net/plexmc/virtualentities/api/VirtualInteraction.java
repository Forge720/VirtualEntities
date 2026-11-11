package net.plexmc.virtualentities.api;

import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.util.Vector;

import java.util.Objects;
import java.util.UUID;

/** A validated interaction with one viewer-specific virtual entity. */
public record VirtualInteraction(
        UUID virtualEntityId,
        Player player,
        InteractionAction action,
        EquipmentSlot hand,
        Vector clickedPosition
) {
    public VirtualInteraction {
        Objects.requireNonNull(virtualEntityId, "virtualEntityId");
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(hand, "hand");
        clickedPosition = clickedPosition == null ? new Vector() : clickedPosition.clone();
    }
}
