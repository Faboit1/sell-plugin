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

/**
 * 9x6 (54-slot) items-list GUI showing all items in a category with prices.
 *
 * Layout:
 *   Rows 0–4 (slots 0–44): up to 45 item icons per page.
 *   Row 5   (slots 45–53): navigation bar.
 *     [PREV] [G] [G] [G] [INFO] [G] [G] [G] [NEXT]
 *       45                  49                  53
 */
public class CategoryItemsGUI implements InventoryHolder {

    private static final int GUI_SIZE = 54;
    private static final int ITEMS_PER_PAGE = 45;
    private static final Material NAV_FILL = Material.BLACK_STAINED_GLASS_PANE;

    private final Inventory inv;
    private final SellPlugin plugin;
    private final String category;
    private final List<String> itemKeys;
    private final int page;
    private final int totalPages;

    public CategoryItemsGUI(SellPlugin plugin, Player player, String category, int page) {
        this.plugin   = plugin;
        this.category = category;
        this.itemKeys = new ArrayList<>(plugin.getPriceManager().getItemsInCategory(category));
        this.page     = page;
        this.totalPages = Math.max(1, (int) Math.ceil(itemKeys.size() / (double) ITEMS_PER_PAGE));

        String title = plugin.getConfigManager().getCategoryDisplayName(category)
                + ChatColor.DARK_GRAY + " [" + (page + 1) + "/" + totalPages + "]";
        this.inv = Bukkit.createInventory(this, GUI_SIZE, title);
        buildInventory(player);
    }

    private void buildInventory(Player player) {
        // Item display slots 0–44
        int start = page * ITEMS_PER_PAGE;
        int end   = Math.min(start + ITEMS_PER_PAGE, itemKeys.size());

        for (int i = start; i < end; i++) {
            String key = itemKeys.get(i);
            inv.setItem(i - start, makeItemDisplay(key, player));
        }

        // Navigation row (slots 45–53)
        ItemStack navFill = makeNavFill();
        for (int i = 45; i < 54; i++) {
            inv.setItem(i, navFill);
        }
        if (page > 0) {
            inv.setItem(45, makePrevButton());
        }
        inv.setItem(49, makeInfoButton(player));
        if (page < totalPages - 1) {
            inv.setItem(53, makeNextButton());
        }
        inv.setItem(48, makeBackButton());
    }

    private ItemStack makeItemDisplay(String key, Player player) {
        // Derive the material from the key (handles "POTION:NIGHT_VISION" → POTION)
        String matName = key.contains(":") ? key.split(":")[0] : key;
        Material mat = Material.matchMaterial(matName);
        if (mat == null) mat = Material.PAPER;

        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String displayName = ChatColor.YELLOW + formatKey(key);
            meta.setDisplayName(displayName);

            double basePrice = plugin.getPriceManager().getPrice(key);
            double multi     = plugin.getMultiplierManager().getMultiplier(player, category);
            double finalPrice = basePrice * multi;

            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Base price:  " + ChatColor.WHITE
                    + String.format("$%.2f", basePrice));
            lore.add(ChatColor.GRAY + "Your price:  " + ChatColor.GREEN
                    + String.format("$%.2f", finalPrice));
            lore.add(ChatColor.GRAY + "Multiplier:  " + ChatColor.AQUA
                    + String.format("%.2fx", multi));
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack makePrevButton() {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.YELLOW + "← Previous Page");
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack makeNextButton() {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.YELLOW + "Next Page →");
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack makeInfoButton(Player player) {
        Material mat = plugin.getConfigManager().getCategoryIcon(category);
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(plugin.getConfigManager().getCategoryDisplayName(category));
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Page: " + ChatColor.WHITE + (page + 1) + "/" + totalPages);
            lore.add(ChatColor.GRAY + "Items: " + ChatColor.WHITE + itemKeys.size());
            double multi = plugin.getMultiplierManager().getMultiplier(player, category);
            lore.add(ChatColor.GRAY + "Multiplier: " + ChatColor.GREEN
                    + String.format("%.2fx", multi));
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack makeBackButton() {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.RED + "← Back");
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Return to category view");
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack makeNavFill() {
        ItemStack pane = new ItemStack(NAV_FILL);
        ItemMeta meta = pane.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            pane.setItemMeta(meta);
        }
        return pane;
    }

    private String formatKey(String key) {
        // "DIAMOND_SWORD" → "Diamond Sword"
        // "POTION:NIGHT_VISION" → "Potion (Night Vision)"
        if (key.contains(":")) {
            String[] parts = key.split(":", 2);
            return toTitle(parts[0]) + " (" + toTitle(parts[1]) + ")";
        }
        return toTitle(key);
    }

    private String toTitle(String s) {
        String[] words = s.split("_");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (sb.length() > 0) sb.append(" ");
            if (w.isEmpty()) continue;
            sb.append(w.substring(0, 1).toUpperCase())
              .append(w.substring(1).toLowerCase());
        }
        return sb.toString();
    }

    public String getCategory() {
        return category;
    }

    public int getPage() {
        return page;
    }

    public int getTotalPages() {
        return totalPages;
    }

    @Override
    public Inventory getInventory() {
        return inv;
    }

    public void open(Player player) {
        player.openInventory(inv);
    }
}
