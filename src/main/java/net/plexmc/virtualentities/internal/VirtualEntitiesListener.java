package net.plexmc.virtualentities.internal;

import net.plexmc.virtualentities.VirtualEntitiesPlugin;
import net.plexmc.virtualentities.api.SourceType;
import net.plexmc.virtualentities.internal.render.VirtualRenderer;
import org.bukkit.Bukkit;
import com.destroystokyo.paper.event.player.PlayerUseUnknownEntityEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;

/** Event bridge for viewer transitions and provider lifecycle changes. */
public final class VirtualEntitiesListener implements Listener {
    private final VirtualEntitiesPlugin plugin;
    private final VirtualEntityServiceImpl service;

    public VirtualEntitiesListener(VirtualEntitiesPlugin plugin, VirtualEntityServiceImpl service) {
        this.plugin = plugin;
        this.service = service;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        service.onPlayerQuit(event.getPlayer());
    }

    @EventHandler
    public void onUnknownEntityUse(PlayerUseUnknownEntityEvent event) {
        service.debug("Unknown-entity interaction id=" + event.getEntityId() + " player="
                + event.getPlayer().getName() + " attack=" + event.isAttack() + " hand=" + event.getHand());
        service.handleUnknownEntityInteraction(event.getPlayer(), event.getEntityId(), event.isAttack(),
                event.getHand(), event.getClickedRelativePosition());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTask(plugin, service::reconcileAll);
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        service.reconcileAll();
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        if (event.getTo() != null && event.getFrom().getWorld() != event.getTo().getWorld()) {
            service.reconcileAll();
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Bukkit.getScheduler().runTask(plugin, service::reconcileAll);
    }

    @EventHandler
    public void onPluginEnable(PluginEnableEvent event) {
        if (event.getPlugin().getName().equals("ModelEngine") || event.getPlugin().getName().equals("MythicMobs")) {
            service.reloadConfiguration();
            service.reconcileAll();
        }
    }

    @EventHandler
    public void onPluginDisable(PluginDisableEvent event) {
        if (event.getPlugin() == plugin) return;
        if (event.getPlugin().getName().equals("ModelEngine")) {
            service.removeProviderEntities(SourceType.MODELENGINE);
            service.removeProviderEntities(SourceType.MYTHICMOBS);
        } else if (event.getPlugin().getName().equals("MythicMobs")) {
            service.removeProviderEntities(SourceType.MYTHICMOBS);
        } else {
            service.removeAll(event.getPlugin());
            service.unregisterInteractionListeners(event.getPlugin());
        }
    }
}
