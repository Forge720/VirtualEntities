package net.plexmc.virtualentities.api;

import org.bukkit.plugin.Plugin;

import java.util.Optional;
import java.util.UUID;

/** Versioned service contract published through Bukkit's ServicesManager. */
public interface VirtualEntityService {
    String API_VERSION = "0.1";

    SpawnResult spawn(VirtualEntitySpec spec);
    Optional<VirtualEntityHandle> get(UUID id);
    OperationResult remove(UUID id);
    int removeAll(Plugin owner);
    int activeCount();
    AdapterStatus adapterStatus();
    void registerInteractionListener(Plugin owner, VirtualInteractionListener listener);
    void unregisterInteractionListeners(Plugin owner);
}
