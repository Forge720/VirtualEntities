package net.plexmc.virtualentities.internal;

import net.plexmc.virtualentities.VirtualEntitiesPlugin;
import net.plexmc.virtualentities.api.AdapterStatus;
import net.plexmc.virtualentities.api.AnimationSpec;
import net.plexmc.virtualentities.api.FailureReason;
import net.plexmc.virtualentities.api.OperationResult;
import net.plexmc.virtualentities.api.SpawnResult;
import net.plexmc.virtualentities.api.VirtualEntityHandle;
import net.plexmc.virtualentities.api.VirtualInteraction;
import net.plexmc.virtualentities.api.VirtualInteractionListener;
import net.plexmc.virtualentities.api.InteractionAction;
import net.plexmc.virtualentities.api.VirtualEntityService;
import net.plexmc.virtualentities.api.VirtualEntitySpec;
import net.plexmc.virtualentities.internal.render.NmsEntityRenderer;
import net.plexmc.virtualentities.internal.render.ProtocolIdAllocator;
import net.plexmc.virtualentities.internal.render.RendererCreation;
import net.plexmc.virtualentities.internal.render.RendererFactory;
import net.plexmc.virtualentities.internal.render.VirtualRenderer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.util.Collection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Main-thread service implementation. */
public final class VirtualEntityServiceImpl implements VirtualEntityService {
    private final VirtualEntitiesPlugin plugin;
    private final EntityRegistry registry = new EntityRegistry();
    private final ProtocolIdAllocator protocolIdAllocator = new ProtocolIdAllocator();
    private final AudienceManager audienceManager = new AudienceManager(this);
    private final Map<Integer, InternalVirtualEntity> entitiesByProtocolId = new HashMap<>();
    private final Map<Plugin, List<VirtualInteractionListener>> interactionListeners = new IdentityHashMap<>();
    private RendererFactory rendererFactory;
    private BukkitTask ticker;
    private long currentTick;
    private int reconcileInterval;
    private int maxEntities;
    private int maxUpdatesPerTick;
    private double defaultRenderDistance;
    private boolean debug;
    private boolean enabled;

    public VirtualEntityServiceImpl(VirtualEntitiesPlugin plugin) {
        this.plugin = plugin;
        reloadConfiguration();
    }

    public void start() {
        enabled = true;
        ticker = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    public void stop() {
        enabled = false;
        if (ticker != null) {
            ticker.cancel();
            ticker = null;
        }
        for (InternalVirtualEntity entity : registry.snapshot()) {
            removeInternal(entity);
        }
    }

    public void reloadConfiguration() {
        plugin.reloadConfig();
        reconcileInterval = Math.max(1, plugin.getConfig().getInt("audience-reconciliation-interval-ticks", 10));
        maxEntities = Math.max(1, plugin.getConfig().getInt("max-active-entities", 10_000));
        maxUpdatesPerTick = Math.max(1, plugin.getConfig().getInt("max-updates-per-tick", 2_000));
        double configuredDistance = plugin.getConfig().getDouble("default-render-distance", 64.0);
        defaultRenderDistance = Double.isFinite(configuredDistance) && configuredDistance > 0.0
                ? configuredDistance : 64.0;
        debug = plugin.getConfig().getBoolean("debug", false);
        boolean modelEngine = plugin.getConfig().getBoolean("adapters.modelengine", true)
                && Bukkit.getPluginManager().getPlugin("ModelEngine") != null;
        boolean mythicMobs = plugin.getConfig().getBoolean("adapters.mythicmobs", true)
                && Bukkit.getPluginManager().getPlugin("MythicMobs") != null;
        rendererFactory = new RendererFactory(modelEngine, mythicMobs, protocolIdAllocator, plugin);
    }

    @Override
    public SpawnResult spawn(VirtualEntitySpec spec) {
        if (!Bukkit.isPrimaryThread()) {
            return SpawnResult.failure(FailureReason.WRONG_THREAD,
                    "VirtualEntities public mutations must run on the Paper primary thread");
        }
        if (!enabled) {
            return SpawnResult.failure(FailureReason.TERMINATED, "VirtualEntities is shutting down");
        }
        if (spec == null) {
            return SpawnResult.failure(FailureReason.INVALID_SPEC, "spec cannot be null");
        }
        if (!spec.owner().isEnabled()) {
            return SpawnResult.failure(FailureReason.INVALID_SPEC, "Owning plugin is not enabled");
        }
        if (registry.size() >= maxEntities) {
            return SpawnResult.failure(FailureReason.LIMIT_REACHED,
                    "Configured active virtual-entity limit has been reached: " + maxEntities);
        }

        UUID id = UUID.randomUUID();
        RendererCreation creation;
        try {
            creation = rendererFactory.create(id, spec);
        } catch (Throwable throwable) {
            return SpawnResult.failure(FailureReason.INTERNAL_INITIALIZATION_FAILURE,
                    "Renderer initialization failed: " + throwable.getClass().getSimpleName() + ": "
                            + throwable.getMessage());
        }
        if (!creation.success()) {
            return SpawnResult.failure(creation.failureReason(), creation.message());
        }

        long expirationTick = expirationTick(spec.expiration());
        InternalVirtualEntity entity = new InternalVirtualEntity(this, id, spec, creation.renderer(), expirationTick);
        try {
            registry.add(entity);
            for (int entityId : entity.renderer().entityIds()) {
                entitiesByProtocolId.put(entityId, entity);
            }
            audienceManager.reconcile(entity);
            return SpawnResult.success(entity);
        } catch (Throwable throwable) {
            removeInternal(entity);
            return SpawnResult.failure(FailureReason.INTERNAL_INITIALIZATION_FAILURE,
                    "Entity registration failed: " + throwable.getClass().getSimpleName() + ": " + throwable.getMessage());
        }
    }

    @Override
    public Optional<VirtualEntityHandle> get(UUID id) {
        if (id == null) return Optional.empty();
        return Optional.ofNullable(registry.get(id));
    }

    @Override
    public OperationResult remove(UUID id) {
        if (!Bukkit.isPrimaryThread()) {
            return OperationResult.failure(FailureReason.WRONG_THREAD,
                    "VirtualEntities public mutations must run on the Paper primary thread");
        }
        InternalVirtualEntity entity = id == null ? null : registry.get(id);
        if (entity == null) {
            return OperationResult.ok();
        }
        removeInternal(entity);
        return OperationResult.ok();
    }

    @Override
    public int removeAll(Plugin owner) {
        if (!Bukkit.isPrimaryThread() || owner == null) return 0;
        int removed = 0;
        for (InternalVirtualEntity entity : registry.ownedBy(owner)) {
            removeInternal(entity);
            removed++;
        }
        return removed;
    }

    @Override
    public int activeCount() {
        return registry.size();
    }

    @Override
    public AdapterStatus adapterStatus() {
        boolean modelEngine = Bukkit.getPluginManager().getPlugin("ModelEngine") != null
                && Bukkit.getPluginManager().isPluginEnabled("ModelEngine");
        boolean mythicMobs = Bukkit.getPluginManager().getPlugin("MythicMobs") != null
                && Bukkit.getPluginManager().isPluginEnabled("MythicMobs");
        return new AdapterStatus(true, modelEngine, mythicMobs,
                "Paper 1.21.11 NMS adapter active; providers are soft dependencies");
    }

    @Override
    public void registerInteractionListener(Plugin owner, VirtualInteractionListener listener) {
        if (!Bukkit.isPrimaryThread() || owner == null || listener == null) {
            return;
        }
        interactionListeners.computeIfAbsent(owner, ignored -> new ArrayList<>()).add(listener);
    }

    @Override
    public void unregisterInteractionListeners(Plugin owner) {
        if (owner != null) {
            interactionListeners.remove(owner);
        }
    }

    public OperationResult teleport(UUID id, Location location) {
        if (!Bukkit.isPrimaryThread()) return wrongThread();
        InternalVirtualEntity entity = registry.get(id);
        if (entity == null || entity.isRemoved()) return terminated();
        Location old = entity.location();
        OperationResult state = entity.setLocation(location);
        if (!state.success()) return state;
        if (!old.getWorld().equals(location.getWorld())) {
            audienceManager.hideAll(entity);
        }
        OperationResult rendererResult = entity.renderer().updateLocation(location);
        if (!rendererResult.success()) return rendererResult;
        if (!old.getWorld().equals(location.getWorld())) {
            audienceManager.reconcile(entity);
        }
        return OperationResult.ok();
    }

    public OperationResult setViewers(UUID id, Collection<? extends Player> players) {
        if (!Bukkit.isPrimaryThread()) return wrongThread();
        InternalVirtualEntity entity = registry.get(id);
        return entity == null || entity.isRemoved() ? terminated() : audienceManager.setViewers(entity, players);
    }

    public OperationResult addViewer(UUID id, Player player) {
        if (!Bukkit.isPrimaryThread()) return wrongThread();
        InternalVirtualEntity entity = registry.get(id);
        return entity == null || entity.isRemoved() ? terminated() : audienceManager.addViewer(entity, player);
    }

    public OperationResult removeViewer(UUID id, Player player) {
        if (!Bukkit.isPrimaryThread()) return wrongThread();
        InternalVirtualEntity entity = registry.get(id);
        return entity == null || entity.isRemoved() ? terminated() : audienceManager.removeViewer(entity, player);
    }

    public OperationResult playAnimation(UUID id, String animationId) {
        if (!Bukkit.isPrimaryThread()) return wrongThread();
        InternalVirtualEntity entity = registry.get(id);
        if (entity == null || entity.isRemoved()) return terminated();
        if (animationId == null || animationId.isBlank()) {
            return OperationResult.failure(FailureReason.INVALID_SPEC, "animationId cannot be empty");
        }
        try {
            return entity.renderer().playAnimation(AnimationSpec.once(animationId));
        } catch (IllegalArgumentException exception) {
            return OperationResult.failure(FailureReason.INVALID_SPEC, exception.getMessage());
        }
    }

    public OperationResult stopAnimation(UUID id, String animationId) {
        if (!Bukkit.isPrimaryThread()) return wrongThread();
        InternalVirtualEntity entity = registry.get(id);
        if (entity == null || entity.isRemoved()) return terminated();
        if (animationId == null || animationId.isBlank()) {
            return OperationResult.failure(FailureReason.INVALID_SPEC, "animationId cannot be empty");
        }
        return entity.renderer().stopAnimation(animationId);
    }

    public OperationResult setScale(UUID id, double scale) {
        if (!Bukkit.isPrimaryThread()) return wrongThread();
        if (!Double.isFinite(scale) || scale <= 0.0 || scale > 64.0) {
            return OperationResult.failure(FailureReason.INVALID_SPEC,
                    "scale must be finite, positive, and no greater than 64");
        }
        InternalVirtualEntity entity = registry.get(id);
        if (entity == null || entity.isRemoved()) return terminated();
        OperationResult result = entity.renderer().updateScale(scale);
        if (result.success()) {
            entity.renderer().sendUpdatedState(audienceManager.renderedPlayers(entity));
        }
        return result;
    }

    /** Called by the shared Paper unknown-entity interaction event bridge. */
    public void handleUnknownEntityInteraction(Player player, int entityId, boolean attack,
                                               EquipmentSlot hand, Vector clickedPosition) {
        if (player == null || hand == null) {
            return;
        }
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(plugin,
                    () -> handleUnknownEntityInteraction(player, entityId, attack, hand, clickedPosition));
            return;
        }
        InternalVirtualEntity entity = entitiesByProtocolId.get(entityId);
        if (entity == null) {
            debug("Unknown-entity interaction id=" + entityId + " is not registered");
            return;
        }
        if (entity.isRemoved() || !entity.renderedViewers().contains(player.getUniqueId())) {
            debug("Unknown-entity interaction id=" + entityId + " rejected: removed=" + entity.isRemoved()
                    + " rendered=" + entity.renderedViewers().contains(player.getUniqueId()));
            return;
        }
        Location location = entity.location();
        if (location.getWorld() == null || !location.getWorld().equals(player.getWorld())
                || player.getLocation().distanceSquared(location) > Math.max(8.0, renderDistance(entity) + 2.0) *
                Math.max(8.0, renderDistance(entity) + 2.0)) {
            debug("Unknown-entity interaction id=" + entityId + " rejected: world/distance");
            return;
        }
        debug("Dispatching virtual interaction id=" + entityId + " listeners=" + interactionListeners.size());
        VirtualInteraction interaction = new VirtualInteraction(entity.id(), player,
                attack ? InteractionAction.ATTACK : InteractionAction.INTERACT, hand, clickedPosition);
        for (List<VirtualInteractionListener> listeners : interactionListeners.values()) {
            for (VirtualInteractionListener listener : List.copyOf(listeners)) {
                try {
                    listener.onInteraction(interaction);
                } catch (Throwable throwable) {
                    plugin.getLogger().log(java.util.logging.Level.WARNING,
                            "Virtual interaction listener failed", throwable);
                }
            }
        }
    }

    public void onPlayerQuit(Player player) {
        audienceManager.onQuit(player);
    }

    public void removeProviderEntities(net.plexmc.virtualentities.api.SourceType sourceType) {
        if (!Bukkit.isPrimaryThread()) return;
        for (InternalVirtualEntity entity : registry.snapshot()) {
            if (entity.spec().sourceType() == sourceType) {
                removeInternal(entity);
            }
        }
    }

    public void reconcileAll() {
        if (!Bukkit.isPrimaryThread()) return;
        for (InternalVirtualEntity entity : registry.snapshot()) {
            audienceManager.reconcile(entity);
        }
    }

    public EntityRegistry registry() {
        return registry;
    }

    public AudienceManager audienceManager() {
        return audienceManager;
    }

    public double renderDistance(InternalVirtualEntity entity) {
        return entity.spec().usesDefaultRenderDistance()
                ? defaultRenderDistance : entity.spec().renderDistance();
    }

    public void debug(String message) {
        if (debug) plugin.getLogger().info("[debug] " + message);
    }

    private void tick() {
        if (!enabled) return;
        currentTick++;
        int updates = 0;
        for (InternalVirtualEntity entity : registry.snapshot()) {
            if (entity.isExpired(currentTick)) {
                removeInternal(entity);
                continue;
            }
            if (currentTick % reconcileInterval == 0) {
                audienceManager.reconcile(entity);
            }
            if (entity.dirtyTransform() && updates++ < maxUpdatesPerTick) {
                OperationResult result = entity.renderer().updateLocation(entity.location());
                if (result.success()) {
                    entity.renderer().sendTransform(audienceManager.renderedPlayers(entity));
                    entity.clearDirtyTransform();
                } else {
                    debug("Transform update failed for " + entity.id() + ": " + result.message());
                }
            }
        }
    }

    private void removeInternal(InternalVirtualEntity entity) {
        if (entity == null || entity.isRemoved()) return;
        audienceManager.hideAll(entity);
        entity.markRemoved();
        for (int entityId : entity.renderer().entityIds()) {
            entitiesByProtocolId.remove(entityId, entity);
        }
        registry.remove(entity.id());
        VirtualRenderer renderer = entity.renderer();
        renderer.dispose();
        rendererFactory.release(renderer);
    }

    private long expirationTick(Duration duration) {
        if (duration == null) return 0;
        long millis = Math.max(1L, duration.toMillis());
        long ticks = Math.max(1L, (millis + 49L) / 50L);
        return currentTick + ticks;
    }

    private OperationResult wrongThread() {
        return OperationResult.failure(FailureReason.WRONG_THREAD,
                "VirtualEntities public mutations must run on the Paper primary thread");
    }

    private OperationResult terminated() {
        return OperationResult.failure(FailureReason.TERMINATED, "Virtual entity does not exist or has been removed");
    }
}
