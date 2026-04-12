package com.yourname.sellplugin.gui;

import com.yourname.sellplugin.SellPlugin;
import com.yourname.sellplugin.manager.ConfigManager;
import com.yourname.sellplugin.manager.MultiplierManager;
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
 * Category detail GUI – 9×3 (27 slots).
 *
 * Row 1 (0-8):   Category info panel.
 * Row 2 (9-17):  Multiplier progress bar (9 glass panes).
 * Row 3 (18-26): Buttons – Back (18), View Items (22), Sell Category (26).
 */
public class CategoryProgressGUI implements InventoryHolder {

    // Row 3 button slots
    public static final int SLOT_BACK        = 18;
    public static final int SLOT_VIEW_ITEMS  = 22;
    public static final int SLOT_SELL_CAT    = 26;

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
        this.inv = Bukkit.createInventory(this, 27, title);
        populate();
    }

    private void populate() {
        ConfigManager cfg = plugin.getConfigManager();

        ItemStack bg = makeItem(Material.BLACK_STAINED_GLASS_PANE, " ", Collections.emptyList());
        for (int i = 0; i < 27; i++) inv.setItem(i, bg);

        // ── Row 1: category icon centred at slot 4 ──────────────────────────
        int itemCount  = plugin.getSellManager().countCategoryItems(player, categoryId);
        double value   = plugin.getSellManager().calculateCategoryValue(player, categoryId);
        double mult    = plugin.getMultiplierManager().getMultiplier(player, categoryId);
        int    sold    = getTotalSold();
        double maxMult = cfg.getMaxMultiplier();

        List<String> iconLore = new ArrayList<>();
        iconLore.add(ChatColor.DARK_GRAY + "───────────────────");
        iconLore.add(ChatColor.GRAY + "Items in inventory: " + ChatColor.WHITE + itemCount);
        iconLore.add(ChatColor.GRAY + "Sell value:         " + ChatColor.GOLD + "$" + String.format("%.2f", value));
        iconLore.add(ChatColor.GRAY + "Your multiplier:    " + ChatColor.AQUA + String.format("%.2f", mult) + "x");
        iconLore.add(ChatColor.GRAY + "Total sold:         " + ChatColor.WHITE + sold);
        iconLore.add(ChatColor.DARK_GRAY + "───────────────────");

        inv.setItem(4, makeItem(cfg.getCategoryMaterial(categoryId),
                cfg.getCategoryDisplayName(categoryId), iconLore));

        // ── Row 2: progress bar (slots 9-17) ────────────────────────────────
        buildProgressBar(mult, maxMult);

        // ── Row 3: buttons ──────────────────────────────────────────────────
        // Back
        List<String> backLore = new ArrayList<>();
        backLore.add(ChatColor.GRAY + "Return to the main menu.");
        inv.setItem(SLOT_BACK,
                makeItem(Material.ARROW, ChatColor.RED + "" + ChatColor.BOLD + "Back", backLore));

        // View Items
        List<String> listLore = new ArrayList<>();
        listLore.add(ChatColor.GRAY + "Browse all items in this category.");
        inv.setItem(SLOT_VIEW_ITEMS,
                makeItem(Material.BOOK, ChatColor.YELLOW + "" + ChatColor.BOLD + "View Item List", listLore));

        // Sell Category
        List<String> sellLore = new ArrayList<>();
        sellLore.add(ChatColor.GRAY + "Sell all " + ChatColor.WHITE + categoryId + ChatColor.GRAY + " items");
        sellLore.add(ChatColor.GRAY + "from your inventory.");
        if (itemCount > 0) {
            sellLore.add(ChatColor.GREEN + "You will earn: $" + String.format("%.2f", value));
        } else {
            sellLore.add(ChatColor.RED + "No sellable items found.");
        }
        inv.setItem(SLOT_SELL_CAT,
                makeItem(Material.GOLD_INGOT,
                        ChatColor.GREEN + "" + ChatColor.BOLD + "Sell Category", sellLore));
    }

    // ── Progress bar helpers ─────────────────────────────────────────────────

    private void buildProgressBar(double currentMultiplier, double maxMultiplier) {
        // Guard against division by zero if maxMultiplier == 1.0
        int filled = 0;
        if (maxMultiplier > 1.0) {
            double clamped = Math.max(0.0, Math.min(1.0,
                    (currentMultiplier - 1.0) / (maxMultiplier - 1.0)));
            filled = (int) Math.round(clamped * 9);
        }

        for (int i = 0; i < 9; i++) {
            boolean isFilled = i < filled;
            Material pane = isFilled
                    ? Material.LIME_STAINED_GLASS_PANE
                    : Material.GRAY_STAINED_GLASS_PANE;

            String barName = buildBarLabel(i, filled, currentMultiplier, maxMultiplier);
            inv.setItem(9 + i, makeItem(pane, barName, Collections.emptyList()));
        }
    }

    private String buildBarLabel(int index, int filled, double current, double max) {
        int pct = (int) Math.round(((double) filled / 9) * 100);
        return (index < filled ? ChatColor.GREEN : ChatColor.DARK_GRAY)
                + "Multiplier: " + String.format("%.2f", current) + "x"
                + ChatColor.GRAY + " (" + pct + "% to " + String.format("%.2f", max) + "x)";
    }

    private int getTotalSold() {
        MultiplierManager mm = plugin.getMultiplierManager();
        return mm.getStats(player).getOrDefault(categoryId, 0);
    }

    // ── Inventory holder ────────────────────────────────────────────────────

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
