package com.yourname.sellplugin.gui;

import com.yourname.sellplugin.SellPlugin;
import com.yourname.sellplugin.manager.ConfigManager;
import com.yourname.sellplugin.util.NumberFormatter;
import com.yourname.sellplugin.util.Text;
import org.bukkit.Bukkit;
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

    // Shipped defaults, used only when a config key has been deleted outright.
    private static final List<String> DEFAULT_NODE_LORE = List.of(
            "<dark_gray>━━━━━━━━━━━━━━━━━━━",
            "<gray> ▸ Status: {status_color}{status}"
    );
    private static final List<String> DEFAULT_NODE_LORE_START = List.of(
            "<dark_gray>━━━━━━━━━━━━━━━━━━━",
            "<yellow> ✦ Click to view items & prices"
    );
    private static final List<String> DEFAULT_NODE_LORE_PROGRESS = List.of(
            "<dark_gray>━━━━━━━━━━━━━━━━━━━",
            "<gray> ▸ Earned: <green>${earned}",
            "<gray> ▸ Required: <green>${required}",
            "<gray> ▸ Progress: <yellow>{percent}%"
    );
    private static final List<String> DEFAULT_NODE_LORE_LOCKED = List.of(
            "<gray> ▸ Need: <green>${remaining}<gray> more to unlock"
    );

    /**
     * W-shape path (21 nodes).
     *
     * Two connected U-shapes form a W across rows 1-4 (U1) and rows 0-4 (U2).
     * Slot layout reference (row × col, 0-indexed):
     *   Col:  0   1   2   3   4   5   6   7   8
     *   Row0: 0   1   2   3   4   5   6   7   8
     *   Row1: 9  10  11  12  13  14  15  16  17
     *   Row2: 18  19  20  21  22  23  24  25  26
     *   Row3: 27  28  29  30  31  32  33  34  35
     *   Row4: 36  37  38  39  40  41  42  43  44
     *   Row5: 45  46  47  48  49  50  51  52  53
     *
     * Visual W (cols 1-8, rows 0-4):
     *    .   .   .   .   .   .  [7] [8]
     *   [10] .  [12][13][14] .  [16] .
     *   [19] .  [21] .  [23] .  [25] .
     *   [28] .  [30] .  [32] .  [34] .
     *   [37][38][39] .  [41][42][43] .
     *
     * U1: ↓ col1 (rows 1-4) → right 2 (row4) → ↑ col3 (rows 4-1) → right 2 (row1)
     * U2: ↓ col5 (rows 1-4) → right 2 (row4) → ↑ col7 (rows 4-0) → right 1 (row0)
     */
    private static final int[] PATH = {
            10, 19, 28, 37, 38, 39, 30, 21, 12, 13, 14,   // U1: down col1, right, up col3, step right
            23, 32, 41, 42, 43, 34, 25, 16,  7,  8        // U2: down col5, right, up col7, step right
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
        String title = Text.legacy(Text.fill(
                cfg.getRawText("category-progress.title", "{category}"),
                "category", cfg.getRawCategoryDisplayName(categoryId)));
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
        List<String> backLore = cfg.getIconLore("back", List.of("<gray> ▸ Return to the main menu."));
        inv.setItem(SLOT_BACK,
                makeItem(cfg.getIconMaterial("back", Material.ARROW),
                        cfg.getIconName("back", "<red><bold>Back"),
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
            String state;
            if (completed) {
                paneMat = cfg.getProgressBarCompletedColor();
                state = "completed";
            } else if (inProgress) {
                paneMat = cfg.getProgressBarInProgressColor();
                state = "in-progress";
            } else {
                paneMat = cfg.getProgressBarLockedColor();
                state = "locked";
            }

            // First node uses the category icon instead of glass
            boolean isStart = (i == 0);
            Material displayMat = isStart ? cfg.getCategoryMaterial(categoryId) : paneMat;

            double moneyRequired = plugin.getMultiplierManager().getCumulativeThreshold(i + 1);
            double percentage = moneyRequired > 0
                    ? Math.min(100.0, (moneyEarned / moneyRequired) * 100.0)
                    : 0;
            double remaining = Math.max(0, plugin.getMultiplierManager().getCumulativeThreshold(i) - moneyEarned);

            Object[] placeholders = {
                    "category", cfg.getRawCategoryDisplayName(categoryId),
                    "milestone", String.format("%.1f", milestone),
                    "status", cfg.getRawText("category-progress.status-" + state, defaultStatus(state)),
                    "status_color", cfg.getRawText("category-progress.status-color-" + state, defaultStatusColor(state)),
                    "earned", NumberFormatter.format(moneyEarned),
                    "required", NumberFormatter.format(moneyRequired),
                    "percent", String.format("%.1f", percentage),
                    "remaining", NumberFormatter.format(remaining)
            };

            String label = Text.legacy(Text.fill(
                    cfg.getRawText("category-progress.node-name", "{status_color}<bold>{milestone}x Multiplier"),
                    placeholders));

            List<String> lore = new ArrayList<>(
                    renderLines(cfg.getRawTextList("category-progress.node-lore", DEFAULT_NODE_LORE), placeholders));

            if (isStart) {
                lore.addAll(renderLines(
                        cfg.getRawTextList("category-progress.node-lore-start", DEFAULT_NODE_LORE_START), placeholders));
            }
            // Only a node being worked toward has a meaningful requirement, and
            // only a locked one has a gap left to close.
            if (inProgress && moneyRequired > 0) {
                lore.addAll(renderLines(
                        cfg.getRawTextList("category-progress.node-lore-progress", DEFAULT_NODE_LORE_PROGRESS), placeholders));
            }
            if (!completed && !inProgress && !isStart && remaining > 0) {
                lore.addAll(renderLines(
                        cfg.getRawTextList("category-progress.node-lore-locked", DEFAULT_NODE_LORE_LOCKED), placeholders));
            }

            inv.setItem(slot, makeItem(displayMat, label, lore));
        }
    }

    private List<String> renderLines(List<String> template, Object... placeholders) {
        List<String> rendered = new ArrayList<>(template.size());
        for (String line : template) rendered.add(Text.legacy(Text.fill(line, placeholders)));
        return rendered;
    }

    private String defaultStatus(String state) {
        return switch (state) {
            case "completed" -> "Completed";
            case "in-progress" -> "In progress";
            default -> "Locked";
        };
    }

    private String defaultStatusColor(String state) {
        return switch (state) {
            case "completed" -> "<green>";
            case "in-progress" -> "<yellow>";
            default -> "<dark_gray>";
        };
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

