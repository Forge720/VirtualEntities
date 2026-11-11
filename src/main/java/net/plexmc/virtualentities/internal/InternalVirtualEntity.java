package net.plexmc.virtualentities.internal;

import net.plexmc.virtualentities.api.AudiencePolicy;
import net.plexmc.virtualentities.api.OperationResult;
import net.plexmc.virtualentities.api.VirtualEntityHandle;
import net.plexmc.virtualentities.api.VirtualEntitySpec;
import net.plexmc.virtualentities.internal.render.NmsEntityRenderer;
import net.plexmc.virtualentities.internal.render.VirtualRenderer;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Authoritative state for one logical instance; never exposed directly to consumers. */
public final class InternalVirtualEntity implements VirtualEntityHandle {
    private final VirtualEntityServiceImpl service;
    private final UUID id;
    private final Plugin owner;
    private final VirtualEntitySpec spec;
    private final VirtualRenderer renderer;
    private final Set<UUID> authorizedViewers = new HashSet<>();
    private final Set<UUID> renderedViewers = new HashSet<>();
    private AudiencePolicy.Mode audienceMode;
    private Location location;
    private boolean dirtyTransform;
    private boolean removed;
    private long expiresAtTick;

    public InternalVirtualEntity(VirtualEntityServiceImpl service, UUID id, VirtualEntitySpec spec,
                                 VirtualRenderer renderer, long expiresAtTick) {
        this.service = service;
        this.id = id;
        this.owner = spec.owner();
        this.spec = spec;
        this.renderer = renderer;
        this.location = spec.location();
        this.audienceMode = spec.audience().mode();
        this.authorizedViewers.addAll(spec.audience().playerIds());
        this.expiresAtTick = expiresAtTick;
    }

    public UUID id() { return id; }
    public Plugin owner() { return owner; }
    public VirtualEntitySpec spec() { return spec; }
    public VirtualRenderer renderer() { return renderer; }
    public Set<UUID> authorizedViewers() { return authorizedViewers; }
    public Set<UUID> renderedViewers() { return renderedViewers; }
    public AudiencePolicy.Mode audienceMode() { return audienceMode; }
    public void setAudienceMode(AudiencePolicy.Mode audienceMode) { this.audienceMode = audienceMode; }
    public Location location() { return location.clone(); }
    public boolean dirtyTransform() { return dirtyTransform; }
    public void clearDirtyTransform() { dirtyTransform = false; }
    public long expiresAtTick() { return expiresAtTick; }
    public void setExpiresAtTick(long expiresAtTick) { this.expiresAtTick = expiresAtTick; }

    public OperationResult setLocation(Location location) {
        if (removed) {
            return OperationResult.failure(net.plexmc.virtualentities.api.FailureReason.TERMINATED,
                    "Virtual entity has been removed");
        }
        if (location == null || location.getWorld() == null) {
            return OperationResult.failure(net.plexmc.virtualentities.api.FailureReason.INVALID_LOCATION,
                    "Location and world are required");
        }
        if (!Double.isFinite(location.getX()) || !Double.isFinite(location.getY()) || !Double.isFinite(location.getZ())
                || !Float.isFinite(location.getYaw()) || !Float.isFinite(location.getPitch())) {
            return OperationResult.failure(net.plexmc.virtualentities.api.FailureReason.INVALID_LOCATION,
                    "Location coordinates and rotation must be finite");
        }
        this.location = location.clone();
        this.dirtyTransform = true;
        return OperationResult.ok();
    }

    public boolean isExpired(long currentTick) {
        return expiresAtTick > 0 && currentTick >= expiresAtTick;
    }

    public void markRemoved() { removed = true; }
    @Override public boolean isRemoved() { return removed; }

    @Override public UUID getId() { return id; }
    @Override public Set<Integer> getEntityIds() { return renderer.entityIds(); }
    @Override public Location getLocation() { return location(); }

    @Override public OperationResult teleport(Location location) { return service.teleport(id, location); }
    @Override public OperationResult setViewers(Collection<? extends Player> players) { return service.setViewers(id, players); }
    @Override public OperationResult addViewer(Player player) { return service.addViewer(id, player); }
    @Override public OperationResult removeViewer(Player player) { return service.removeViewer(id, player); }
    @Override public OperationResult playAnimation(String animationId) { return service.playAnimation(id, animationId); }
    @Override public OperationResult stopAnimation(String animationId) { return service.stopAnimation(id, animationId); }
    @Override public OperationResult setScale(double scale) { return service.setScale(id, scale); }
    @Override public OperationResult remove() { return service.remove(id); }
}
