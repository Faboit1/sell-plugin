package com.yourname.sellplugin.command;

import com.yourname.sellplugin.SellPlugin;
import com.yourname.sellplugin.item.SellAxe;
import com.yourname.sellplugin.util.Scheduler;
import com.yourname.sellplugin.util.Text;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * {@code /sellaxe give <player> [duration]} hands out a sell axe. Works from
 * the console too, so crates, shops and store packages can give one.
 */
public class SellAxeCommand implements TabExecutor {

    private static final List<String> DURATIONS = List.of("1d", "3d", "7d", "12h", "permanent");

    private final SellPlugin plugin;
    private final SellAxe sellAxe;

    public SellAxeCommand(SellPlugin plugin, SellAxe sellAxe) {
        this.plugin = plugin;
        this.sellAxe = sellAxe;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("sellplugin.sellaxe.give")) {
            sender.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
            return true;
        }
        if (args.length < 2 || !args[0].equalsIgnoreCase("give")) {
            sender.sendMessage(message("usage", "<red>Usage: /sellaxe give <player> [duration|permanent]"));
            return true;
        }

        Player target = plugin.getServer().getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(message("player-not-found", "<red>Player not found."));
            return true;
        }

        long lifetime = sellAxe.defaultLifetimeMs();
        if (args.length >= 3) {
            lifetime = SellAxe.parseDuration(args[2]);
            if (lifetime < 0) {
                sender.sendMessage(message("bad-duration", "<red>Unknown duration. Try 3d, 12h, 1d12h or permanent."));
                return true;
            }
        }

        ItemStack axe = sellAxe.create(lifetime);
        // The target's inventory belongs to their region, which on Folia is
        // not necessarily the thread this command runs on.
        Scheduler.runEntity(plugin, target, () -> {
            for (ItemStack leftover : target.getInventory().addItem(axe).values()) {
                target.getWorld().dropItemNaturally(target.getLocation(), leftover);
            }
        });

        String time = lifetime > 0 ? SellAxe.remainingLabel(lifetime) : "permanent";
        sender.sendMessage(message("given", "<green>You gave a Sell Axe to <white>{player}</white> <gray>({time})")
                .replace("{player}", target.getName())
                .replace("{time}", time));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("sellplugin.sellaxe.give")) return List.of();
        List<String> options = new ArrayList<>();
        if (args.length == 1) {
            options.add("give");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            for (Player player : plugin.getServer().getOnlinePlayers()) options.add(player.getName());
        } else if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            options.addAll(DURATIONS);
        }
        String typed = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        options.removeIf(option -> !option.toLowerCase(Locale.ROOT).startsWith(typed));
        return options;
    }

    private String message(String key, String def) {
        return Text.legacy(plugin.getConfig().getString("messages.sell-axe." + key, def));
    }
}
