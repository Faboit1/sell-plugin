package com.yourname.sellplugin.manager;

import com.yourname.sellplugin.SellPlugin;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

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
        Map<String, Integer> categorySales = new HashMap<>();

        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (item == null || item.getType() == Material.AIR) continue;

            String key = plugin.getPriceManager().getItemKey(item);
            if (key == null) continue;

            double base = plugin.getPriceManager().getPrice(key);
            if (base <= 0) continue;

            String cat = plugin.getPriceManager().getCategory(key);
            double mult = plugin.getMultiplierManager().getMultiplier(player, cat);
            int amount = item.getAmount();
            totalEarned += base * mult * amount;
            totalItems += amount;
            categorySales.merge(cat, amount, Integer::sum);
            player.getInventory().setItem(i, null);
        }

        return finalizeSell(player, totalEarned, totalItems, categorySales);
    }

    // ---------------------------------------------------------------
    // Sell only items in a specific category
    // ---------------------------------------------------------------
    public SellResult sellCategory(Player player, String category) {
        double totalEarned = 0.0;
        int totalItems = 0;
        Map<String, Integer> categorySales = new HashMap<>();

        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (item == null || item.getType() == Material.AIR) continue;

            String key = plugin.getPriceManager().getItemKey(item);
            if (key == null) continue;

            String cat = plugin.getPriceManager().getCategory(key);
            if (!category.equalsIgnoreCase(cat)) continue;

            double base = plugin.getPriceManager().getPrice(key);
            if (base <= 0) continue;

            double mult = plugin.getMultiplierManager().getMultiplier(player, cat);
            int amount = item.getAmount();
            totalEarned += base * mult * amount;
            totalItems += amount;
            categorySales.merge(cat, amount, Integer::sum);
            player.getInventory().setItem(i, null);
        }

        return finalizeSell(player, totalEarned, totalItems, categorySales);
    }

    // ---------------------------------------------------------------
    // Sell all stacks of a specific item type/key
    // ---------------------------------------------------------------
    public SellResult sellItemType(Player player, String itemKey) {
        double totalEarned = 0.0;
        int totalItems = 0;
        Map<String, Integer> categorySales = new HashMap<>();

        double base = plugin.getPriceManager().getPrice(itemKey);
        if (base <= 0) return new SellResult(0, 0, false);

        String cat = plugin.getPriceManager().getCategory(itemKey);
        double mult = plugin.getMultiplierManager().getMultiplier(player, cat);

        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (item == null || item.getType() == Material.AIR) continue;

            String key = plugin.getPriceManager().getItemKey(item);
            if (!itemKey.equalsIgnoreCase(key)) continue;

            int amount = item.getAmount();
            totalEarned += base * mult * amount;
            totalItems += amount;
            categorySales.merge(cat, amount, Integer::sum);
            player.getInventory().setItem(i, null);
        }

        return finalizeSell(player, totalEarned, totalItems, categorySales);
    }

    // ---------------------------------------------------------------
    // Count how many sellable items of a category the player has
    // ---------------------------------------------------------------
    public int countCategoryItems(Player player, String category) {
        int total = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null || item.getType() == Material.AIR) continue;
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
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null || item.getType() == Material.AIR) continue;
            String key = plugin.getPriceManager().getItemKey(item);
            if (key == null) continue;
            String cat = plugin.getPriceManager().getCategory(key);
            if (!category.equalsIgnoreCase(cat)) continue;
            double base = plugin.getPriceManager().getPrice(key);
            if (base <= 0) continue;
            double mult = plugin.getMultiplierManager().getMultiplier(player, cat);
            total += base * mult * item.getAmount();
        }
        return total;
    }

    // ---------------------------------------------------------------
    // Finalize a sell operation
    // ---------------------------------------------------------------
    private SellResult finalizeSell(Player player, double totalEarned, int totalItems,
                                     Map<String, Integer> categorySales) {
        if (totalEarned <= 0) {
            player.sendMessage(plugin.getConfigManager().getMessage("nothing-to-sell"));
            return new SellResult(0, 0, false);
        }

        boolean ok = plugin.getEconomyManager().deposit(player, totalEarned);
        if (!ok) {
            player.sendMessage(plugin.getConfigManager().getMessage("economy-error"));
            return new SellResult(0, 0, false);
        }

        for (Map.Entry<String, Integer> e : categorySales.entrySet()) {
            plugin.getMultiplierManager().addSales(player, e.getKey(), e.getValue());
        }

        sendSellNotification(player, totalEarned, totalItems);
        return new SellResult(totalEarned, totalItems, true);
    }

    // ---------------------------------------------------------------
    // Notification: action bar (always) + chat (if prefix-enabled)
    // ---------------------------------------------------------------
    public void sendSellNotification(Player player, double amount, int itemCount) {
        String formatted = String.format("%.2f", amount);

        // Action bar: always shown
        String actionBarText = ChatColor.GREEN + "+" + ChatColor.GOLD + "$" + formatted
                + ChatColor.GRAY + " (" + itemCount + " items)";
        player.sendActionBar(actionBarText);

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
                    .replace("{amount}", String.valueOf(itemCount))
                    .replace("{price}", formatted);
            player.sendMessage(msg);
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
}
