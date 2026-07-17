package com.yourname.sellplugin.manager;

import com.yourname.sellplugin.SellPlugin;
import com.yourname.sellplugin.util.NumberFormatter;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class SellManager {

    private static final String FALLBACK_SOUND = "ENTITY_EXPERIENCE_ORB_PICKUP";

    private final SellPlugin plugin;

    public SellManager(SellPlugin plugin) {
        this.plugin = plugin;
    }

    // ---------------------------------------------------------------
    // Sell entire player inventory
    // ---------------------------------------------------------------
    public SellResult sellAll(Player player) {
        double totalEarned = 0.0;
        int totalItems = 0;
        Map<String, Double> categoryEarnings = new HashMap<>();

        int storageSize = player.getInventory().getStorageContents().length;
        for (int i = 0; i < storageSize; i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (item == null || item.getType() == Material.AIR) continue;

            if (isShulkerBox(item)) {
                ShulkerSellData data = sellShulkerContents(player, item, null, null);
                totalEarned += data.earned;
                totalItems += data.items;
                data.categoryEarnings.forEach((cat, val) -> categoryEarnings.merge(cat, val, Double::sum));
                continue;
            }

            String key = plugin.getPriceManager().getItemKey(item);
            if (key == null) continue;

            double base = plugin.getPriceManager().getPrice(key);
            if (base <= 0) continue;

            String cat = plugin.getPriceManager().getCategory(key);
            double mult = plugin.getMultiplierManager().getEffectiveMultiplier(player, cat);
            int amount = item.getAmount();
            double earned = base * mult * amount;
            totalEarned += earned;
            totalItems += amount;
            categoryEarnings.merge(cat, earned, Double::sum);
            player.getInventory().setItem(i, null);
        }

        return finalizeSell(player, totalEarned, totalItems, categoryEarnings);
    }

    // ---------------------------------------------------------------
    // Sell only items in a specific category
    // ---------------------------------------------------------------
    public SellResult sellCategory(Player player, String category) {
        double totalEarned = 0.0;
        int totalItems = 0;
        Map<String, Double> categoryEarnings = new HashMap<>();

        int storageSize = player.getInventory().getStorageContents().length;
        for (int i = 0; i < storageSize; i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (item == null || item.getType() == Material.AIR) continue;

            if (isShulkerBox(item)) {
                ShulkerSellData data = sellShulkerContents(player, item, category, null);
                totalEarned += data.earned;
                totalItems += data.items;
                data.categoryEarnings.forEach((cat, val) -> categoryEarnings.merge(cat, val, Double::sum));
                continue;
            }

            String key = plugin.getPriceManager().getItemKey(item);
            if (key == null) continue;

            String cat = plugin.getPriceManager().getCategory(key);
            if (!category.equalsIgnoreCase(cat)) continue;

            double base = plugin.getPriceManager().getPrice(key);
            if (base <= 0) continue;

            double mult = plugin.getMultiplierManager().getEffectiveMultiplier(player, cat);
            int amount = item.getAmount();
            double earned = base * mult * amount;
            totalEarned += earned;
            totalItems += amount;
            categoryEarnings.merge(cat, earned, Double::sum);
            player.getInventory().setItem(i, null);
        }

        return finalizeSell(player, totalEarned, totalItems, categoryEarnings);
    }

    // ---------------------------------------------------------------
    // Sell all stacks of a specific item type/key
    // ---------------------------------------------------------------
    public SellResult sellItemType(Player player, String itemKey) {
        double totalEarned = 0.0;
        int totalItems = 0;
        Map<String, Double> categoryEarnings = new HashMap<>();

        double base = plugin.getPriceManager().getPrice(itemKey);
        if (base <= 0) return new SellResult(0, 0, false);

        String cat = plugin.getPriceManager().getCategory(itemKey);
        double mult = plugin.getMultiplierManager().getEffectiveMultiplier(player, cat);

        int storageSize = player.getInventory().getStorageContents().length;
        for (int i = 0; i < storageSize; i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (item == null || item.getType() == Material.AIR) continue;

            if (isShulkerBox(item)) {
                ShulkerSellData data = sellShulkerContents(player, item, null, itemKey);
                totalEarned += data.earned;
                totalItems += data.items;
                data.categoryEarnings.forEach((c, val) -> categoryEarnings.merge(c, val, Double::sum));
                continue;
            }

            String key = plugin.getPriceManager().getItemKey(item);
            if (!itemKey.equalsIgnoreCase(key)) continue;

            int amount = item.getAmount();
            double earned = base * mult * amount;
            totalEarned += earned;
            totalItems += amount;
            categoryEarnings.merge(cat, earned, Double::sum);
            player.getInventory().setItem(i, null);
        }

        return finalizeSell(player, totalEarned, totalItems, categoryEarnings);
    }

    // ---------------------------------------------------------------
    // Preview result (item count + value) for selling all items
    // ---------------------------------------------------------------
    public SellPreview previewSellAll(Player player) {
        int itemCount = 0;
        double value = 0.0;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item == null || item.getType() == Material.AIR) continue;

            if (isShulkerBox(item)) {
                ShulkerSellData data = peekShulkerContents(player, item, null, null);
                itemCount += data.items;
                value += data.earned;
                continue;
            }

            String key = plugin.getPriceManager().getItemKey(item);
            if (key == null) continue;
            double base = plugin.getPriceManager().getPrice(key);
            if (base <= 0) continue;
            String cat = plugin.getPriceManager().getCategory(key);
            double mult = plugin.getMultiplierManager().getEffectiveMultiplier(player, cat);
            itemCount += item.getAmount();
            value += base * mult * item.getAmount();
        }
        return new SellPreview(itemCount, value);
    }

    // ---------------------------------------------------------------
    // Count how many sellable items of a category the player has
    // ---------------------------------------------------------------
    public int countCategoryItems(Player player, String category) {
        int total = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item == null || item.getType() == Material.AIR) continue;

            if (isShulkerBox(item)) {
                total += peekShulkerContents(player, item, category, null).items;
                continue;
            }

            String key = plugin.getPriceManager().getItemKey(item);
            if (key == null) continue;
            String cat = plugin.getPriceManager().getCategory(key);
            if (category.equalsIgnoreCase(cat)) total += item.getAmount();
        }
        return total;
    }

    // ---------------------------------------------------------------
    // Calculate value of sellable items in a category (with multiplier)
    // ---------------------------------------------------------------
    public double calculateCategoryValue(Player player, String category) {
        double total = 0.0;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item == null || item.getType() == Material.AIR) continue;

            if (isShulkerBox(item)) {
                total += peekShulkerContents(player, item, category, null).earned;
                continue;
            }

            String key = plugin.getPriceManager().getItemKey(item);
            if (key == null) continue;
            String cat = plugin.getPriceManager().getCategory(key);
            if (!category.equalsIgnoreCase(cat)) continue;
            double base = plugin.getPriceManager().getPrice(key);
            if (base <= 0) continue;
            double mult = plugin.getMultiplierManager().getEffectiveMultiplier(player, cat);
            total += base * mult * item.getAmount();
        }
        return total;
    }

    public double calculateItemWorth(Player player, ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return 0.0;

        if (isShulkerBox(item)) {
            return peekShulkerContents(player, item, null, null).earned;
        }

        String key = plugin.getPriceManager().getItemKey(item);
        if (key == null) return 0.0;

        double base = plugin.getPriceManager().getPrice(key);
        if (base <= 0) return 0.0;

        String category = plugin.getPriceManager().getCategory(key);
        double multiplier = plugin.getMultiplierManager().getEffectiveMultiplier(player, category);
        return base * multiplier * item.getAmount();
    }

    // ---------------------------------------------------------------
    // Finalize a sell operation
    // ---------------------------------------------------------------
    private SellResult finalizeSell(Player player, double totalEarned, int totalItems,
                                     Map<String, Double> categoryEarnings) {
        if (totalEarned <= 0) {
            player.sendMessage(plugin.getConfigManager().getMessage("nothing-to-sell"));
            return new SellResult(0, 0, false);
        }

        boolean ok = plugin.getEconomyManager().deposit(player, totalEarned);
        if (!ok) {
            player.sendMessage(plugin.getConfigManager().getMessage("economy-error"));
            return new SellResult(0, 0, false);
        }

        for (Map.Entry<String, Double> e : categoryEarnings.entrySet()) {
            plugin.getMultiplierManager().addEarnings(player, e.getKey(), e.getValue());
        }

        sendSellNotification(player, totalEarned, totalItems);
        return new SellResult(totalEarned, totalItems, true);
    }

    // ---------------------------------------------------------------
    // Notification: action bar only (+$amount in lime/green color)
    // Title and chat are optional via config
    // ---------------------------------------------------------------
    public void sendSellNotification(Player player, double amount, int itemCount) {
        String formatted = NumberFormatter.format(amount);

        // Action bar: always shown – "+$amount"
        String actionBarText = plugin.getConfigManager().getText("action-bar", "&a+${amount}")
                .replace("{amount}", formatted);
        player.sendActionBar(actionBarText);

        // Title notification: only if enabled in config
        if (plugin.getConfigManager().isTitleNotificationEnabled()) {
            String titleText = plugin.getConfigManager().getText("sell-title", "&a+${amount}")
                    .replace("{amount}", formatted);
            String subtitleText = plugin.getConfigManager().getText("sell-subtitle", "&7You sold {count} item(s)")
                    .replace("{count}", NumberFormatter.format(itemCount));
            player.sendTitle(titleText, subtitleText, 10, 40, 20);
        }

        // Play sound if enabled
        String soundName = plugin.getConfigManager().getSoundType();
        if (plugin.getConfigManager().areSoundsEnabled() && soundName != null) {
            try {
                Sound sound = Sound.valueOf(soundName);
                player.playSound(player.getLocation(), sound, 1.0f, 1.2f);
            } catch (IllegalArgumentException ignored) {
                player.playSound(player.getLocation(), Sound.valueOf(FALLBACK_SOUND), 1.0f, 1.2f);
            }
        }

        // Chat message: only if prefix enabled
        if (plugin.getConfigManager().isPrefixEnabled()) {
            String msg = plugin.getConfigManager().getMessage("sold-items")
                    .replace("{amount}", NumberFormatter.format(itemCount))
                    .replace("{price}", formatted);
            player.sendMessage(msg);
        }
    }

    // ---------------------------------------------------------------
    // Shulker box helpers
    // ---------------------------------------------------------------

    /** Returns true if the item is any colour of shulker box. */
    public static boolean isShulkerBox(ItemStack item) {
        return item != null && item.getType().name().endsWith("_SHULKER_BOX");
    }

    /**
     * Sell sellable items inside a shulker box, modifying its inventory in place.
     * Filters by category and/or itemKey when non-null.
     * The shulker box item itself is never consumed.
     */
    public ShulkerSellData sellShulkerContents(Player player, ItemStack shulkerItem,
                                                String categoryFilter, String itemKeyFilter) {
        if (!(shulkerItem.getItemMeta() instanceof BlockStateMeta bsm)) return ShulkerSellData.EMPTY;
        if (!(bsm.getBlockState() instanceof ShulkerBox shulker)) return ShulkerSellData.EMPTY;

        double totalEarned = 0.0;
        int totalItems = 0;
        Map<String, Double> categoryEarnings = new HashMap<>();

        ItemStack[] contents = shulker.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack inner = contents[i];
            if (inner == null || inner.getType() == Material.AIR) continue;

            String key = plugin.getPriceManager().getItemKey(inner);
            if (key == null) continue;

            String cat = plugin.getPriceManager().getCategory(key);
            if (categoryFilter != null && !categoryFilter.equalsIgnoreCase(cat)) continue;
            if (itemKeyFilter != null && !itemKeyFilter.equalsIgnoreCase(key)) continue;

            double base = plugin.getPriceManager().getPrice(key);
            if (base <= 0) continue;

            double mult = plugin.getMultiplierManager().getEffectiveMultiplier(player, cat);
            int amount = inner.getAmount();
            double earned = base * mult * amount;
            totalEarned += earned;
            totalItems += amount;
            categoryEarnings.merge(cat, earned, Double::sum);
            shulker.getInventory().setItem(i, null);
        }

        if (totalItems > 0) {
            bsm.setBlockState(shulker);
            shulkerItem.setItemMeta(bsm);
        }

        return new ShulkerSellData(totalEarned, totalItems, categoryEarnings);
    }

    /**
     * Peek at sellable items inside a shulker box without modifying it.
     * Filters by category and/or itemKey when non-null.
     */
    private ShulkerSellData peekShulkerContents(Player player, ItemStack shulkerItem,
                                                 String categoryFilter, String itemKeyFilter) {
        if (!(shulkerItem.getItemMeta() instanceof BlockStateMeta bsm)) return ShulkerSellData.EMPTY;
        if (!(bsm.getBlockState() instanceof ShulkerBox shulker)) return ShulkerSellData.EMPTY;

        double totalEarned = 0.0;
        int totalItems = 0;
        Map<String, Double> categoryEarnings = new HashMap<>();

        for (ItemStack inner : shulker.getInventory().getContents()) {
            if (inner == null || inner.getType() == Material.AIR) continue;

            String key = plugin.getPriceManager().getItemKey(inner);
            if (key == null) continue;

            String cat = plugin.getPriceManager().getCategory(key);
            if (categoryFilter != null && !categoryFilter.equalsIgnoreCase(cat)) continue;
            if (itemKeyFilter != null && !itemKeyFilter.equalsIgnoreCase(key)) continue;

            double base = plugin.getPriceManager().getPrice(key);
            if (base <= 0) continue;

            double mult = plugin.getMultiplierManager().getEffectiveMultiplier(player, cat);
            int amount = inner.getAmount();
            totalEarned += base * mult * amount;
            totalItems += amount;
            categoryEarnings.merge(cat, base * mult * amount, Double::sum);
        }

        return new ShulkerSellData(totalEarned, totalItems, categoryEarnings);
    }

    // ---------------------------------------------------------------
    // Shulker sell data container
    // ---------------------------------------------------------------
    public static class ShulkerSellData {
        public static final ShulkerSellData EMPTY = new ShulkerSellData(0, 0, Collections.emptyMap());

        public final double earned;
        public final int items;
        public final Map<String, Double> categoryEarnings;

        public ShulkerSellData(double earned, int items, Map<String, Double> categoryEarnings) {
            this.earned = earned;
            this.items = items;
            this.categoryEarnings = categoryEarnings;
        }
    }

    // ---------------------------------------------------------------
    // Simple inner result class
    // ---------------------------------------------------------------
    public static class SellResult {
        public final double earned;
        public final int itemsSold;
        public final boolean success;

        public SellResult(double earned, int itemsSold, boolean success) {
            this.earned = earned;
            this.itemsSold = itemsSold;
            this.success = success;
        }
    }

    // ---------------------------------------------------------------
    // Preview result for sell-all (no inventory modification)
    // ---------------------------------------------------------------
    public static class SellPreview {
        public final int itemCount;
        public final double value;

        public SellPreview(int itemCount, double value) {
            this.itemCount = itemCount;
            this.value = value;
        }
    }
}
