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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Simple /sellall GUI – configurable size / item / slot.
 * A single "Sell All" button in the centre.
 */
public class SellAllGUI implements InventoryHolder {

    private final Inventory inv;
    private final SellPlugin plugin;
    private final int sellAllSlot;

    public SellAllGUI(SellPlugin plugin, Player player) {
        this.plugin = plugin;
        ConfigManager cfg = plugin.getConfigManager();
        this.sellAllSlot = cfg.getSellAllSlot();

        int size = cfg.getSellAllGuiSize();
        // Clamp to valid inventory sizes (multiples of 9, 9-54)
        if (size < 9 || size > 54 || size % 9 != 0) size = 27;

        this.inv = Bukkit.createInventory(this, size, cfg.getSellAllGuiTitle());
        populate(player);
    }

    private void populate(Player player) {
        ConfigManager cfg = plugin.getConfigManager();

        // Background
        ItemStack bg = makeItem(Material.BLACK_STAINED_GLASS_PANE, " ", Collections.emptyList());
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, bg);

        // Sell All button
        Material mat = Material.matchMaterial(cfg.getSellAllMaterial());
        if (mat == null) mat = Material.EMERALD_BLOCK;

        Set<String> categories = plugin.getPriceManager().getCategories();
        List<String> lore = new ArrayList<>();
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

        int slot = Math.min(sellAllSlot, inv.getSize() - 1);
        if (sellAllSlot >= inv.getSize()) {
            plugin.getLogger().warning("sell-all-gui.slot (" + sellAllSlot
                    + ") exceeds inventory size (" + inv.getSize()
                    + "). Placing button at slot " + slot + ".");
        }
        inv.setItem(slot, makeItem(mat, cfg.getSellAllName(), lore));
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

    public int getSellAllSlot() {
        return sellAllSlot;
    }
}
