package com.yourname.sellplugin.manager;

import com.yourname.sellplugin.SellPlugin;
import com.yourname.sellplugin.util.Text;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ConfigManager {
    private final SellPlugin plugin;

    public ConfigManager(SellPlugin plugin) {
        this.plugin = plugin;
    }

    // ---- Feature toggles -----------------------------------------------------
    /**
     * Generic feature switch under the {@code features.<key>} section. Every
     * major piece of the plugin can be turned off here without touching code.
     */
    public boolean isFeatureEnabled(String key, boolean def) {
        return plugin.getConfig().getBoolean("features." + key, def);
    }

    public boolean isMultipliersEnabled()      { return isFeatureEnabled("multipliers", true); }
    public boolean isEnchantmentPricingEnabled(){ return isFeatureEnabled("enchantment-pricing", true); }
    public boolean isShulkerSellingEnabled()   { return isFeatureEnabled("shulker-selling", true); }
    public boolean isProgressGuiEnabled()      { return isFeatureEnabled("progress-gui", true); }
    public boolean isTopSellEnabled()          { return isFeatureEnabled("top-sell", true); }
    public boolean isActionBarEnabled()        { return isFeatureEnabled("action-bar", true); }

    // ---- Enchantment pricing -------------------------------------------------
    /** Base value added per enchantment level for enchantments not explicitly listed. */
    public double getEnchantDefaultValuePerLevel() {
        return plugin.getConfig().getDouble("enchantments.default-value-per-level", 50.0);
    }

    /** Factor the price is multiplied by for each distinct enchantment on the item. */
    public double getEnchantMultiplierPerEnchantment() {
        return plugin.getConfig().getDouble("enchantments.multiplier-per-enchantment", 1.1);
    }

    /**
     * Value added per level for a specific enchantment key (e.g. "sharpness"),
     * falling back to {@link #getEnchantDefaultValuePerLevel()} when unlisted.
     */
    public double getEnchantValue(String enchantKey) {
        return plugin.getConfig().getDouble("enchantments.values." + enchantKey,
                getEnchantDefaultValuePerLevel());
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

    // ---- Filler block -----------------------------------------------------
    public Material getFillerBlock() {
        String matName = plugin.getConfig().getString("filler-block", "BLACK_STAINED_GLASS_PANE");
        Material mat = Material.matchMaterial(matName);
        return mat != null ? mat : Material.BLACK_STAINED_GLASS_PANE;
    }

    public boolean isWorthEnabled() {
        return plugin.getConfig().getBoolean("worth.enabled", true);
    }

    /**
     * Whether to inject the worth line for players in creative mode. Creative
     * clients echo whatever lore they are shown back to the server, which bakes
     * the line into the real item and causes duplicates — so this defaults to
     * false. Only turn it on if you understand that trade-off.
     */
    public boolean isWorthShownInCreative() {
        return plugin.getConfig().getBoolean("worth.show-in-creative", false);
    }

    public String getWorthFormat() {
        return color(plugin.getConfig().getString("worth.format", "&7Worth &a&l${worth}"));
    }

    /**
     * The worth line used when part of the stack would go into an open FoOrders
     * order instead of the shop. Separate from {@link #getWorthFormat()} so the
     * value can be marked as an estimate - somebody else may fill the order
     * first - which is what the default "~" is for.
     */
    public String getOrderWorthFormat() {
        return color(plugin.getConfig().getString("worth.order-format", "&7Worth &a&l~${worth}"));
    }

    // ---- FoOrders integration --------------------------------------------

    /** Whether selling routes items into open FoOrders orders before the shop. */
    public boolean isOrderFillingEnabled() {
        return plugin.getConfig().getBoolean("orders.enabled", true);
    }

    /**
     * When true (the default) an order only takes items if it pays at least the
     * shop price for them, so filling an order never costs the seller money.
     */
    public boolean isOrderFillingOnlyWhenBetter() {
        return plugin.getConfig().getBoolean("orders.only-when-better", true);
    }

    /**
     * Whether a player's own orders may be filled by their own selling. Off by
     * default, matching FoOrders, where the deliver menu refuses your own orders.
     */
    public boolean isOwnOrderFillingAllowed() {
        return plugin.getConfig().getBoolean("orders.include-own-orders", false);
    }

    /** Whether money earned from orders counts toward category multiplier progress. */
    public boolean doOrderEarningsCountTowardMultipliers() {
        return plugin.getConfig().getBoolean("orders.count-toward-multipliers", true);
    }

    /**
     * Marker put in front of a money figure that includes order payouts, to show
     * it is an estimate. Blank to show no marker at all.
     */
    public String getOrderEstimateMarker() {
        return plugin.getConfig().getString("orders.estimate-marker", "~");
    }

    /** Config schema version, used by the auto-migrator. 0 = pre-versioning. */
    public int getConfigVersion() {
        return plugin.getConfig().getInt("config-version", 0);
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
        return color(getRawCategoryDisplayName(categoryId));
    }

    /** The category's display name as written, for substituting into another line. */
    public String getRawCategoryDisplayName(String categoryId) {
        String path = "categories." + categoryId + ".display-name";
        String def = "<white>" + capitalize(categoryId);
        return plugin.getConfig().getString(path, def);
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

    /**
     * Returns an arbitrary configurable text under {@code messages.<path>},
     * colour-translated, falling back to {@code def} if absent.
     * Used for all customisable GUI/command text that isn't a chat "message".
     */
    public String getText(String path, String def) {
        return color(getRawText(path, def));
    }

    /**
     * The configured string as written, before MiniMessage is parsed. Callers
     * that substitute placeholders need this, so a value carrying its own
     * formatting is parsed along with the line it lands in.
     */
    public String getRawText(String path, String def) {
        return plugin.getConfig().getString("messages." + path, def);
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
        // The fallback is written in MiniMessage like everything else, so it
        // has to be rendered too rather than handed back as written.
        return Text.legacyLines(raw.isEmpty() ? defaultLore : raw);
    }

    // ---- Reload -----------------------------------------------------------
    public void reload() {
        plugin.reloadConfig();
        plugin.getPriceManager().loadPrices();
    }

    // ---- Helpers ----------------------------------------------------------
    /**
     * Renders a configured string. The plugin adds no colour, boldness or
     * letter-casing of its own any more - whatever a line looks like is what
     * the config says, written in MiniMessage (legacy {@code &} codes still work).
     */
    public String color(String s) {
        return Text.legacy(s);
    }

    /** The raw, unrendered lines of a configured list, for callers filling in placeholders. */
    public List<String> getRawTextList(String path, List<String> def) {
        List<String> raw = plugin.getConfig().getStringList("messages." + path);
        return raw.isEmpty() ? (def == null ? List.of() : def) : raw;
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase();
    }
}
