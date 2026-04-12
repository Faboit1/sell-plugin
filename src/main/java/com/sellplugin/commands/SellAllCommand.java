package com.sellplugin.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import com.sellplugin.gui.CategoryGUI;

public class SellAllCommand implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be used by players!");
            return false;
        }
        Player player = (Player) sender;
        CategoryGUI gui = new CategoryGUI(player);
        gui.open();
        return true;
    }
}