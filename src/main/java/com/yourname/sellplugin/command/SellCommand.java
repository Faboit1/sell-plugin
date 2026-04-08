package com.yourname.sellplugin.command;

import com.yourname.sellplugin.SellPlugin;
import com.yourname.sellplugin.gui.SellGUI;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class SellCommand implements CommandExecutor {
    private final SellPlugin plugin;

    public SellCommand(SellPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        Player player = (Player) sender;

        if (!player.hasPermission("sellplugin.use")) {
            player.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
            return true;
        }

        SellGUI gui = new SellGUI(plugin, player);
        gui.open(player);

        return true;
    }
}
