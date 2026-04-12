package com.sellplugin.gui;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import java.util.*;

public class CategoryGUI {
    private static final int GUI_SIZE = 45;
    private final Player player;
    private final Map<Integer, ItemStack> categoryItems;

    public CategoryGUI(Player player) {
        this.player = player;
        this.categoryItems = new HashMap<>();
    }

    public void open() {
        Inventory inventory = Bukkit.createInventory(null, GUI_SIZE, ChatColor.GOLD + ChatColor.BOLD + "Sell Menu");
        List<CategoryData> categories = Arrays.asList(
            new CategoryData("Ores", Material.IRON_ORE),
            new CategoryData("Logs", Material.OAK_LOG),
            new CategoryData("Crops", Material.WHEAT),
            new CategoryData("Building", Material.DIRT),
            new CategoryData("Valuables", Material.DIAMOND),
            new CategoryData("Dyes", Material.RED_DYE),
            new CategoryData("Food", Material.PUMPKIN),
            new CategoryData("Misc", Material.COBBLESTONE),
            new CategoryData("Blocks", Material.STONE)
        );

        for (int i = 0; i < 9; i++) {
            ItemStack item = createCategoryItem(categories.get(i).getName(), categories.get(i).getMaterial());
            inventory.setItem(i, item);
            categoryItems.put(i, item);
        }

        ItemStack glass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = glass.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            glass.setItemMeta(meta);
        }

        for (int i = 9; i < GUI_SIZE; i++) {
            inventory.setItem(i, glass);
        }

        player.openInventory(inventory);
    }

    private ItemStack createCategoryItem(String name, Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.YELLOW + name);
            item.setItemMeta(meta);
        }
        return item;
    }

    public static class CategoryData {
        private final String name;
        private final Material material;

        public CategoryData(String name, Material material) {
            this.name = name;
            this.material = material;
        }

        public String getName() {
            return name;
        }

        public Material getMaterial() {
            return material;
        }
    }
}