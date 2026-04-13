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
                || holder instanceof SellAllGUI
                || holder instanceof ConfirmSellGUI
                || holder instanceof ConfirmSellAllGUI
                || holder instanceof TopSellGUI) {
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

            // Click in player inventory (bottom) – allow freely
            if (clicked != null && clicked.equals(player.getInventory())) {
                return;
            }

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
                new CategoryProgressGUI(plugin, player, confirmGUI.getCategoryId()).open(player);
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
            double earned = base * mult * amount;
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

