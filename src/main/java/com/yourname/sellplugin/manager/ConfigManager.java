package com.yourname.sellplugin.manager;

import com.yourname.sellplugin.SellPlugin;
import org.bukkit.ChatColor;
import org.bukkit.Material;

import java.util.List;

public class ConfigManager {
    private final SellPlugin plugin;

    public ConfigManager(SellPlugin plugin) {
        this.plugin = plugin;
    }

    public double getMultiplierStep() {
        return plugin.getConfig().getDouble("multiplier-step", 0.001);
    }

    public int getProgressBarItemsPerSegment() {
        return plugin.getConfig().getInt("progress-bar-items-per-segment", 100);
    }

    // ---- Category GUI ----

    public String getCategoryGuiTitle() {
        return color(plugin.getConfig().getString("category-gui.title", "&8&lSell Menu"));
    }

    public String getCategoryDisplayName(String category) {
        String path = "category-gui.categories." + category + ".display-name";
        String def = "&f" + capitalize(category);
        return color(plugin.getConfig().getString(path, def));
    }

    public Material getCategoryIcon(String category) {
        String path = "category-gui.categories." + category + ".icon";
        String matName = plugin.getConfig().getString(path, "CHEST");
        Material mat = Material.matchMaterial(matName);
        return mat != null ? mat : Material.CHEST;
    }

    public List<String> getCategoryLore(String category) {
        return plugin.getConfig().getStringList("category-gui.categories." + category + ".lore");
    }

    // ---- Sell-all GUI ----

    public String getSellAllGuiTitle() {
        return color(plugin.getConfig().getString("sellall-gui.title", "&8&lSell All"));
    }

    public int getSellAllGuiSize() {
        return plugin.getConfig().getInt("sellall-gui.size", 27);
    }

    public int getSellAllSlot() {
        return plugin.getConfig().getInt("sellall-gui.sell-all-slot", 13);
    }

    public String getSellAllMaterial() {
        return plugin.getConfig().getString("sellall-gui.sell-all-item", "EMERALD_BLOCK");
    }

    public String getSellAllName() {
        return color(plugin.getConfig().getString("sellall-gui.sell-all-name", "&aSell All"));
    }

    public List<String> getSellAllLore() {
        return plugin.getConfig().getStringList("sellall-gui.sell-all-lore");
    }

    // ---- Legacy GUI (kept for backwards compat) ----

    public String getGuiTitle() {
        return color(plugin.getConfig().getString("gui.title", "&8Sell Menu"));
    }

    public int getGuiSize() {
        return plugin.getConfig().getInt("gui.size", 27);
    }

    // ---- Settings ----

    public boolean isPrefixEnabled() {
        return plugin.getConfig().getBoolean("prefix-enabled", true);
    }

    public boolean isActionBarEnabled() {
        return plugin.getConfig().getBoolean("action-bar-enabled", true);
    }

    public boolean areSoundsEnabled() {
        return plugin.getConfig().getBoolean("sounds-enabled", true);
    }

    public String getSoundType() {
        return plugin.getConfig().getString("sound-type", "ENTITY_PLAYER_LEVELUP");
    }

    public String getEconomyMode() {
        return plugin.getConfig().getString("economy-mode", "VAULT").toUpperCase();
    }

    public String getCoinsEngineCurrencyId() {
        return plugin.getConfig().getString("coinsengine-currency-id", "coins");
    }

    // ---- Messages ----

    public String getMessage(String path) {
        String prefix = isPrefixEnabled() ? plugin.getConfig().getString("messages.prefix", "") : "";
        String msg = plugin.getConfig().getString("messages." + path, "");
        return color(prefix + msg);
    }

    public String getRawPrefix() {
        return color(plugin.getConfig().getString("messages.prefix", ""));
    }

    public String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.substring(0, 1).toUpperCase() + s.substring(1).toLowerCase();
    }
}
