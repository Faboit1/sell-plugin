package com.yourname.sellplugin.gui;

import com.yourname.sellplugin.SellPlugin;
import com.yourname.sellplugin.manager.SellManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.InventoryHolder;

public class GUIListener implements Listener {

    private final SellPlugin plugin;

    public GUIListener(SellPlugin plugin) {
        this.plugin = plugin;
    }

    // Cancel all drags while any of our GUIs are open
    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        InventoryHolder holder = e.getView().getTopInventory().getHolder();
        if (holder instanceof ShopMainGUI
                || holder instanceof CategoryProgressGUI
                || holder instanceof CategoryItemsGUI
                || holder instanceof SellAllGUI) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player)) return;
        Player player = (Player) e.getWhoClicked();

        InventoryHolder holder = e.getView().getTopInventory().getHolder();

        // ── ShopMainGUI ──────────────────────────────────────────────────────
        if (holder instanceof ShopMainGUI shopGUI) {
            e.setCancelled(true);
            if (e.getClickedInventory() == null
                    || !(e.getClickedInventory().getHolder() instanceof ShopMainGUI)) return;

            int slot = e.getSlot();

            // Sell All button
            if (shopGUI.isSellAllSlot(slot)) {
                player.closeInventory();
                SellManager.SellResult result = plugin.getSellManager().sellAll(player);
                if (!result.success && result.earned == 0 && result.itemsSold == 0) {
                    // nothing-to-sell already sent inside SellManager
                }
                return;
            }

            // Category button (bottom row 36-44)
            String catId = shopGUI.getCategoryAtSlot(slot);
            if (catId != null) {
                new CategoryProgressGUI(plugin, player, catId).open(player);
            }
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

            if (slot == CategoryProgressGUI.SLOT_VIEW_ITEMS) {
                new CategoryItemsGUI(plugin, player, catProgressGUI.getCategoryId(), 0).open(player);
                return;
            }

            if (slot == CategoryProgressGUI.SLOT_SELL_CAT) {
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
}
