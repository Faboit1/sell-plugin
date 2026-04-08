package com.yourname.sellplugin.gui;

import com.yourname.sellplugin.SellPlugin;
import org.bukkit.Material;
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

    // ANTI-DUPE: We cancel ALL drags if the top inventory is our GUI.
    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof SellGUI) {
            e.setCancelled(true);
        }
    }

    // ANTI-DUPE: We cancel ALL clicks if the GUI is open.
    @EventHandler
    public void onClick(InventoryClickEvent e) {
        // Check if the inventory they are viewing is our GUI
        if (e.getView().getTopInventory().getHolder() instanceof SellGUI) {
            e.setCancelled(true); // Cancels the click entirely so no items can move

            // We only care if they clicked the exact inventory, not their own bottom inventory
            if (e.getClickedInventory() != null && e.getClickedInventory().getHolder() instanceof SellGUI) {
                if (e.getSlot() == plugin.getConfigManager().getSellAllSlot()) {
                    Player p = (Player) e.getWhoClicked();
                    processSellAll(p);
                    p.closeInventory();
                }
            }
        }
    }

    private void processSellAll(Player p) {
        Inventory pInv = p.getInventory();
        double totalEarned = 0.0;
        int totalItemsSold = 0;
        
        // Track how many of each category we sold to batch-save stats at the end
        Map<String, Integer> categorySales = new HashMap<>();

        for (int i = 0; i < pInv.getSize(); i++) {
            ItemStack item = pInv.getItem(i);
            if (item == null || item.getType() == Material.AIR) continue;

            String itemKey = plugin.getPriceManager().getItemKey(item);
            if (itemKey == null) continue;

            double basePrice = plugin.getPriceManager().getPrice(itemKey);
            
            if (basePrice > 0) {
                String category = plugin.getPriceManager().getCategory(itemKey);
                double multiplier = plugin.getMultiplierManager().getMultiplier(p, category);
                
                int amount = item.getAmount();
                double finalPrice = (basePrice * multiplier) * amount;

                totalEarned += finalPrice;
                totalItemsSold += amount;
                
                categorySales.put(category, categorySales.getOrDefault(category, 0) + amount);
                
                // Remove item securely
                pInv.setItem(i, null);
            }
        }

        if (totalEarned > 0) {
            // Apply money
            boolean success = plugin.getEconomyManager().deposit(p, totalEarned);
            if (success) {
                // Update Multipliers only if economy transaction succeeded
                for (Map.Entry<String, Integer> entry : categorySales.entrySet()) {
                    plugin.getMultiplierManager().addSales(p, entry.getKey(), entry.getValue());
                }
                
                String msg = plugin.getConfigManager().getMessage("sold-items")
                        .replace("{amount}", String.valueOf(totalItemsSold))
                        .replace("{price}", String.format("%.2f", totalEarned));
                p.sendMessage(msg);
            } else {
                p.sendMessage(plugin.getConfigManager().getMessage("economy-error"));
            }
        } else {
            p.sendMessage(plugin.getConfigManager().getMessage("nothing-to-sell"));
        }
    }
}
