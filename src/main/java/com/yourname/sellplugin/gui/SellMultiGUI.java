package com.yourname.sellplugin.gui;

import com.yourname.sellplugin.SellPlugin;
import com.yourname.sellplugin.manager.ConfigManager;
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
 * 1×9 multiplier overview GUI opened via /sellmulti.
 * Shows all category multipliers at a glance.
 */
public class SellMultiGUI implements InventoryHolder {

    private static final int SIZE = 9;

    // Shipped defaults, used only when a config key has been deleted outright.
    private static final String DEFAULT_TITLE = "<dark_gray><bold>Multipliers";
    private static final List<String> DEFAULT_CATEGORY_LORE = List.of(
            "<dark_gray>━━━━━━━━━━━━━━━━━━━",
            "<gray> ▸ Earned: <aqua>{earned}x",
            "<gray> ▸ Effective: <green>{effective}x",
            "<dark_gray>━━━━━━━━━━━━━━━━━━━",
            "<yellow> ✦ Click to view progress"
    );

    private final Inventory inv;
    private final SellPlugin plugin;
    private final Player player;

    public SellMultiGUI(SellPlugin plugin, Player player) {
        this.plugin = plugin;
        this.player = player;

        ConfigManager cfg = plugin.getConfigManager();
        String title = cfg.getText("sellmulti.title", DEFAULT_TITLE);
        this.inv = Bukkit.createInventory(this, SIZE, title);
        populate();
    }

    private void populate() {
        ConfigManager cfg = plugin.getConfigManager();
        List<String> catOrder = cfg.getCategoryOrder();

        // Fill with filler
        Material fillerMat = cfg.getFillerBlock();
        ItemStack bg = makeItem(fillerMat, " ", Collections.emptyList());
        for (int i = 0; i < SIZE; i++) inv.setItem(i, bg);

        for (int i = 0; i < Math.min(SIZE, catOrder.size()); i++) {
            inv.setItem(i, buildMultiplierIcon(catOrder.get(i)));
        }
    }

    private ItemStack buildMultiplierIcon(String catId) {
        ConfigManager cfg = plugin.getConfigManager();

        double multiplier = plugin.getMultiplierManager().getMultiplier(player, catId);
        double effective = multiplier;

        List<String> lore = new ArrayList<>();
        for (String line : cfg.getRawTextList("sellmulti.category-lore", DEFAULT_CATEGORY_LORE)) {
            lore.add(Text.legacy(Text.fill(line,
                    "category", cfg.getRawCategoryDisplayName(catId),
                    "earned", String.format("%.2f", multiplier),
                    "effective", String.format("%.2f", effective))));
        }

        return makeItem(cfg.getCategoryMaterial(catId), cfg.getCategoryDisplayName(catId), lore);
    }

    /** Returns the category ID for a slot, or null if not a category slot. */
    public String getCategoryAtSlot(int slot) {
        if (slot < 0 || slot >= SIZE) return null;
        List<String> order = plugin.getConfigManager().getCategoryOrder();
        if (slot < order.size()) return order.get(slot);
        return null;
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

    public SellPlugin getPlugin() {
        return plugin;
    }
}
