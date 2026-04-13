package com.yourname.sellplugin.gui;

import com.yourname.sellplugin.SellPlugin;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GUIListener implements Listener {

    private final SellPlugin plugin;

    public GUIListener(SellPlugin plugin) {
        this.plugin = plugin;
    }

    // ── Drag handling ────────────────────────────────────────────────────────

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        InventoryHolder holder = e.getView().getTopInventory().getHolder();

        // ShopMainGUI: allow drags in the item-placement area (0-35),
        // cancel if any slot touches the protected bottom row (36-44).
        if (holder instanceof ShopMainGUI) {
            for (int slot : e.getRawSlots()) {
                if (slot >= ShopMainGUI.BOTTOM_ROW_START && slot <= 44) {
                    e.setCancelled(true);
                    return;
                }
            }
            return; // allow the drag
        }

        // All other plugin GUIs: cancel drags entirely.
        if (holder instanceof CategoryProgressGUI
                || holder instanceof CategoryItemsGUI
                || holder instanceof SellAllGUI) {
            e.setCancelled(true);
        }
    }

    // ── Click handling ───────────────────────────────────────────────────────

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player)) return;
        Player player = (Player) e.getWhoClicked();

        InventoryHolder holder = e.getView().getTopInventory().getHolder();

        // ── ShopMainGUI ──────────────────────────────────────────────────────
        if (holder instanceof ShopMainGUI shopGUI) {
            // Determine which inventory was clicked
            Inventory clicked = e.getClickedInventory();

            // Click in player inventory (bottom) – allow freely (including shift-click)
            if (clicked != null && clicked.equals(player.getInventory())) {
                return; // allow
            }

            // Click in the shop GUI (top inventory)
            if (clicked != null && clicked.getHolder() instanceof ShopMainGUI) {
                int slot = e.getSlot();

                // Bottom row (36-44): protected – handle category clicks
                if (slot >= ShopMainGUI.BOTTOM_ROW_START) {
                    e.setCancelled(true);
                    String catId = shopGUI.getCategoryAtSlot(slot);
                    if (catId != null) {
                        new CategoryProgressGUI(plugin, player, catId).open(player);
                    }
                    return;
                }

                // Slots 0-35: allow item placement / removal
                return;
            }

            // Safety: cancel anything else
            e.setCancelled(true);
            return;
        }

        // ── CategoryProgressGUI ──────────────────────────────────────────────
        if (holder instanceof CategoryProgressGUI catProgressGUI) {
            e.setCancelled(true);
            if (e.getClickedInventory() == null
                    || !(e.getClickedInventory().getHolder() instanceof CategoryProgressGUI)) return;

            int slot = e.getSlot();

            if (slot == CategoryProgressGUI.SLOT_BACK) {
                new ShopMainGUI(plugin, player).open(player);
                return;
            }

            // Click the category item at the start of the path → sell category
            if (catProgressGUI.isSellSlot(slot)) {
                player.closeInventory();
                plugin.getSellManager().sellCategory(player, catProgressGUI.getCategoryId());
                return;
            }
            return;
        }

        // ── CategoryItemsGUI ─────────────────────────────────────────────────
        if (holder instanceof CategoryItemsGUI catItemsGUI) {
            e.setCancelled(true);
            if (e.getClickedInventory() == null
                    || !(e.getClickedInventory().getHolder() instanceof CategoryItemsGUI)) return;

            int slot = e.getSlot();

            if (slot == CategoryItemsGUI.SLOT_BACK) {
                new CategoryProgressGUI(plugin, player, catItemsGUI.getCategoryId()).open(player);
                return;
            }

            if (slot == CategoryItemsGUI.SLOT_PREV && catItemsGUI.hasPrevPage()) {
                catItemsGUI.prevPage().open(player);
                return;
            }

            if (slot == CategoryItemsGUI.SLOT_NEXT && catItemsGUI.hasNextPage()) {
                catItemsGUI.nextPage().open(player);
                return;
            }

            if (slot == CategoryItemsGUI.SLOT_SELL_ALL) {
                player.closeInventory();
                plugin.getSellManager().sellCategory(player, catItemsGUI.getCategoryId());
                return;
            }

            // Item click (slots 0-44) – sell all of that item type
            String itemKey = catItemsGUI.getItemKeyAtSlot(slot);
            if (itemKey != null) {
                plugin.getSellManager().sellItemType(player, itemKey);
                // Refresh the GUI to show updated counts
                new CategoryItemsGUI(plugin, player, catItemsGUI.getCategoryId(),
                        catItemsGUI.getPage()).open(player);
            }
            return;
        }

        // ── SellAllGUI ───────────────────────────────────────────────────────
        if (holder instanceof SellAllGUI sellAllGUI) {
            e.setCancelled(true);
            if (e.getClickedInventory() == null
                    || !(e.getClickedInventory().getHolder() instanceof SellAllGUI)) return;

            if (e.getSlot() == sellAllGUI.getSellAllSlot()) {
                player.closeInventory();
                plugin.getSellManager().sellAll(player);
            }
        }
    }

    // ── Close handling – sell items placed in ShopMainGUI ────────────────────

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player player)) return;

        InventoryHolder holder = e.getView().getTopInventory().getHolder();
        if (!(holder instanceof ShopMainGUI shopGUI)) return;

        Inventory top = e.getView().getTopInventory();
        SellPlugin pl = shopGUI.getPlugin();

        double totalEarned = 0.0;
        int totalItems = 0;
        Map<String, Integer> categorySales = new HashMap<>();
        List<ItemStack> sellableItems = new ArrayList<>();
        List<ItemStack> nonSellableItems = new ArrayList<>();

        // Classify items in slots 0-35 (the item-placement area)
        for (int i = 0; i < ShopMainGUI.BOTTOM_ROW_START; i++) {
            ItemStack item = top.getItem(i);
            if (item == null || item.getType() == Material.AIR) continue;

            String key = pl.getPriceManager().getItemKey(item);
            if (key == null || pl.getPriceManager().getPrice(key) <= 0) {
                nonSellableItems.add(item);
                continue;
            }

            double base = pl.getPriceManager().getPrice(key);
            String cat = pl.getPriceManager().getCategory(key);
            double mult = pl.getMultiplierManager().getMultiplier(player, cat);
            int amount = item.getAmount();
            totalEarned += base * mult * amount;
            totalItems += amount;
            categorySales.merge(cat, amount, Integer::sum);
            sellableItems.add(item);
        }

        // Always return non-sellable items
        for (ItemStack item : nonSellableItems) {
            returnItem(player, item);
        }

        // Process sellable items
        if (totalEarned > 0) {
            boolean ok = pl.getEconomyManager().deposit(player, totalEarned);
            if (ok) {
                for (Map.Entry<String, Integer> entry : categorySales.entrySet()) {
                    pl.getMultiplierManager().addSales(player, entry.getKey(), entry.getValue());
                }
                pl.getSellManager().sendSellNotification(player, totalEarned, totalItems);
            } else {
                // Economy error – return sellable items too
                player.sendMessage(pl.getConfigManager().getMessage("economy-error"));
                for (ItemStack item : sellableItems) {
                    returnItem(player, item);
                }
            }
        }
    }

    /**
     * Returns an item to the player's inventory; drops it at their feet if
     * the inventory is full.
     */
    private void returnItem(Player player, ItemStack item) {
        HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(item);
        for (ItemStack drop : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), drop);
        }
    }
}
