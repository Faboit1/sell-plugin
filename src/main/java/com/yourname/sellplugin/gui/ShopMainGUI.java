package com.yourname.sellplugin.gui;

import com.yourname.sellplugin.SellPlugin;
import com.yourname.sellplugin.manager.ConfigManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * Main 9×5 shop GUI.
 * Rows 1-4 (slots 0-35): black-glass background with a centred info panel.
 * Row 5 (slots 36-44):   up to 9 category buttons.
 *
 * Layout of the info panel (rows 2-3, centre):
 *   Slot 13 – separator pane
 *   Slot 22 – player-stats icon showing total inventory sell value
 *   Slot 31 – separator pane
 */
public class ShopMainGUI implements InventoryHolder {

    private static final int ROWS = 5;
    private static final int SIZE = ROWS * 9; // 45

    private final Inventory inv;
    private final SellPlugin plugin;
    private final Player player;

    public ShopMainGUI(SellPlugin plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
        ConfigManager cfg = plugin.getConfigManager();
        this.inv = Bukkit.createInventory(this, SIZE, cfg.getGuiTitle());
        populate();
    }

    private void populate() {
        ConfigManager cfg = plugin.getConfigManager();

        // --- Background glass (rows 1-4) ---
        ItemStack bg = makeItem(Material.BLACK_STAINED_GLASS_PANE, " ", Collections.emptyList());
        for (int i = 0; i < 36; i++) inv.setItem(i, bg);

        // --- Centre info panel (slot 22) ---
        buildInfoPanel();

        // --- Category buttons in bottom row (slots 36-44) ---
        List<String> catOrder = cfg.getCategoryOrder();
        ItemStack catBg = makeItem(Material.GRAY_STAINED_GLASS_PANE, " ", Collections.emptyList());
        for (int slot = 36; slot <= 44; slot++) inv.setItem(slot, catBg);

        for (int i = 0; i < Math.min(9, catOrder.size()); i++) {
            inv.setItem(36 + i, buildCategoryButton(catOrder.get(i)));
        }
    }

    /**
     * Builds a centred "Your Inventory" info icon at slot 22.
     * Shows total sellable value and a per-category breakdown.
     */
    private void buildInfoPanel() {
        double totalValue = 0.0;
        int totalItems = 0;

        List<String> catLines = new ArrayList<>();
        for (String catId : plugin.getConfigManager().getCategoryOrder()) {
            int cnt = plugin.getSellManager().countCategoryItems(player, catId);
            if (cnt > 0) {
                double val = plugin.getSellManager().calculateCategoryValue(player, catId);
                totalValue += val;
                totalItems += cnt;
                catLines.add(ChatColor.GRAY + "  \u25b6 "
                        + plugin.getConfigManager().getCategoryDisplayName(catId)
                        + ChatColor.WHITE + ": " + ChatColor.GOLD + "$" + String.format("%.2f", val));
            }
        }

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.DARK_GRAY + "───────────────────");
        if (totalItems == 0) {
            lore.add(ChatColor.GRAY + "No sellable items in your inventory.");
        } else {
            lore.add(ChatColor.GRAY + "Total sellable items: " + ChatColor.WHITE + totalItems);
            lore.add(ChatColor.GRAY + "Total value: " + ChatColor.GOLD + "$" + String.format("%.2f", totalValue));
            lore.add(ChatColor.DARK_GRAY + "───────────────────");
            lore.addAll(catLines);
        }
        lore.add(ChatColor.DARK_GRAY + "───────────────────");
        lore.add(ChatColor.YELLOW + "Click a category below to sell!");

        inv.setItem(22, makeItem(Material.CHEST, ChatColor.AQUA + "" + ChatColor.BOLD + "Your Inventory", lore));
    }

    private ItemStack buildCategoryButton(String catId) {
        ConfigManager cfg = plugin.getConfigManager();

        int itemCount = plugin.getSellManager().countCategoryItems(player, catId);
        double value = plugin.getSellManager().calculateCategoryValue(player, catId);
        double multiplier = plugin.getMultiplierManager().getMultiplier(player, catId);

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.DARK_GRAY + "───────────────────");
        lore.add(ChatColor.GRAY + "Items in inventory: " + ChatColor.WHITE + itemCount);
        lore.add(ChatColor.GRAY + "Value: " + ChatColor.GOLD + "$" + String.format("%.2f", value));
        lore.add(ChatColor.GRAY + "Multiplier: " + ChatColor.AQUA + String.format("%.2fx", multiplier));
        lore.add(ChatColor.DARK_GRAY + "───────────────────");
        lore.add(ChatColor.YELLOW + "Click to open category!");

        List<String> extraLore = cfg.getCategoryLore(catId);
        if (!extraLore.isEmpty()) lore.addAll(extraLore);

        return makeItem(cfg.getCategoryMaterial(catId), cfg.getCategoryDisplayName(catId), lore);
    }

    private ItemStack makeItem(Material mat, String name, List<String> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    @Override
    public Inventory getInventory() {
        return inv;
    }

    public void open(Player p) {
        p.openInventory(inv);
    }

    /** Returns the category ID for a bottom-row slot (36-44), or null if not a category slot. */
    public String getCategoryAtSlot(int slot) {
        if (slot < 36 || slot > 44) return null;
        List<String> order = plugin.getConfigManager().getCategoryOrder();
        int idx = slot - 36;
        if (idx < order.size()) return order.get(idx);
        return null;
    }
}
