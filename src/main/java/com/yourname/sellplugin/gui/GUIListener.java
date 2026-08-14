package com.yourname.sellplugin.gui;

import com.yourname.sellplugin.SellPlugin;
import com.yourname.sellplugin.manager.SellManager;
import com.yourname.sellplugin.util.Scheduler;
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

        // ShopMainGUI: allow drags in the item-placement area (0-44),
        // cancel if any slot touches the protected bottom row (45-53).
        if (holder instanceof ShopMainGUI shopGUI) {
            for (int slot : e.getRawSlots()) {
                if (slot >= ShopMainGUI.BOTTOM_ROW_START && slot <= 53) {
                    e.setCancelled(true);
                    return;
                }
            }
            // Allow the drag, then refresh the sell button so the value updates.
            Player dragger = (e.getWhoClicked() instanceof Player p) ? p : null;
            if (dragger != null) {
                Scheduler.runEntityLater(plugin, dragger, shopGUI::refreshSellButton, 1L);
            }
            return; // allow the drag
        }

        // All other plugin GUIs: cancel drags entirely.
        if (holder instanceof CategoryProgressGUI
                || holder instanceof CategoryItemsGUI
                || holder instanceof SellAllGUI
                || holder instanceof ConfirmSellGUI
                || holder instanceof ConfirmSellAllGUI
                || holder instanceof TopSellGUI
                || holder instanceof SellMultiGUI
                || holder instanceof WorthGUI) {
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
            Inventory clicked = e.getClickedInventory();

            // Click in player inventory (bottom) – allow freely, but a
            // shift-click / number-key / move-to-other-inventory action can push
            // an item up into the shop area without the top inventory being the
            // clicked one. Schedule a sell-button refresh so the value updates.
            if (clicked != null && clicked.equals(player.getInventory())) {
                Scheduler.runEntityLater(plugin, player, shopGUI::refreshSellButton, 1L);
                return;
            }

            if (clicked != null && clicked.getHolder() instanceof ShopMainGUI) {
                int slot = e.getSlot();

                // Bottom row (45-53): protected – handle sell button
                if (slot >= ShopMainGUI.BOTTOM_ROW_START) {
                    e.setCancelled(true);
                    if (slot == ShopMainGUI.SLOT_SELL_BUTTON) {
                        // Sell all items in the GUI
                        sellGuiItems(player, shopGUI);
                    }
                    return;
                }

                // Slots 0-44: allow item placement / removal
                // After any click, schedule a sell button refresh (on the
                // player's own region thread for Folia compatibility).
                Scheduler.runEntityLater(plugin, player, shopGUI::refreshSellButton, 1L);
                return;
            }

            e.setCancelled(true);
            return;
        }

        // ── SellMultiGUI ─────────────────────────────────────────────────────
        if (holder instanceof SellMultiGUI multiGUI) {
            e.setCancelled(true);
            if (e.getClickedInventory() == null
                    || !(e.getClickedInventory().getHolder() instanceof SellMultiGUI)) return;

            int slot = e.getSlot();
            String catId = multiGUI.getCategoryAtSlot(slot);
            if (catId != null) {
                new CategoryProgressGUI(plugin, player, catId).open(player);
            }
            return;
        }

        // ── WorthGUI ─────────────────────────────────────────────────────────
        if (holder instanceof WorthGUI worthGUI) {
            e.setCancelled(true);
            if (e.getClickedInventory() == null
                    || !(e.getClickedInventory().getHolder() instanceof WorthGUI)) return;

            int slot = e.getSlot();

            if (slot == WorthGUI.SLOT_CLOSE) {
                player.closeInventory();
                return;
            }

            if (slot == WorthGUI.SLOT_PREV && worthGUI.hasPrevPage()) {
                worthGUI.prevPage().open(player);
                return;
            }

            if (slot == WorthGUI.SLOT_NEXT && worthGUI.hasNextPage()) {
                worthGUI.nextPage().open(player);
                return;
            }

            if (slot == WorthGUI.SLOT_FILTER) {
                String nextFilter = worthGUI.getNextFilter();
                new WorthGUI(plugin, player, nextFilter, 0).open(player);
                return;
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
                new SellMultiGUI(plugin, player).open(player);
                return;
            }

            // Click the first path node → open items list for this category
            if (catProgressGUI.isSellSlot(slot)) {
                new CategoryItemsGUI(plugin, player, catProgressGUI.getCategoryId(), 0).open(player);
                return;
            }
            return;
        }

        // ── ConfirmSellGUI ───────────────────────────────────────────────────
        if (holder instanceof ConfirmSellGUI confirmGUI) {
            e.setCancelled(true);
            if (e.getClickedInventory() == null
                    || !(e.getClickedInventory().getHolder() instanceof ConfirmSellGUI)) return;

            int slot = e.getSlot();

            if (slot == ConfirmSellGUI.SLOT_CONFIRM) {
                player.closeInventory();
                plugin.getSellManager().sellCategory(player, confirmGUI.getCategoryId());
                return;
            }

            if (slot == ConfirmSellGUI.SLOT_CANCEL) {
                new CategoryItemsGUI(plugin, player, confirmGUI.getCategoryId(), confirmGUI.getReturnPage()).open(player);
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
                new SellMultiGUI(plugin, player).open(player);
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
                new ConfirmSellGUI(plugin, player, catItemsGUI.getCategoryId(), catItemsGUI.getPage()).open(player);
                return;
            }

            // Item click (slots 0-44) – sell all of that item type
            String itemKey = catItemsGUI.getItemKeyAtSlot(slot);
            if (itemKey != null) {
                plugin.getSellManager().sellItemType(player, itemKey);
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
                new ConfirmSellAllGUI(plugin, player).open(player);
            }
        }

        // ── ConfirmSellAllGUI ─────────────────────────────────────────────────
        if (holder instanceof ConfirmSellAllGUI) {
            e.setCancelled(true);
            if (e.getClickedInventory() == null
                    || !(e.getClickedInventory().getHolder() instanceof ConfirmSellAllGUI)) return;

            int slot = e.getSlot();

            if (slot == ConfirmSellAllGUI.SLOT_CONFIRM) {
                player.closeInventory();
                plugin.getSellManager().sellAll(player);
                return;
            }

            if (slot == ConfirmSellAllGUI.SLOT_CANCEL) {
                player.closeInventory();
                new SellAllGUI(plugin, player).open(player);
            }
        }

        // ── TopSellGUI ────────────────────────────────────────────────────────
        if (holder instanceof TopSellGUI topSellGUI) {
            e.setCancelled(true);
            if (e.getClickedInventory() == null
                    || !(e.getClickedInventory().getHolder() instanceof TopSellGUI)) return;

            int slot = e.getSlot();

            if (slot == TopSellGUI.SLOT_CLOSE) {
                player.closeInventory();
                return;
            }

            if (slot == TopSellGUI.SLOT_PREV && topSellGUI.hasPrevPage()) {
                topSellGUI.prevPage().open(player);
                return;
            }

            if (slot == TopSellGUI.SLOT_NEXT && topSellGUI.hasNextPage()) {
                topSellGUI.nextPage().open(player);
            }
        }
    }

    // ── Sell items placed in the ShopMainGUI (via button click) ─────────────

    private void sellGuiItems(Player player, ShopMainGUI shopGUI) {
        Inventory top = shopGUI.getInventory();
        SellPlugin pl = shopGUI.getPlugin();

        double totalEarned = 0.0;
        int totalItems = 0;
        Map<String, Double> categoryEarnings = new HashMap<>();
        List<ItemStack> sellableItems = new ArrayList<>();
        List<ItemStack> nonSellableItems = new ArrayList<>();

        for (int i = 0; i < ShopMainGUI.ITEM_AREA_END; i++) {
            ItemStack item = top.getItem(i);
            if (item == null || item.getType() == Material.AIR) continue;

            // Shulker box: sell its contents, return the shulker
            if (SellManager.isShulkerBox(item)) {
                SellManager.ShulkerSellData data = pl.getSellManager().sellShulkerContents(player, item, null, null);
                totalEarned += data.earned;
                totalItems += data.items;
                data.categoryEarnings.forEach((cat, val) -> categoryEarnings.merge(cat, val, Double::sum));
                nonSellableItems.add(item); // return shulker box
                top.setItem(i, null);
                continue;
            }

            String key = pl.getPriceManager().getItemKey(item);
            if (key == null || pl.getPriceManager().getPrice(key) <= 0) {
                nonSellableItems.add(item);
                top.setItem(i, null);
                continue;
            }

            double base = pl.getPriceManager().getPrice(key);
            String cat = pl.getPriceManager().getCategory(key);
            double mult = pl.getMultiplierManager().getEffectiveMultiplier(player, cat);
            int amount = item.getAmount();
            double earned = pl.getSellManager().enchantedUnitPrice(item, base) * mult * amount;
            totalEarned += earned;
            totalItems += amount;
            categoryEarnings.merge(cat, earned, Double::sum);
            sellableItems.add(item);
            top.setItem(i, null);
        }

        for (ItemStack item : nonSellableItems) {
            returnItem(player, item);
        }

        if (totalEarned > 0) {
            boolean ok = pl.getEconomyManager().deposit(player, totalEarned);
            if (ok) {
                for (Map.Entry<String, Double> entry : categoryEarnings.entrySet()) {
                    pl.getMultiplierManager().addEarnings(player, entry.getKey(), entry.getValue());
                }
                pl.getSellManager().sendSellNotification(player, totalEarned, totalItems);
            } else {
                player.sendMessage(pl.getConfigManager().getMessage("economy-error"));
                for (ItemStack item : sellableItems) {
                    returnItem(player, item);
                }
            }
        }

        // Refresh the sell button after selling
        shopGUI.refreshSellButton();
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
        Map<String, Double> categoryEarnings = new HashMap<>();
        List<ItemStack> sellableItems = new ArrayList<>();
        List<ItemStack> nonSellableItems = new ArrayList<>();

        for (int i = 0; i < ShopMainGUI.ITEM_AREA_END; i++) {
            ItemStack item = top.getItem(i);
            if (item == null || item.getType() == Material.AIR) continue;

            // Shulker box: sell its contents, return the (now empty/partially-empty) shulker
            if (SellManager.isShulkerBox(item)) {
                SellManager.ShulkerSellData data = pl.getSellManager().sellShulkerContents(player, item, null, null);
                totalEarned += data.earned;
                totalItems += data.items;
                data.categoryEarnings.forEach((cat, val) -> categoryEarnings.merge(cat, val, Double::sum));
                // Return the shulker box (now with sold items removed) to the player
                returnItem(player, item);
                continue;
            }

            String key = pl.getPriceManager().getItemKey(item);
            if (key == null || pl.getPriceManager().getPrice(key) <= 0) {
                nonSellableItems.add(item);
                continue;
            }

            double base = pl.getPriceManager().getPrice(key);
            String cat = pl.getPriceManager().getCategory(key);
            double mult = pl.getMultiplierManager().getEffectiveMultiplier(player, cat);
            int amount = item.getAmount();
            double earned = pl.getSellManager().enchantedUnitPrice(item, base) * mult * amount;
            totalEarned += earned;
            totalItems += amount;
            categoryEarnings.merge(cat, earned, Double::sum);
            sellableItems.add(item);
        }

        for (ItemStack item : nonSellableItems) {
            returnItem(player, item);
        }

        if (totalEarned > 0) {
            boolean ok = pl.getEconomyManager().deposit(player, totalEarned);
            if (ok) {
                for (Map.Entry<String, Double> entry : categoryEarnings.entrySet()) {
                    pl.getMultiplierManager().addEarnings(player, entry.getKey(), entry.getValue());
                }
                pl.getSellManager().sendSellNotification(player, totalEarned, totalItems);
            } else {
                player.sendMessage(pl.getConfigManager().getMessage("economy-error"));
                for (ItemStack item : sellableItems) {
                    returnItem(player, item);
                }
            }
        }
    }

    private void returnItem(Player player, ItemStack item) {
        HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(item);
        for (ItemStack drop : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), drop);
        }
    }
}
