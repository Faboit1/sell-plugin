package com.yourname.sellplugin.manager;

import com.yourname.sellplugin.SellPlugin;
import org.bukkit.ChatColor;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ConfigManager {
    private final SellPlugin plugin;

    public ConfigManager(SellPlugin plugin) {
        this.plugin = plugin;
    }

    // ---- Multiplier -------------------------------------------------------
    public double getMultiplierStep() {
        return plugin.getConfig().getDouble("multiplier-step", 0.001);
    }

    public double getMaxMultiplier() {
        return plugin.getConfig().getDouble("max-multiplier", 5.0);
    }

    // ---- GUI (main shop menu) ---------------------------------------------
    public String getGuiTitle() {
        return color(plugin.getConfig().getString("gui.title", "&8&lShop"));
    }

    public int getGuiSize() {
        return plugin.getConfig().getInt("gui.size", 45);
    }

    // ---- Filler block -----------------------------------------------------
    public Material getFillerBlock() {
        String matName = plugin.getConfig().getString("filler-block", "BLACK_STAINED_GLASS_PANE");
        Material mat = Material.matchMaterial(matName);
        return mat != null ? mat : Material.BLACK_STAINED_GLASS_PANE;
    }

    // ---- SellAll GUI (simple /sellall GUI) --------------------------------
    public String getSellAllGuiTitle() {
        return color(plugin.getConfig().getString("sell-all-gui.title", "&8&lSell All Items"));
    }

    public int getSellAllGuiSize() {
        return plugin.getConfig().getInt("sell-all-gui.size", 27);
    }

    public int getSellAllSlot() {
        return plugin.getConfig().getInt("sell-all-gui.slot", 13);
    }

    public String getSellAllMaterial() {
        return plugin.getConfig().getString("sell-all-gui.item", "EMERALD_BLOCK");
    }

    public String getSellAllName() {
        return color(plugin.getConfig().getString("sell-all-gui.name", "&a&lSell All"));
    }

    public List<String> getSellAllLore() {
        return plugin.getConfig().getStringList("sell-all-gui.lore");
    }

    // ---- Prefix / sounds --------------------------------------------------
    public boolean isPrefixEnabled() {
        return plugin.getConfig().getBoolean("prefix-enabled", false);
    }

    public boolean areSoundsEnabled() {
        return plugin.getConfig().getBoolean("sounds-enabled", true);
    }

    public String getSoundType() {
        return plugin.getConfig().getString("sound-type", "ENTITY_EXPERIENCE_ORB_PICKUP");
    }

    public boolean isTitleNotificationEnabled() {
        return plugin.getConfig().getBoolean("title-notification-enabled", false);
    }

    // ---- Economy ----------------------------------------------------------
    public String getEconomyMode() {
        return plugin.getConfig().getString("economy-mode", "VAULT").toUpperCase();
    }

    public String getCoinsEngineCurrencyId() {
        return plugin.getConfig().getString("coinsengine-currency-id", "coins");
    }

    // ---- Category config --------------------------------------------------
    public List<String> getCategoryOrder() {
        List<String> order = plugin.getConfig().getStringList("category-order");
        if (order == null || order.isEmpty()) {
            List<String> sorted = new ArrayList<>(plugin.getPriceManager().getCategories());
            Collections.sort(sorted);
            return sorted;
        }
        return order;
    }

    public String getCategoryDisplayName(String categoryId) {
        String path = "categories." + categoryId + ".display-name";
        String def = "&f" + capitalize(categoryId);
        return color(plugin.getConfig().getString(path, def));
    }

    public Material getCategoryMaterial(String categoryId) {
        String path = "categories." + categoryId + ".material";
        String matName = plugin.getConfig().getString(path, "CHEST");
        Material mat = Material.matchMaterial(matName);
        return mat != null ? mat : Material.CHEST;
    }

    public List<String> getCategoryLore(String categoryId) {
        List<String> raw = plugin.getConfig().getStringList("categories." + categoryId + ".lore");
        List<String> result = new ArrayList<>();
        for (String line : raw) result.add(color(line));
        return result;
    }

    // ---- Messages ---------------------------------------------------------
    public String getMessage(String path) {
        String prefix = isPrefixEnabled()
                ? color(plugin.getConfig().getString("messages.prefix", ""))
                : "";
        String msg = plugin.getConfig().getString("messages." + path, "");
        return color(prefix + msg);
    }

    // ---- Reload -----------------------------------------------------------
    public void reload() {
        plugin.reloadConfig();
        plugin.getPriceManager().loadPrices();
    }

    // ---- Helpers ----------------------------------------------------------
    public String color(String s) {
        if (s == null) return "";
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase();
    }
}
