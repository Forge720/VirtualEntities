package net.plexmc.virtualentities.internal;

import net.plexmc.virtualentities.api.SpawnResult;
import net.plexmc.virtualentities.api.VirtualEntitySpec;
import net.plexmc.virtualentities.api.SourceType;
import net.plexmc.virtualentities.VirtualEntitiesPlugin;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Small permission-protected diagnostics and test command. */
public final class VirtualEntitiesCommand implements CommandExecutor, TabCompleter {
    private final VirtualEntitiesPlugin plugin;
    private final VirtualEntityServiceImpl service;

    public VirtualEntitiesCommand(VirtualEntitiesPlugin plugin, VirtualEntityServiceImpl service) {
        this.plugin = plugin;
        this.service = service;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("virtualentities.admin")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission.");
            return true;
        }
        String action = args.length == 0 ? "status" : args[0].toLowerCase(java.util.Locale.ROOT);
        switch (action) {
            case "status" -> {
                var status = service.adapterStatus();
                sender.sendMessage(ChatColor.AQUA + "VirtualEntities active=" + service.activeCount()
                        + " vanilla=" + status.vanilla() + " modelengine=" + status.modelEngine()
                        + " mythicmobs=" + status.mythicMobs());
            }
            case "reload" -> {
                service.reloadConfiguration();
                service.reconcileAll();
                sender.sendMessage(ChatColor.GREEN + "Configuration reloaded.");
            }
            case "test" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(ChatColor.RED + "The test command requires a player sender.");
                    return true;
                }
                String type = args.length > 1 ? args[1] : "armor_stand";
                VirtualEntitySpec spec = VirtualEntitySpec.builder(SourceType.VANILLA, type, player.getLocation(), plugin)
                        .audience(net.plexmc.virtualentities.api.AudiencePolicy.player(player))
                        .expiresAfter(Duration.ofSeconds(10))
                        .build();
                SpawnResult result = service.spawn(spec);
                sender.sendMessage(result.success() ? ChatColor.GREEN + "Spawned " + result.handle().getId()
                        : ChatColor.RED + result.failureReason().name() + ": " + result.message());
            }
            case "remove" -> {
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /" + label + " remove <uuid>");
                    return true;
                }
                try {
                    sender.sendMessage(service.remove(UUID.fromString(args[1])).success()
                            ? ChatColor.GREEN + "Removed."
                            : ChatColor.RED + "Removal failed.");
                } catch (IllegalArgumentException exception) {
                    sender.sendMessage(ChatColor.RED + "Invalid UUID.");
                }
            }
            default -> sender.sendMessage(ChatColor.YELLOW + "Usage: /" + label + " <status|reload|test|remove>");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("status", "reload", "test", "remove").stream()
                    .filter(value -> value.startsWith(args[0].toLowerCase(java.util.Locale.ROOT))).toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("test")) {
            return List.of("armor_stand", "zombie", "skeleton", "creeper", "spider", "villager");
        }
        return List.of();
    }
}
