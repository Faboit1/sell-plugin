package com.yourname.sellplugin.command;

import com.yourname.sellplugin.SellPlugin;
import com.yourname.sellplugin.util.Scheduler;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * {@code /showworth [true|false]} — lets a player show or hide the worth
 * tooltip for themselves. With no argument it toggles the current state.
 */
public class ShowWorthCommand implements CommandExecutor, TabCompleter {

    private final SellPlugin plugin;

    public ShowWorthCommand(SellPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getConfigManager().getText(
                    "player-only-command", "&cOnly players can use this command."));
            return true;
        }

        if (!player.hasPermission("sellplugin.use")) {
            player.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
            return true;
        }

        boolean nowVisible;
        if (args.length == 0) {
            nowVisible = plugin.getWorthVisibilityManager().toggle(player.getUniqueId());
        } else {
            Boolean parsed = parseBoolean(args[0]);
            if (parsed == null) {
                player.sendMessage(plugin.getConfigManager().getText(
                        "showworth-usage", "&cUsage: /showworth [true|false]"));
                return true;
            }
            nowVisible = parsed;
            plugin.getWorthVisibilityManager().setVisible(player.getUniqueId(), nowVisible);
        }

        String key = nowVisible ? "showworth-enabled" : "showworth-disabled";
        String def = nowVisible
                ? "&aItem worth is now &lshown&r&a in your inventory."
                : "&eItem worth is now &lhidden&r&e in your inventory.";
        player.sendMessage(plugin.getConfigManager().getText(key, def));

        // Resend the inventory so the change is reflected immediately. Runs on
        // the player's own region thread for Folia compatibility.
        Scheduler.runEntityLater(plugin, player, () -> {
            if (player.isOnline()) {
                player.updateInventory();
            }
        }, 1L);
        return true;
    }

    private Boolean parseBoolean(String arg) {
        String a = arg.toLowerCase();
        return switch (a) {
            case "true", "on", "show", "yes", "enable", "enabled" -> Boolean.TRUE;
            case "false", "off", "hide", "no", "disable", "disabled" -> Boolean.FALSE;
            default -> null;
        };
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Stream.of("true", "false")
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return List.of();
    }
}
