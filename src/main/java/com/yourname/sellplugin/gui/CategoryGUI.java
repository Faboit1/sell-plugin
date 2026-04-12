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
import java.util.Set;

/**
 * 9x5 (45-slot) category selection GUI.
 * Row 0–3 (slots 0–35): decorative gray glass panes.
 * Row 4  (slots 36–44): one button per category.
 */
public class CategoryGUI implements InventoryHolder {

    private static final int GUI_SIZE = 45;
    private static final Material FILL = Material.GRAY_STAINED_GLASS_PANE;

    private final Inventory inv;
    private final SellPlugin plugin;

    public CategoryGUI(SellPlugin plugin, Player player) {
        this.plugin = plugin;
        this.inv = Bukkit.createInventory(this, GUI_SIZE,
                plugin.getConfigManager().getCategoryGuiTitle());
        buildInventory(player);
    }

    private void buildInventory(Player player) {
        // Fill top 4 rows with glass panes
        ItemStack filler = makeFiller();
        for (int i = 0; i < 36; i++) {
            inv.setItem(i, filler);
        }

        // Place category buttons in the bottom row (slots 36–44)
        Set<String> cats = plugin.getPriceManager().getCategories();
        List<String> ordered = getOrderedCategories(cats);

        for (int i = 0; i < Math.min(ordered.size(), 9); i++) {
            String category = ordered.get(i);
            inv.setItem(36 + i, makeCategoryItem(category, player));
        }
    }

    /**
     * Returns categories in a consistent order based on config (falls back to alphabetical).
     */
    private List<String> getOrderedCategories(Set<String> cats) {
        // Fixed display order matching config
        String[] preferred = {
            "ores", "blocks", "crops", "enchantedbooks",
            "fish", "mobdrops", "naturalitems", "armortools", "potions"
        };
        List<String> result = new ArrayList<>();
        for (String p : preferred) {
            if (cats.contains(p)) result.add(p);
        }
        // Add any extra categories not in the preferred list
        for (String c : cats) {
            if (!result.contains(c)) result.add(c);
        }
        return result;
    }

    private ItemStack makeCategoryItem(String category, Player player) {
        Material mat = plugin.getConfigManager().getCategoryIcon(category);
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(plugin.getConfigManager().getCategoryDisplayName(category));

            List<String> lore = new ArrayList<>();
            // Config lore lines
            for (String line : plugin.getConfigManager().getCategoryLore(category)) {
                lore.add(ChatColor.translateAlternateColorCodes('&', line));
            }
            lore.add("");
            // Show item count in category
            int count = plugin.getPriceManager().getItemsInCategory(category).size();
            lore.add(ChatColor.GRAY + "Items: " + ChatColor.WHITE + count);
            // Show player's multiplier
            double multi = plugin.getMultiplierManager().getMultiplier(player, category);
            lore.add(ChatColor.GRAY + "Multiplier: " + ChatColor.GREEN
                    + String.format("%.2fx", multi));
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack makeFiller() {
        ItemStack pane = new ItemStack(FILL);
        ItemMeta meta = pane.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            pane.setItemMeta(meta);
        }
        return pane;
    }

    @Override
    public Inventory getInventory() {
        return inv;
    }

    public void open(Player player) {
        player.openInventory(inv);
    }
}
