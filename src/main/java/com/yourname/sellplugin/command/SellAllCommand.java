package com.yourname.sellplugin.command;

import com.yourname.sellplugin.SellPlugin;
import com.yourname.sellplugin.gui.SellAllGUI;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class SellAllCommand implements CommandExecutor {
    private final SellPlugin plugin;

    public SellAllCommand(SellPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        if (!player.hasPermission("sellplugin.use")) {
            player.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
            return true;
        }

        new SellAllGUI(plugin, player).open(player);
        return true;
    }
}
