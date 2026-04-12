package com.sellplugin.managers;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import net.milkbowl.vault.economy.Economy;

public class SellManager {
    private Economy economy;
    private JavaPlugin plugin;
    private boolean soundsEnabled;
    private boolean prefixEnabled;

    public SellManager(JavaPlugin plugin, Economy economy, boolean soundsEnabled, boolean prefixEnabled) {
        this.plugin = plugin;
        this.economy = economy;
        this.soundsEnabled = soundsEnabled;
        this.prefixEnabled = prefixEnabled;
    }

    public void sellItems(Player player, double amount) {
        if (amount <= 0) return;
        economy.depositPlayer(player, amount);
        String actionBar = ChatColor.GREEN + "+" + ChatColor.GOLD + "$" + String.format("%.2f", amount);
        player.sendActionBar(actionBar);
        if (soundsEnabled) {
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
        }
        if (prefixEnabled) {
            player.sendMessage(ChatColor.GOLD + "[SELL] " + ChatColor.GREEN + "You earned $" + String.format("%.2f", amount));
        }
    }

    public void setSoundsEnabled(boolean enabled) {
        this.soundsEnabled = enabled;
    }

    public void setPrefixEnabled(boolean enabled) {
        this.prefixEnabled = enabled;
    }

    public boolean areSoundsEnabled() {
        return soundsEnabled;
    }

    public boolean isPrefixEnabled() {
        return prefixEnabled;
    }
}
