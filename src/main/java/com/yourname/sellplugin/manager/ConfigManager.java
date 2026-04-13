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
    /** Cost (in money earned) to unlock the very first multiplier level (1.1x). */
    public double getStartMultiplier() {
        return plugin.getConfig().getDouble("start-multiplier", 1000.0);
    }

    /**
     * Geometric factor: each subsequent milestone costs
     * (previous milestone cost × this value).
     */
    public double getMultiplierFactor() {
        return plugin.getConfig().getDouble("multiplier", 1.6);
    }

    public double getMaxMultiplier() {
        return plugin.getConfig().getDouble("max-multiplier", 3.0);
    }

    // ---- Progress bar colours ---------------------------------------------
    public Material getProgressBarCompletedColor() {
        return resolvePane(plugin.getConfig().getString(
                "progress-bar.completed-color", "LIME_STAINED_GLASS_PANE"),
                Material.LIME_STAINED_GLASS_PANE);
    }

    public Material getProgressBarInProgressColor() {
        return resolvePane(plugin.getConfig().getString(
                "progress-bar.inprogress-color", "YELLOW_STAINED_GLASS_PANE"),
                Material.YELLOW_STAINED_GLASS_PANE);
    }

    public Material getProgressBarLockedColor() {
        return resolvePane(plugin.getConfig().getString(
                "progress-bar.locked-color", "GRAY_STAINED_GLASS_PANE"),
                Material.GRAY_STAINED_GLASS_PANE);
    }

    private Material resolvePane(String name, Material fallback) {
        if (name == null) return fallback;
        Material mat = Material.matchMaterial(name);
        return mat != null ? mat : fallback;
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

    // ---- Icons ----------------------------------------------------------------
    /**
     * Returns the configured Material for an icon key, falling back to
     * {@code fallback} if the key is absent or the material name is invalid.
     *
     * @param key      e.g. "back", "confirm", "prev-page"
     * @param fallback default Material to use
     */
    public Material getIconMaterial(String key, Material fallback) {
        String matName = plugin.getConfig().getString("icons." + key + ".material");
        if (matName == null) return fallback;
        Material mat = Material.matchMaterial(matName);
        return mat != null ? mat : fallback;
    }

    /**
     * Returns the colour-translated display name for an icon key.
     * Falls back to {@code defaultName} (also colour-translated) if absent.
     */
    public String getIconName(String key, String defaultName) {
        String name = plugin.getConfig().getString("icons." + key + ".name");
        return color(name != null ? name : defaultName);
    }

    /**
     * Returns the colour-translated lore lines for an icon key.
     * Falls back to {@code defaultLore} (pre-coloured) if the list is empty.
     */
    public List<String> getIconLore(String key, List<String> defaultLore) {
        List<String> raw = plugin.getConfig().getStringList("icons." + key + ".lore");
        if (raw.isEmpty()) return defaultLore;
        List<String> result = new ArrayList<>();
        for (String line : raw) result.add(color(line));
        return result;
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
