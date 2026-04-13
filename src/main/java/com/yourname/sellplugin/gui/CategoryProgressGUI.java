package com.yourname.sellplugin.gui;

import com.yourname.sellplugin.SellPlugin;
import com.yourname.sellplugin.manager.ConfigManager;
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
 * A vertical "snake / U-Path" of multiplier milestones winds through the menu.
 * Each milestone goes from 1.0x to 3.0x in 0.1 increments (21 nodes).
 *
 * The snake starts vertically (column 1 going down, then column 2 going up, …).
 *
 * Colour key (configurable via config.yml progress-bar section):
 *   GREEN  – completed milestone
 *   YELLOW – current / in-progress milestone (shows money earned & required)
 *   GRAY   – locked / future milestone
 *
 * The very first path node opens the CategoryItemsGUI.
 * Back button sits at slot 53 (bottom-right).
 */
public class CategoryProgressGUI implements InventoryHolder {

    // ── Constants ────────────────────────────────────────────────────────────

    private static final int SIZE = 54;

    /** Floating-point tolerance for milestone comparisons. */
    private static final double EPSILON = 0.001;

    /** Back button slot (bottom-right). */
    public static final int SLOT_BACK = 53;

    /**
     * Diagonal V-shape path (21 nodes).
     *
     * Three connected V-shapes march diagonally from the top-left to the
     * bottom-right of the inventory.  Each V has the same step pattern:
     *   diagonal ↘ (or ↙), straight ↓, → →, straight ↑, diagonal ↗ (or ↖)
     *
     * Slot layout reference (row × col, 0-indexed):
     *   Col:  0   1   2   3   4   5   6   7   8
     *   Row0: 0   1   2   3   4   5   6   7   8
     *   Row1: 9  10  11  12  13  14  15  16  17
     *   Row2: 18  19  20  21  22  23  24  25  26
     *   Row3: 27  28  29  30  31  32  33  34  35
     *   Row4: 36  37  38  39  40  41  42  43  44
     *   Row5: 45  46  47  48  49  50  51  52  53
     *
     * V1 (rows 0-2, left):   1→9→18→19→20→11→3
     *   diag↙ : (r0c1)→(r1c0)   straight↓: (r1c0)→(r2c0)
     *   →→    : (r2c0)→(r2c1)→(r2c2)
     *   straight↑: (r2c2)→(r1c2)  diag↗: (r1c2)→(r0c3)
     *
     * connector: 3(r0c3) → 13(r1c4)  [diagonal ↘, +10]
     *
     * V2 (rows 1-3, middle): 13→21→30→31→32→23→15
     *   diag↙ : (r1c4)→(r2c3)   straight↓: (r2c3)→(r3c3)
     *   →→    : (r3c3)→(r3c4)→(r3c5)
     *   straight↑: (r3c5)→(r2c5)  diag↗: (r2c5)→(r1c6)
     *
     * connector: 15(r1c6) → 25(r2c7)  [diagonal ↘, +10]
     *
     * V3 (rows 2-4, right):  25→33→42→43→44→35→26
     *   diag↙ : (r2c7)→(r3c6)   straight↓: (r3c6)→(r4c6)
     *   →→    : (r4c6)→(r4c7)→(r4c8)
     *   straight↑: (r4c8)→(r3c8)  straight↑: (r3c8)→(r2c8)
     */
    private static final int[] PATH = {
             1,  9, 18, 19, 20, 11,  3,   // V1: rows 0-2 (left)
            13, 21, 30, 31, 32, 23, 15,   // V2: rows 1-3 (middle)
            25, 33, 42, 43, 44, 35, 26    // V3: rows 2-4 (right)
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

        // Fill everything with configurable filler block
        Material fillerMat = cfg.getFillerBlock();
        ItemStack bg = makeItem(fillerMat, " ", Collections.emptyList());
        for (int i = 0; i < SIZE; i++) inv.setItem(i, bg);

        // ── Snake path ──────────────────────────────────────────────────────
        double mult = plugin.getMultiplierManager().getMultiplier(player, categoryId);
        double moneyEarned = plugin.getMultiplierManager().getMoneyEarned(player, categoryId);
        buildSnakePath(mult, moneyEarned);

        // ── Back button (bottom-right) ──────────────────────────────────────
        List<String> backLore = cfg.getIconLore("back",
                Collections.singletonList(ChatColor.GRAY + SmallCaps.convert("return to the main menu.")));
        inv.setItem(SLOT_BACK,
                makeItem(cfg.getIconMaterial("back", Material.ARROW),
                        cfg.getIconName("back", "&c&l" + SmallCaps.convert("back")),
                        backLore));
    }

    // ── Snake / U-Path builder ───────────────────────────────────────────────

    private void buildSnakePath(double currentMultiplier, double moneyEarned) {
        ConfigManager cfg = plugin.getConfigManager();

        for (int i = 0; i < PATH.length; i++) {
            int slot = PATH[i];
            double milestone = MILESTONES[i];

            // Determine colour state
            boolean completed = currentMultiplier >= milestone + 0.1 - EPSILON;
            boolean inProgress = !completed && currentMultiplier >= milestone - EPSILON;

            Material paneMat;
            ChatColor nameColour;
            String status;

            if (completed) {
                paneMat = cfg.getProgressBarCompletedColor();
                nameColour = ChatColor.GREEN;
                status = SmallCaps.convert("completed");
            } else if (inProgress) {
                paneMat = cfg.getProgressBarInProgressColor();
                nameColour = ChatColor.YELLOW;
                status = SmallCaps.convert("in progress");
            } else {
                paneMat = cfg.getProgressBarLockedColor();
                nameColour = ChatColor.DARK_GRAY;
                status = SmallCaps.convert("locked");
            }

            // First node uses the category icon instead of glass
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
                lore.add(ChatColor.YELLOW + SmallCaps.convert("click to view items & prices"));
            }

            if (inProgress) {
                // Show cumulative money earned vs required to reach next milestone
                double moneyRequired = plugin.getMultiplierManager().getCumulativeThreshold(i + 1);
                if (moneyRequired > 0) {
                    double percentage = Math.min(100.0, (moneyEarned / moneyRequired) * 100.0);
                    lore.add(ChatColor.DARK_GRAY + "───────────────────");
                    lore.add(ChatColor.GRAY + SmallCaps.convert("earned: ")
                            + ChatColor.GREEN + "$" + String.format("%.2f", moneyEarned));
                    lore.add(ChatColor.GRAY + SmallCaps.convert("required: ")
                            + ChatColor.GREEN + "$" + String.format("%.2f", moneyRequired));
                    lore.add(ChatColor.GRAY + SmallCaps.convert("progress: ")
                            + ChatColor.YELLOW + String.format("%.1f%%", percentage));
                }
            }

            if (!completed && !isStart && !inProgress) {
                // Show how much more money is needed for locked nodes
                double moneyNeeded = plugin.getMultiplierManager().getCumulativeThreshold(i);
                double remaining = Math.max(0, moneyNeeded - moneyEarned);
                if (remaining > 0) {
                    lore.add(ChatColor.GRAY + SmallCaps.convert("earn ")
                            + ChatColor.GREEN + "$" + String.format("%.2f", remaining)
                            + ChatColor.GRAY + SmallCaps.convert(" more to unlock"));
                }
            }

            inv.setItem(slot, makeItem(displayMat, label, lore));
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Returns the slot index of the first path node. */
    public int getSellSlot() {
        return PATH[0];
    }

    /** Check whether a given slot is the first path node. */
    public boolean isSellSlot(int slot) {
        return slot == PATH[0];
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

