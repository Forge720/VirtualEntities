package net.plexmc.virtualentities.internal;

import net.plexmc.virtualentities.api.AudiencePolicy;
import net.plexmc.virtualentities.api.FailureReason;
import net.plexmc.virtualentities.api.OperationResult;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Reconciles desired authorization with the set of clients actually rendered to. */
public final class AudienceManager {
    private final VirtualEntityServiceImpl service;

    public AudienceManager(VirtualEntityServiceImpl service) {
        this.service = service;
    }

    public void reconcile(InternalVirtualEntity entity) {
        if (entity.isRemoved()) {
            return;
        }
        if (entity.audienceMode() == AudiencePolicy.Mode.WORLD) {
            entity.authorizedViewers().clear();
            Location location = entity.location();
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getWorld().equals(location.getWorld())) {
                    entity.authorizedViewers().add(player.getUniqueId());
                }
            }
        }

        Set<UUID> eligible = new HashSet<>();
        for (UUID viewerId : entity.authorizedViewers()) {
            Player player = Bukkit.getPlayer(viewerId);
            if (player != null && isEligible(entity, player)) {
                eligible.add(viewerId);
            }
        }

        for (UUID viewerId : new HashSet<>(entity.renderedViewers())) {
            if (!eligible.contains(viewerId)) {
                Player player = Bukkit.getPlayer(viewerId);
                if (player != null) {
                    entity.renderer().hide(player);
                }
                entity.renderedViewers().remove(viewerId);
            }
        }
        for (UUID viewerId : eligible) {
            if (!entity.renderedViewers().contains(viewerId)) {
                Player player = Bukkit.getPlayer(viewerId);
                if (player != null) {
                    OperationResult result = entity.renderer().show(player);
                    if (result.success()) {
                        entity.renderedViewers().add(viewerId);
                    } else {
                        service.debug("Could not render " + entity.id() + " to " + player.getName() + ": "
                                + result.message());
                    }
                }
            }
        }
    }

    public OperationResult setViewers(InternalVirtualEntity entity, Collection<? extends Player> players) {
        if (players == null) {
            return OperationResult.failure(FailureReason.INVALID_SPEC, "players cannot be null");
        }
        Set<UUID> ids = new HashSet<>();
        for (Player player : players) {
            if (player == null) {
                return OperationResult.failure(FailureReason.INVALID_SPEC, "players cannot contain null");
            }
            ids.add(player.getUniqueId());
        }
        entity.setAudienceMode(AudiencePolicy.Mode.PLAYERS);
        entity.authorizedViewers().clear();
        entity.authorizedViewers().addAll(ids);
        reconcile(entity);
        return OperationResult.ok();
    }

    public OperationResult addViewer(InternalVirtualEntity entity, Player player) {
        if (player == null) {
            return OperationResult.failure(FailureReason.INVALID_SPEC, "player cannot be null");
        }
        entity.setAudienceMode(AudiencePolicy.Mode.PLAYERS);
        entity.authorizedViewers().add(player.getUniqueId());
        reconcile(entity);
        return OperationResult.ok();
    }

    public OperationResult removeViewer(InternalVirtualEntity entity, Player player) {
        if (player == null) {
            return OperationResult.failure(FailureReason.INVALID_SPEC, "player cannot be null");
        }
        entity.authorizedViewers().remove(player.getUniqueId());
        if (entity.renderedViewers().remove(player.getUniqueId())) {
            entity.renderer().hide(player);
        }
        return OperationResult.ok();
    }

    public void onQuit(Player player) {
        UUID id = player.getUniqueId();
        for (InternalVirtualEntity entity : service.registry().snapshot()) {
            entity.renderedViewers().remove(id);
        }
    }

    public void hideAll(InternalVirtualEntity entity) {
        for (UUID viewerId : new HashSet<>(entity.renderedViewers())) {
            Player player = Bukkit.getPlayer(viewerId);
            if (player != null) {
                entity.renderer().hide(player);
            }
        }
        entity.renderedViewers().clear();
    }

    public Collection<Player> renderedPlayers(InternalVirtualEntity entity) {
        ArrayList<Player> players = new ArrayList<>(entity.renderedViewers().size());
        for (UUID viewerId : entity.renderedViewers()) {
            Player player = Bukkit.getPlayer(viewerId);
            if (player != null) {
                players.add(player);
            }
        }
        return players;
    }

    private boolean isEligible(InternalVirtualEntity entity, Player player) {
        Location entityLocation = entity.location();
        if (!player.isOnline() || player.isDead() || !player.getWorld().equals(entityLocation.getWorld())) {
            return false;
        }
        double renderDistance = service.renderDistance(entity);
        if (player.getLocation().distanceSquared(entityLocation) > renderDistance * renderDistance) {
            return false;
        }
        int entityChunkX = entityLocation.getBlockX() >> 4;
        int entityChunkZ = entityLocation.getBlockZ() >> 4;
        int playerChunkX = player.getLocation().getBlockX() >> 4;
        int playerChunkZ = player.getLocation().getBlockZ() >> 4;
        return entityLocation.getWorld().isChunkLoaded(entityChunkX, entityChunkZ)
                && entityLocation.getWorld().isChunkLoaded(playerChunkX, playerChunkZ);
    }
}
