package com.yourname.sellplugin.gui;

import com.yourname.sellplugin.SellPlugin;
import com.yourname.sellplugin.manager.ConfigManager;
import com.yourname.sellplugin.manager.PriceManager;
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
 * Rows 1-4: black-glass background + centered "Sell All" button.
 * Row 5 (slots 36-44): up to 9 category buttons.
 */
public class ShopMainGUI implements InventoryHolder {

    private static final int ROWS = 5;
    private static final int SIZE = ROWS * 9; // 45
    private static final int SELL_ALL_SLOT = 22; // centre of rows 1-4

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

        // --- Background glass ---
        ItemStack bg = makeItem(Material.BLACK_STAINED_GLASS_PANE, " ", Collections.emptyList());
        for (int i = 0; i < 36; i++) inv.setItem(i, bg);

        // --- Sell All button (centre row 3) ---
        Material sellMat = Material.matchMaterial(cfg.getSellAllMaterial());
        if (sellMat == null) sellMat = Material.EMERALD_BLOCK;

        List<String> lore = new ArrayList<>();
        Set<String> categories = plugin.getPriceManager().getCategories();
        for (String raw : cfg.getSellAllLore()) {
            if (raw.contains("{multipliers}")) {
                for (String cat : categories) {
                    double m = plugin.getMultiplierManager().getMultiplier(player, cat);
                    lore.add(ChatColor.translateAlternateColorCodes('&',
                            "&e  \u25b6 &f" + cat + ": &a" + String.format("%.2f", m) + "x"));
                }
            } else {
                lore.add(ChatColor.translateAlternateColorCodes('&', raw));
            }
        }
        inv.setItem(SELL_ALL_SLOT, makeItem(sellMat, cfg.getSellAllName(), lore));

        // --- Category buttons in bottom row (slots 36-44) ---
        List<String> catOrder = cfg.getCategoryOrder();
        ItemStack catBg = makeItem(Material.GRAY_STAINED_GLASS_PANE, " ", Collections.emptyList());
        for (int slot = 36; slot <= 44; slot++) inv.setItem(slot, catBg);

        int catSlot = 36;
        for (int i = 0; i < Math.min(9, catOrder.size()); i++) {
            String catId = catOrder.get(i);
            inv.setItem(catSlot + i, buildCategoryButton(catId));
        }
    }

    private ItemStack buildCategoryButton(String catId) {
        ConfigManager cfg = plugin.getConfigManager();
        PriceManager pm = plugin.getPriceManager();

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

    public boolean isSellAllSlot(int slot) {
        return slot == SELL_ALL_SLOT;
    }
}
