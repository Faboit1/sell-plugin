package com.yourname.sellplugin.gui;

import com.yourname.sellplugin.SellPlugin;
import com.yourname.sellplugin.manager.ConfigManager;
import com.yourname.sellplugin.manager.MultiplierManager;
import com.yourname.sellplugin.util.SmallCaps;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Category progress GUI – full double-chest (54 slots).
 *
 * A "Snake / U-Path" of multiplier milestones winds through the menu.
 * Each milestone goes from 1.0x to 3.0x in 0.1 increments (21 nodes).
 *
 * Colour key:
 *   GREEN  – completed milestone
 *   YELLOW – current / in-progress milestone
 *   GRAY   – locked / future milestone
 *
 * The very first node uses the category material (same icon as the
 * main menu) and is clickable to sell the category.
 *
 * All text uses small-capital Unicode letters.
 */
public class CategoryProgressGUI implements InventoryHolder {

    // ── Constants ────────────────────────────────────────────────────────────

    private static final int SIZE = 54;

    /** Back button slot (bottom-right area). */
    public static final int SLOT_BACK = 53;

    /** Sell button = first path node (the category icon). */
    public static final int SLOT_SELL_CAT = -1; // resolved dynamically via PATH

    /**
     * The 21-node snake path through the 54-slot grid.
     *
     * Row 0 (0-8):   border / category info at slot 4
     * Row 1 (9-17):  → path nodes 0-6  (slots 10-16)
     * Row 2 (18-26): ↓ path node 7     (slot 25)
     * Row 3 (27-35): ← path nodes 8-14 (slots 34 down to 28)
     * Row 4 (36-44): ↓ path node 15    (slot 37)
     * Row 5 (45-53): → path nodes 16-20 (slots 46-50)
     */
    private static final int[] PATH = {
            10, 11, 12, 13, 14, 15, 16,   // row 1 left→right
            25,                             // row 2 turn-down
            34, 33, 32, 31, 30, 29, 28,   // row 3 right→left
            37,                             // row 4 turn-down
            46, 47, 48, 49, 50             // row 5 left→right
    };

    /** Multiplier value for each path node: 1.0, 1.1, 1.2 … 3.0. */
    private static final double[] MILESTONES = new double[PATH.length];
    static {
        for (int i = 0; i < MILESTONES.length; i++) {
            MILESTONES[i] = 1.0 + i * 0.1;
        }
    }

    // ── Instance fields ──────────────────────────────────────────────────────

    private final Inventory inv;
    private final SellPlugin plugin;
    private final Player player;
    private final String categoryId;

    public CategoryProgressGUI(SellPlugin plugin, Player player, String categoryId) {
        this.plugin = plugin;
        this.player = player;
        this.categoryId = categoryId;

        ConfigManager cfg = plugin.getConfigManager();
        String title = cfg.getCategoryDisplayName(categoryId);
        this.inv = Bukkit.createInventory(this, SIZE, title);
        populate();
    }

    // ── Layout ───────────────────────────────────────────────────────────────

    private void populate() {
        ConfigManager cfg = plugin.getConfigManager();

        // Fill everything with black glass background
        ItemStack bg = makeItem(Material.BLACK_STAINED_GLASS_PANE, " ", Collections.emptyList());
        for (int i = 0; i < SIZE; i++) inv.setItem(i, bg);

        // ── Category info icon at slot 4 (top-centre) ───────────────────────
        double mult = plugin.getMultiplierManager().getMultiplier(player, categoryId);
        int itemCount = plugin.getSellManager().countCategoryItems(player, categoryId);
        double value = plugin.getSellManager().calculateCategoryValue(player, categoryId);
        int sold = getTotalSold();

        List<String> iconLore = new ArrayList<>();
        iconLore.add(ChatColor.DARK_GRAY + "───────────────────");
        iconLore.add(ChatColor.GRAY + SmallCaps.convert("items in inventory: ")
                + ChatColor.WHITE + itemCount);
        iconLore.add(ChatColor.GRAY + SmallCaps.convert("sell value: ")
                + ChatColor.GOLD + "$" + String.format("%.2f", value));
        iconLore.add(ChatColor.GRAY + SmallCaps.convert("your multiplier: ")
                + ChatColor.AQUA + String.format("%.2fx", mult));
        iconLore.add(ChatColor.GRAY + SmallCaps.convert("total sold: ")
                + ChatColor.WHITE + sold);
        iconLore.add(ChatColor.DARK_GRAY + "───────────────────");

        inv.setItem(4, makeItem(cfg.getCategoryMaterial(categoryId),
                cfg.getCategoryDisplayName(categoryId), iconLore));

        // ── Snake path ──────────────────────────────────────────────────────
        buildSnakePath(mult);

        // ── Back button (bottom-right) ──────────────────────────────────────
        List<String> backLore = new ArrayList<>();
        backLore.add(ChatColor.GRAY + SmallCaps.convert("return to the main menu."));
        inv.setItem(SLOT_BACK,
                makeItem(Material.ARROW,
                        ChatColor.RED + "" + ChatColor.BOLD + SmallCaps.convert("back"),
                        backLore));
    }

    // ── Snake / U-Path builder ───────────────────────────────────────────────

    private void buildSnakePath(double currentMultiplier) {
        ConfigManager cfg = plugin.getConfigManager();

        for (int i = 0; i < PATH.length; i++) {
            int slot = PATH[i];
            double milestone = MILESTONES[i];

            // Determine colour state
            boolean completed = currentMultiplier >= milestone + 0.1 - 0.001;
            boolean inProgress = !completed
                    && currentMultiplier >= milestone - 0.001;

            Material paneMat;
            ChatColor nameColour;
            String status;

            if (completed) {
                paneMat = Material.LIME_STAINED_GLASS_PANE;
                nameColour = ChatColor.GREEN;
                status = SmallCaps.convert("completed");
            } else if (inProgress) {
                paneMat = Material.YELLOW_STAINED_GLASS_PANE;
                nameColour = ChatColor.YELLOW;
                status = SmallCaps.convert("in progress");
            } else {
                paneMat = Material.GRAY_STAINED_GLASS_PANE;
                nameColour = ChatColor.DARK_GRAY;
                status = SmallCaps.convert("locked");
            }

            // For the very first node, use the category material instead of glass
            boolean isStart = (i == 0);
            Material displayMat = isStart ? cfg.getCategoryMaterial(categoryId) : paneMat;

            String label = nameColour + "" + ChatColor.BOLD
                    + String.format("%.1fx", milestone)
                    + " " + SmallCaps.convert("multiplier");

            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.DARK_GRAY + "───────────────────");
            lore.add(ChatColor.GRAY + SmallCaps.convert("status: ") + nameColour + status);

            if (isStart) {
                lore.add(ChatColor.DARK_GRAY + "───────────────────");
                int itemCount = plugin.getSellManager().countCategoryItems(player, categoryId);
                if (itemCount > 0) {
                    double sellValue = plugin.getSellManager().calculateCategoryValue(player, categoryId);
                    lore.add(ChatColor.GREEN + SmallCaps.convert("click to sell ")
                            + ChatColor.WHITE + itemCount
                            + ChatColor.GREEN + SmallCaps.convert(" items"));
                    lore.add(ChatColor.GREEN + SmallCaps.convert("earn: ")
                            + ChatColor.GOLD + "$" + String.format("%.2f", sellValue));
                } else {
                    lore.add(ChatColor.RED + SmallCaps.convert("no sellable items found."));
                }
            }

            if (!completed && !isStart) {
                // Show how many more items needed (rough estimate)
                double step = cfg.getMultiplierStep();
                if (step > 0) {
                    double needed = (milestone - 1.0) / step;
                    int totalNeeded = (int) Math.ceil(needed);
                    int alreadySold = getTotalSold();
                    int remaining = Math.max(0, totalNeeded - alreadySold);
                    if (remaining > 0) {
                        lore.add(ChatColor.GRAY + SmallCaps.convert("sell ")
                                + ChatColor.WHITE + remaining
                                + ChatColor.GRAY + SmallCaps.convert(" more items to unlock"));
                    }
                }
            }

            inv.setItem(slot, makeItem(displayMat, label, lore));
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Returns the slot index of the first path node (the sell button). */
    public int getSellSlot() {
        return PATH[0];
    }

    /** Check whether a given slot is the first path node (sell slot). */
    public boolean isSellSlot(int slot) {
        return slot == PATH[0];
    }

    private int getTotalSold() {
        MultiplierManager mm = plugin.getMultiplierManager();
        return mm.getStats(player).getOrDefault(categoryId, 0);
    }

    private ItemStack makeItem(Material mat, String name, List<String> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (!lore.isEmpty()) meta.setLore(lore);
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

    public String getCategoryId() {
        return categoryId;
    }
}
