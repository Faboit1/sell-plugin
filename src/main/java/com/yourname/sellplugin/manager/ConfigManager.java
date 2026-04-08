package com.yourname.sellplugin.manager;

import com.yourname.sellplugin.SellPlugin;
import org.bukkit.ChatColor;

import java.util.List;

public class ConfigManager {
    private final SellPlugin plugin;

    public ConfigManager(SellPlugin plugin) {
        this.plugin = plugin;
    }

    public double getMultiplierStep() {
        return plugin.getConfig().getDouble("multiplier-step", 0.001);
    }

    public String getGuiTitle() {
        return color(plugin.getConfig().getString("gui.title", "&8Sell Menu"));
    }

    public int getGuiSize() {
        return plugin.getConfig().getInt("gui.size", 27);
    }

    public int getSellAllSlot() {
        return plugin.getConfig().getInt("gui.sell-all-slot", 13);
    }

    public String getSellAllMaterial() {
        return plugin.getConfig().getString("gui.sell-all-item", "EMERALD_BLOCK");
    }

    public String getSellAllName() {
        return color(plugin.getConfig().getString("gui.sell-all-name", "&aSell All"));
    }

    public List<String> getSellAllLore() {
        return plugin.getConfig().getStringList("gui.sell-all-lore");
    }

    public String getMessage(String path) {
        String prefix = plugin.getConfig().getString("messages.prefix", "");
        String msg = plugin.getConfig().getString("messages." + path, "");
        return color(prefix + msg);
    }

    public String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }
}
