package com.yourname.sellplugin.command;

import com.yourname.sellplugin.SellPlugin;
import com.yourname.sellplugin.gui.SellGUI;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /sellall command – opens the quick sell-all GUI (single button, configurable block).
 */
public class SellAllCommand implements CommandExecutor {
    private final SellPlugin plugin;

    public SellAllCommand(SellPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        Player player = (Player) sender;

        if (!player.hasPermission("sellplugin.sellall")) {
            player.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
            return true;
        }

        SellGUI gui = new SellGUI(plugin, player);
        gui.open(player);
        return true;
    }
}
