package com.yourname.sellplugin.gui;

import com.yourname.sellplugin.SellPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 9x4 (36-slot) progress-bar GUI shown when a player clicks a category.
 *
 * Layout (slot indices):
 *   Row 0:  0  1  2  3 [ICON]  5  6  7  8
 *   Row 1:  9 10 11 12  13 14 15 16 17     ← 9-slot progress bar
 *   Row 2: 18 19 20 21  22 23 24 25 26     ← decorative
 *   Row 3: [BACK]  20 21 22 [SELL_CAT] 24 25 26 [VIEW_ITEMS]
 *            27                 31                    35
 */
public class ProgressBarGUI implements InventoryHolder {

    private static final int GUI_SIZE = 36; // 9x4

    private static final Material FILLED_PANE  = Material.LIME_STAINED_GLASS_PANE;
    private static final Material EMPTY_PANE   = Material.GRAY_STAINED_GLASS_PANE;
    private static final Material BORDER_PANE  = Material.BLACK_STAINED_GLASS_PANE;

    private final Inventory inv;
    private final SellPlugin plugin;
    private final String category;

    public ProgressBarGUI(SellPlugin plugin, Player player, String category) {
        this.plugin = plugin;
        this.category = category;

        String title = plugin.getConfigManager().getCategoryDisplayName(category);
        this.inv = Bukkit.createInventory(this, GUI_SIZE, title);
        buildInventory(player);
    }

    private void buildInventory(Player player) {
        // Row 0 (slots 0-8): border + category icon in slot 4
        ItemStack border = makeBorder();
        for (int i = 0; i < 9; i++) {
            inv.setItem(i, border);
        }
        inv.setItem(4, makeCategoryIcon(player));

        // Row 1 (slots 9-17): progress bar
        buildProgressBar(player);

        // Row 2 (slots 18-26): decorative border
        for (int i = 18; i < 27; i++) {
            inv.setItem(i, border);
        }

        // Row 3 (slots 27-35): controls
        for (int i = 27; i < 36; i++) {
            inv.setItem(i, border);
        }
        inv.setItem(27, makeBackButton());
        inv.setItem(31, makeSellCategoryButton(player));
        inv.setItem(35, makeViewItemsButton());
    }

    /**
     * Fills slots 9–17 with lime (sold) and gray (remaining) panes
     * based on progress towards the next multiplier step.
     */
    private void buildProgressBar(Player player) {
        int itemsSold = plugin.getMultiplierManager().getTotalSold(player, category);
        int perSegment = plugin.getConfigManager().getProgressBarItemsPerSegment();

        // Calculate how many full segments of `perSegment` items have been sold
        // Bar shows progress inside the current "level" (between two whole multipliers)
        int soldInCurrentLevel = itemsSold % (9 * perSegment);
        int filledSegments = soldInCurrentLevel / perSegment;

        for (int i = 0; i < 9; i++) {
            if (i < filledSegments) {
                ItemStack pane = new ItemStack(FILLED_PANE);
                ItemMeta meta = pane.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName(ChatColor.GREEN + "▌ Sold");
                    pane.setItemMeta(meta);
                }
                inv.setItem(9 + i, pane);
            } else {
                ItemStack pane = new ItemStack(EMPTY_PANE);
                ItemMeta meta = pane.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName(ChatColor.GRAY + "▌ Empty");
                    pane.setItemMeta(meta);
                }
                inv.setItem(9 + i, pane);
            }
        }
    }

    private ItemStack makeCategoryIcon(Player player) {
        Material mat = plugin.getConfigManager().getCategoryIcon(category);
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(plugin.getConfigManager().getCategoryDisplayName(category));

            List<String> lore = new ArrayList<>();
            int itemsSold = plugin.getMultiplierManager().getTotalSold(player, category);
            double multi    = plugin.getMultiplierManager().getMultiplier(player, category);
            int perSegment  = plugin.getConfigManager().getProgressBarItemsPerSegment();
            int nextLevel   = (itemsSold / perSegment + 1) * perSegment;

            lore.add(ChatColor.GRAY + "Items sold: " + ChatColor.WHITE + itemsSold);
            lore.add(ChatColor.GRAY + "Multiplier: " + ChatColor.GREEN
                    + String.format("%.2fx", multi));
            lore.add(ChatColor.GRAY + "Next level in: " + ChatColor.YELLOW
                    + (nextLevel - itemsSold) + " items");
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack makeBackButton() {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.RED + "← Back");
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Return to category menu");
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack makeSellCategoryButton(Player player) {
        ItemStack item = new ItemStack(Material.GOLD_INGOT);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD + "Sell " + plugin.getConfigManager()
                    .getCategoryDisplayName(category));
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Sell all items from this");
            lore.add(ChatColor.GRAY + "category in your inventory.");

            // Preview how much they'd earn
            double preview = previewCategoryEarnings(player);
            if (preview > 0) {
                lore.add("");
                lore.add(ChatColor.YELLOW + "Estimated: " + ChatColor.GREEN
                        + String.format("$%.2f", preview));
            }
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack makeViewItemsButton() {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.AQUA + "View Items →");
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Browse all items in this");
            lore.add(ChatColor.GRAY + "category and their prices.");
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack makeBorder() {
        ItemStack pane = new ItemStack(BORDER_PANE);
        ItemMeta meta = pane.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            pane.setItemMeta(meta);
        }
        return pane;
    }

    /**
     * Calculates how much the player would earn by selling all items
     * from this category that are currently in their inventory.
     */
    private double previewCategoryEarnings(Player player) {
        double total = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null) continue;
            String key = plugin.getPriceManager().getItemKey(item);
            if (key == null) continue;
            if (!category.equals(plugin.getPriceManager().getCategory(key))) continue;
            double price = plugin.getPriceManager().getPrice(key);
            if (price <= 0) continue;
            double multi = plugin.getMultiplierManager().getMultiplier(player, category);
            total += price * multi * item.getAmount();
        }
        return total;
    }

    public String getCategory() {
        return category;
    }

    @Override
    public Inventory getInventory() {
        return inv;
    }

    public void open(Player player) {
        player.openInventory(inv);
    }
}
