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

public class SellGUI implements InventoryHolder {
    private final Inventory inv;
    private final SellPlugin plugin;

    public SellGUI(SellPlugin plugin, Player player) {
        this.plugin = plugin;
        String title = plugin.getConfigManager().getSellAllGuiTitle();
        int size = plugin.getConfigManager().getSellAllGuiSize();
        
        this.inv = Bukkit.createInventory(this, size, title);
        
        setupItems(player);
    }

    private void setupItems(Player player) {
        // Fill with glass panes
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();
        if (fillerMeta != null) {
            fillerMeta.setDisplayName(" ");
            filler.setItemMeta(fillerMeta);
        }
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, filler);
        }

        int slot = plugin.getConfigManager().getSellAllSlot();
        Material mat = Material.matchMaterial(plugin.getConfigManager().getSellAllMaterial());
        if (mat == null) mat = Material.EMERALD_BLOCK;

        ItemStack sellAllBtn = new ItemStack(mat);
        ItemMeta meta = sellAllBtn.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(plugin.getConfigManager().getSellAllName());
            
            // Build the lore dynamically to show multipliers
            List<String> rawLore = plugin.getConfigManager().getSellAllLore();
            List<String> finalLore = new ArrayList<>();
            
            Set<String> categories = plugin.getPriceManager().getCategories();
            
            for (String line : rawLore) {
                if (line.contains("{multipliers}")) {
                    for (String cat : categories) {
                        double multi = plugin.getMultiplierManager().getMultiplier(player, cat);
                        String formatted = String.format("%.2f", multi);
                        finalLore.add(ChatColor.translateAlternateColorCodes('&', "&e  \u25b6 &f" + cat + ": &a" + formatted + "x"));
                    }
                } else {
                    finalLore.add(ChatColor.translateAlternateColorCodes('&', line));
                }
            }
            
            meta.setLore(finalLore);
            sellAllBtn.setItemMeta(meta);
        }
        
        inv.setItem(slot, sellAllBtn);
    }

    @Override
    public Inventory getInventory() {
        return inv;
    }

    public void open(Player player) {
        player.openInventory(inv);
    }
}
