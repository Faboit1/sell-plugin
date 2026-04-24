package com.yourname.sellplugin.gui;

import com.yourname.sellplugin.SellPlugin;
import com.yourname.sellplugin.manager.ConfigManager;
import com.yourname.sellplugin.util.NumberFormatter;
import com.yourname.sellplugin.util.SmallCaps;
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
 * Rows 1-4 (slots 0-35): empty area – players can place items here to sell.
 * Row 5 (slots 36-44):   up to 9 category buttons with proper icons.
 *
 * When the GUI is closed, every sellable item left in slots 0-35 is sold
 * automatically and non-sellable items are returned to the player.
 */
public class ShopMainGUI implements InventoryHolder {

    private static final int ROWS = 5;
    private static final int SIZE = ROWS * 9; // 45

    /** First slot of the protected bottom row (category buttons). */
    public static final int BOTTOM_ROW_START = 36;

    private final Inventory inv;
    private final SellPlugin plugin;
    private final Player player;

    public ShopMainGUI(SellPlugin plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
        // Title in small caps: "put items here to sell"
        String title = ChatColor.DARK_GRAY + "" + ChatColor.BOLD + SmallCaps.convert("put items here to sell");
        this.inv = Bukkit.createInventory(this, SIZE, title);
        populate();
    }

    private void populate() {
        ConfigManager cfg = plugin.getConfigManager();

        // Rows 1-4 (slots 0-35) are left EMPTY for item placement.

        // --- Category buttons in bottom row (slots 36-44) ---
        List<String> catOrder = cfg.getCategoryOrder();

        // Fill bottom row with dark-gray glass as spacer
        ItemStack catBg = makeItem(Material.GRAY_STAINED_GLASS_PANE, " ", Collections.emptyList());
        for (int slot = BOTTOM_ROW_START; slot <= 44; slot++) inv.setItem(slot, catBg);

        for (int i = 0; i < Math.min(9, catOrder.size()); i++) {
            inv.setItem(BOTTOM_ROW_START + i, buildCategoryButton(catOrder.get(i)));
        }
    }

    private ItemStack buildCategoryButton(String catId) {
        ConfigManager cfg = plugin.getConfigManager();

        double value = plugin.getSellManager().calculateCategoryValue(player, catId);
        double multiplier = plugin.getMultiplierManager().getMultiplier(player, catId);
        double dailyBonus = plugin.getDailyBonusManager().getDailyBonus(catId);
        double effective  = multiplier + dailyBonus;

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.DARK_GRAY + "━━━━━━━━━━━━━━━━━━━");
        lore.add(ChatColor.GRAY + " ▸ " + SmallCaps.convert("value: ")
                + ChatColor.GREEN + "$" + NumberFormatter.format(value));
        lore.add(ChatColor.GRAY + " ▸ " + SmallCaps.convert("multiplier: ")
                + ChatColor.AQUA + String.format("%.2fx", effective));
        if (dailyBonus > 0) {
            lore.add(ChatColor.GOLD + " ▸ \uD83D\uDD25 " + SmallCaps.convert("daily boost: ")
                    + ChatColor.YELLOW + "+" + String.format("%.2f", dailyBonus) + "x");
        }
        lore.add(ChatColor.DARK_GRAY + "━━━━━━━━━━━━━━━━━━━");
        lore.add(ChatColor.YELLOW + " ✦ " + SmallCaps.convert("click to view progress!"));

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

    public SellPlugin getPlugin() {
        return plugin;
    }

    /** Returns the category ID for a bottom-row slot (36-44), or null if not a category slot. */
    public String getCategoryAtSlot(int slot) {
        if (slot < BOTTOM_ROW_START || slot > 44) return null;
        List<String> order = plugin.getConfigManager().getCategoryOrder();
        int idx = slot - BOTTOM_ROW_START;
        if (idx < order.size()) return order.get(idx);
        return null;
    }
}
