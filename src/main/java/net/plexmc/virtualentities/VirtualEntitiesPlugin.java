package net.plexmc.virtualentities;

import net.plexmc.virtualentities.api.VirtualEntityService;
import net.plexmc.virtualentities.internal.VirtualEntityServiceImpl;
import net.plexmc.virtualentities.internal.VirtualEntitiesCommand;
import net.plexmc.virtualentities.internal.VirtualEntitiesListener;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

/** Plugin entry point and Bukkit service registration. */
public final class VirtualEntitiesPlugin extends JavaPlugin {
    private VirtualEntityServiceImpl service;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        service = new VirtualEntityServiceImpl(this);
        getServer().getServicesManager().register(VirtualEntityService.class, service, this, ServicePriority.Normal);
        getServer().getPluginManager().registerEvents(new VirtualEntitiesListener(this, service), this);
        getCommand("virtualentities").setExecutor(new VirtualEntitiesCommand(this, service));
        getCommand("virtualentities").setTabCompleter(new VirtualEntitiesCommand(this, service));
        service.start();
        getLogger().info("VirtualEntities " + getDescription().getVersion() + " enabled; API "
                + VirtualEntityService.API_VERSION);
    }

    @Override
    public void onDisable() {
        if (service != null) {
            service.stop();
            getServer().getServicesManager().unregister(VirtualEntityService.class, service);
        }
    }

    public VirtualEntityServiceImpl service() {
        return service;
    }
}
