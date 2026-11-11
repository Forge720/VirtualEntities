package net.plexmc.virtualentities.internal;

import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Lightweight authoritative logical registry with owner indexing. */
public final class EntityRegistry {
    private final Map<UUID, InternalVirtualEntity> entities = new HashMap<>();
    private final Map<Plugin, Set<UUID>> byOwner = new IdentityHashMap<>();

    public void add(InternalVirtualEntity entity) {
        if (entities.putIfAbsent(entity.id(), entity) != null) {
            throw new IllegalStateException("Duplicate virtual entity UUID: " + entity.id());
        }
        byOwner.computeIfAbsent(entity.owner(), ignored -> new java.util.HashSet<>()).add(entity.id());
    }

    public InternalVirtualEntity remove(UUID id) {
        InternalVirtualEntity entity = entities.remove(id);
        if (entity != null) {
            Set<UUID> owned = byOwner.get(entity.owner());
            if (owned != null) {
                owned.remove(id);
                if (owned.isEmpty()) {
                    byOwner.remove(entity.owner());
                }
            }
        }
        return entity;
    }

    public InternalVirtualEntity get(UUID id) { return entities.get(id); }
    public int size() { return entities.size(); }
    public Collection<InternalVirtualEntity> snapshot() {
        return Collections.unmodifiableList(new ArrayList<>(entities.values()));
    }
    public Collection<InternalVirtualEntity> ownedBy(Plugin owner) {
        Set<UUID> ids = byOwner.getOrDefault(owner, Set.of());
        ArrayList<InternalVirtualEntity> result = new ArrayList<>(ids.size());
        for (UUID id : ids) {
            InternalVirtualEntity entity = entities.get(id);
            if (entity != null) result.add(entity);
        }
        return result;
    }
}
