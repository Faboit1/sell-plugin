package com.yourname.sellplugin.command;

import com.yourname.sellplugin.SellPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class FastSellAllCommand implements CommandExecutor {

    private final SellPlugin plugin;

    public FastSellAllCommand(SellPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getConfigManager().getText("player-only-command", "&cOnly players can use this command."));
            return true;
        }

        if (!player.hasPermission("sellplugin.use")) {
            player.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
            return true;
        }

        plugin.getSellManager().sellAll(player);
        return true;
    }
}
