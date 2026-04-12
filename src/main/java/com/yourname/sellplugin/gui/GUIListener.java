package com.yourname.sellplugin.gui;

import com.yourname.sellplugin.SellPlugin;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

public class GUIListener implements Listener {
    private final SellPlugin plugin;

    public GUIListener(SellPlugin plugin) {
        this.plugin = plugin;
    }

    // ANTI-DUPE: Cancel all drags on our GUIs
    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        Object holder = e.getView().getTopInventory().getHolder();
        if (holder instanceof SellGUI
                || holder instanceof CategoryGUI
                || holder instanceof ProgressBarGUI
                || holder instanceof CategoryItemsGUI) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player)) return;
        Player player = (Player) e.getWhoClicked();

        Object holder = e.getView().getTopInventory().getHolder();

        if (holder instanceof CategoryGUI) {
            e.setCancelled(true);
            if (e.getClickedInventory() == null
                    || !(e.getClickedInventory().getHolder() instanceof CategoryGUI)) return;
            // Bottom row slots 36–44 are category buttons
            int slot = e.getSlot();
            if (slot >= 36 && slot <= 44) {
                int index = slot - 36;
                String category = getCategoryByIndex(index);
                if (category != null) {
                    ProgressBarGUI pbGui = new ProgressBarGUI(plugin, player, category);
                    pbGui.open(player);
                }
            }

        } else if (holder instanceof ProgressBarGUI) {
            e.setCancelled(true);
            if (e.getClickedInventory() == null
                    || !(e.getClickedInventory().getHolder() instanceof ProgressBarGUI)) return;
            ProgressBarGUI pbGui = (ProgressBarGUI) holder;
            String category = pbGui.getCategory();
            int slot = e.getSlot();

            if (slot == 27) {
                // Back → open CategoryGUI
                CategoryGUI catGui = new CategoryGUI(plugin, player);
                catGui.open(player);
            } else if (slot == 31) {
                // Sell all items in this category
                double[] result = processSellCategory(player, category);
                if (result[0] > 0) {
                    notifySell(player, result[0], (int) result[1]);
                } else {
                    player.sendMessage(plugin.getConfigManager().getMessage("nothing-to-sell"));
                }
                player.closeInventory();
            } else if (slot == 35) {
                // View items → open CategoryItemsGUI page 0
                CategoryItemsGUI ciGui = new CategoryItemsGUI(plugin, player, category, 0);
                ciGui.open(player);
            }

        } else if (holder instanceof CategoryItemsGUI) {
            e.setCancelled(true);
            if (e.getClickedInventory() == null
                    || !(e.getClickedInventory().getHolder() instanceof CategoryItemsGUI)) return;
            CategoryItemsGUI ciGui = (CategoryItemsGUI) holder;
            String category = ciGui.getCategory();
            int slot = e.getSlot();

            if (slot == 45) {
                // Previous page
                int prevPage = ciGui.getPage() - 1;
                if (prevPage >= 0) {
                    CategoryItemsGUI newGui = new CategoryItemsGUI(plugin, player, category, prevPage);
                    newGui.open(player);
                }
            } else if (slot == 48) {
                // Back → open ProgressBarGUI
                ProgressBarGUI pbGui = new ProgressBarGUI(plugin, player, category);
                pbGui.open(player);
            } else if (slot == 53) {
                // Next page
                int nextPage = ciGui.getPage() + 1;
                if (nextPage < ciGui.getTotalPages()) {
                    CategoryItemsGUI newGui = new CategoryItemsGUI(plugin, player, category, nextPage);
                    newGui.open(player);
                }
            }

        } else if (holder instanceof SellGUI) {
            e.setCancelled(true);
            if (e.getClickedInventory() == null
                    || !(e.getClickedInventory().getHolder() instanceof SellGUI)) return;
            if (e.getSlot() == plugin.getConfigManager().getSellAllSlot()) {
                double[] result = processSellAll(player);
                if (result[0] > 0) {
                    notifySell(player, result[0], (int) result[1]);
                } else {
                    player.sendMessage(plugin.getConfigManager().getMessage("nothing-to-sell"));
                }
                player.closeInventory();
            }
        }
    }

    // ---- Sell helpers ----

    /**
     * Sells all items from a specific category in the player's inventory.
     * Returns double[] { totalEarned, totalItemsSold }.
     */
    private double[] processSellCategory(Player player, String targetCategory) {
        Inventory pInv = player.getInventory();
        double totalEarned = 0;
        int totalItems = 0;
        Map<String, Integer> categorySales = new HashMap<>();

        for (int i = 0; i < pInv.getSize(); i++) {
            ItemStack item = pInv.getItem(i);
            if (item == null || item.getType() == Material.AIR) continue;
            String key = plugin.getPriceManager().getItemKey(item);
            if (key == null) continue;
            String cat = plugin.getPriceManager().getCategory(key);
            if (!targetCategory.equals(cat)) continue;
            double basePrice = plugin.getPriceManager().getPrice(key);
            if (basePrice <= 0) continue;
            double multi = plugin.getMultiplierManager().getMultiplier(player, cat);
            int amount = item.getAmount();
            totalEarned += basePrice * multi * amount;
            totalItems  += amount;
            categorySales.put(cat, categorySales.getOrDefault(cat, 0) + amount);
            pInv.setItem(i, null);
        }

        if (totalEarned > 0) {
            boolean ok = plugin.getEconomyManager().deposit(player, totalEarned);
            if (ok) {
                for (Map.Entry<String, Integer> e : categorySales.entrySet()) {
                    plugin.getMultiplierManager().addSales(player, e.getKey(), e.getValue());
                }
                return new double[]{ totalEarned, totalItems };
            } else {
                player.sendMessage(plugin.getConfigManager().getMessage("economy-error"));
                return new double[]{ 0, 0 };
            }
        }
        return new double[]{ 0, 0 };
    }

    /**
     * Sells all sellable items in the player's inventory (across all categories).
     * Returns double[] { totalEarned, totalItemsSold }.
     */
    private double[] processSellAll(Player player) {
        Inventory pInv = player.getInventory();
        double totalEarned = 0;
        int totalItems = 0;
        Map<String, Integer> categorySales = new HashMap<>();

        for (int i = 0; i < pInv.getSize(); i++) {
            ItemStack item = pInv.getItem(i);
            if (item == null || item.getType() == Material.AIR) continue;
            String key = plugin.getPriceManager().getItemKey(item);
            if (key == null) continue;
            double basePrice = plugin.getPriceManager().getPrice(key);
            if (basePrice <= 0) continue;
            String cat = plugin.getPriceManager().getCategory(key);
            double multi = plugin.getMultiplierManager().getMultiplier(player, cat);
            int amount = item.getAmount();
            totalEarned += basePrice * multi * amount;
            totalItems  += amount;
            categorySales.put(cat, categorySales.getOrDefault(cat, 0) + amount);
            pInv.setItem(i, null);
        }

        if (totalEarned > 0) {
            boolean ok = plugin.getEconomyManager().deposit(player, totalEarned);
            if (ok) {
                for (Map.Entry<String, Integer> e : categorySales.entrySet()) {
                    plugin.getMultiplierManager().addSales(player, e.getKey(), e.getValue());
                }
                return new double[]{ totalEarned, totalItems };
            } else {
                player.sendMessage(plugin.getConfigManager().getMessage("economy-error"));
                return new double[]{ 0, 0 };
            }
        }
        return new double[]{ 0, 0 };
    }

    /**
     * Notifies the player of earnings via action bar and optionally chat.
     */
    private void notifySell(Player player, double amount, int itemCount) {
        // Action bar: +$X.XX
        if (plugin.getConfigManager().isActionBarEnabled()) {
            player.sendActionBar(ChatColor.GREEN + "+" + ChatColor.GOLD
                    + "$" + String.format("%.2f", amount));
        }

        // Sound
        if (plugin.getConfigManager().areSoundsEnabled()) {
            try {
                Sound sound = Sound.valueOf(plugin.getConfigManager().getSoundType());
                player.playSound(player.getLocation(), sound, 1.0f, 1.2f);
            } catch (IllegalArgumentException ex) {
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
            }
        }

        // Chat message (only if prefix-enabled)
        if (plugin.getConfigManager().isPrefixEnabled()) {
            player.sendMessage(plugin.getConfigManager().getMessage("sold-items")
                    .replace("{amount}", String.valueOf(itemCount))
                    .replace("{price}", String.format("%.2f", amount)));
        }
    }

    /**
     * Returns the category name for a given bottom-row index (0–8).
     */
    private String getCategoryByIndex(int index) {
        String[] ordered = {
            "ores", "blocks", "crops", "enchantedbooks",
            "fish", "mobdrops", "naturalitems", "armortools", "potions"
        };
        if (index < 0 || index >= ordered.length) return null;
        // Only return if the category actually exists
        if (plugin.getPriceManager().getCategories().contains(ordered[index])) {
            return ordered[index];
        }
        // Fallback: pick by position from the actual category set
        java.util.List<String> cats = new java.util.ArrayList<>(plugin.getPriceManager().getCategories());
        java.util.Collections.sort(cats);
        if (index < cats.size()) return cats.get(index);
        return null;
    }
}
