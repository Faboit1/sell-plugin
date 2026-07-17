package com.yourname.sellplugin.gui;

import com.yourname.sellplugin.SellPlugin;
import com.yourname.sellplugin.manager.ConfigManager;
import com.yourname.sellplugin.manager.PriceManager;
import com.yourname.sellplugin.util.ItemNameFormatter;
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
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionData;
import org.bukkit.potion.PotionType;

import java.util.*;

/**
 * Item Prices GUI opened via /sellworth or /worth.
 * Paginated 6×9 (54 slots) with category filter support.
 *
 * Layout:
 *   Rows 0-4 (slots 0-44): item display
 *   Row 5 (slots 45-53): navigation bar
 *     45 – Back/Close
 *     47 – Previous page
 *     48 – Filter button
 *     49 – Page indicator
 *     50 – Filter button (right)
 *     51 – Next page
 *     53 – (unused)
 */
public class WorthGUI implements InventoryHolder {

    private static final int ITEMS_PER_PAGE = 45;

    // Navigation slots
    public static final int SLOT_CLOSE   = 45;
    public static final int SLOT_PREV    = 47;
    public static final int SLOT_FILTER  = 48;
    public static final int SLOT_INFO    = 49;
    public static final int SLOT_NEXT    = 51;

    // Filter categories (null = all)
    private static final String FILTER_ALL = "all";

    private final Inventory inv;
    private final SellPlugin plugin;
    private final Player player;
    private final String filter; // category filter or "all"
    private final List<String> itemKeys;
    private int page;

    public WorthGUI(SellPlugin plugin, Player player, String filter, int page) {
        this.plugin = plugin;
        this.player = player;
        this.filter = filter != null ? filter : FILTER_ALL;
        this.page = page;
        this.itemKeys = buildItemKeyList();

        ConfigManager cfg = plugin.getConfigManager();
        String titleBase = cfg.getText("worth-gui.title", "&8&lItem Prices");
        String pageStr = " (Page " + (page + 1) + ")";
        this.inv = Bukkit.createInventory(this, 54, titleBase + pageStr);
        populate();
    }

    private List<String> buildItemKeyList() {
        PriceManager pm = plugin.getPriceManager();
        List<String> keys = new ArrayList<>();
        for (String key : pm.getAllItemKeys()) {
            if (FILTER_ALL.equals(filter) || filter.equalsIgnoreCase(pm.getCategory(key))) {
                keys.add(key);
            }
        }
        Collections.sort(keys);
        return keys;
    }

    private void populate() {
        inv.clear();
        ConfigManager cfg = plugin.getConfigManager();

        // Background for navigation row
        Material fillerMat = cfg.getFillerBlock();
        ItemStack bg = makeItem(fillerMat, " ", Collections.emptyList());
        for (int i = 45; i < 54; i++) inv.setItem(i, bg);

        // Items area
        int start = page * ITEMS_PER_PAGE;
        int end = Math.min(start + ITEMS_PER_PAGE, itemKeys.size());
        for (int i = start; i < end; i++) {
            inv.setItem(i - start, buildItemDisplay(itemKeys.get(i)));
        }

        // Fill remaining item area
        ItemStack filler = makeItem(Material.GRAY_STAINED_GLASS_PANE, " ", Collections.emptyList());
        for (int i = (end - start); i < 45; i++) inv.setItem(i, filler);

        // Close button
        List<String> closeLore = cfg.getIconLore("topsell-close",
                Collections.singletonList(ChatColor.GRAY + "Close the menu."));
        inv.setItem(SLOT_CLOSE, makeItem(
                cfg.getIconMaterial("topsell-close", Material.BARRIER),
                cfg.getIconName("topsell-close", "&c&lClose"),
                closeLore));

        // Previous page
        if (page > 0) {
            List<String> prevLore = cfg.getIconLore("prev-page",
                    Collections.singletonList(ChatColor.GRAY + "Previous page."));
            inv.setItem(SLOT_PREV, makeItem(
                    cfg.getIconMaterial("prev-page", Material.ARROW),
                    cfg.getIconName("prev-page", "&e← Previous"),
                    prevLore));
        }

        // Filter button
        List<String> filterLore = buildFilterLore();
        String filterName = FILTER_ALL.equals(filter)
                ? cfg.getText("worth-gui.filter-all", "&e&lFILTER: &fAll")
                : cfg.getText("worth-gui.filter-category", "&e&lFILTER: &f") + cfg.getCategoryDisplayName(filter);
        inv.setItem(SLOT_FILTER, makeItem(
                Material.HOPPER,
                filterName,
                filterLore));

        // Page indicator
        int totalPages = Math.max(1, (int) Math.ceil((double) itemKeys.size() / ITEMS_PER_PAGE));
        List<String> infoLore = Collections.singletonList(
                ChatColor.GRAY + "Total items: " + itemKeys.size());
        inv.setItem(SLOT_INFO, makeItem(
                cfg.getIconMaterial("page-indicator", Material.PAPER),
                ChatColor.WHITE + "Page " + (page + 1) + " / " + totalPages,
                infoLore));

        // Next page
        if ((page + 1) * ITEMS_PER_PAGE < itemKeys.size()) {
            List<String> nextLore = cfg.getIconLore("next-page",
                    Collections.singletonList(ChatColor.GRAY + "Next page."));
            inv.setItem(SLOT_NEXT, makeItem(
                    cfg.getIconMaterial("next-page", Material.ARROW),
                    cfg.getIconName("next-page", "&eNext →"),
                    nextLore));
        }
    }

    private List<String> buildFilterLore() {
        ConfigManager cfg = plugin.getConfigManager();
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Click to cycle filter.");
        lore.add("");

        List<String> categories = cfg.getCategoryOrder();
        // Show current filter highlighted
        if (FILTER_ALL.equals(filter)) {
            lore.add(ChatColor.GREEN + " • All");
        } else {
            lore.add(ChatColor.GRAY + " • All");
        }
        for (String cat : categories) {
            String displayName = ChatColor.stripColor(cfg.getCategoryDisplayName(cat));
            if (cat.equalsIgnoreCase(filter)) {
                lore.add(ChatColor.GREEN + " • " + displayName);
            } else {
                lore.add(ChatColor.GRAY + " • " + displayName);
            }
        }
        return lore;
    }

    private ItemStack buildItemDisplay(String itemKey) {
        PriceManager pm = plugin.getPriceManager();
        double base = pm.getPrice(itemKey);
        String category = pm.getCategory(itemKey);
        double effectiveMultiplier = plugin.getMultiplierManager().getEffectiveMultiplier(player, category);
        double effective = base * effectiveMultiplier;

        ItemStack item = resolveItemStack(itemKey);

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.DARK_GRAY + "━━━━━━━━━━━━━━━━━━━━━");
        lore.add(ChatColor.GRAY + " ▸ " + ChatColor.WHITE + "Base:  "
                + ChatColor.GREEN + "$" + NumberFormatter.format(base));
        lore.add(ChatColor.GRAY + " ▸ " + ChatColor.WHITE + "Mult:  "
                + ChatColor.AQUA + String.format("%.2fx", effectiveMultiplier));
        lore.add(ChatColor.GRAY + " ▸ " + ChatColor.WHITE + "Price: "
                + ChatColor.GREEN + "$" + NumberFormatter.format(effective));
        lore.add(ChatColor.DARK_GRAY + "━━━━━━━━━━━━━━━━━━━━━");
        lore.add(ChatColor.GRAY + " Category: " + ChatColor.WHITE
                + ChatColor.stripColor(plugin.getConfigManager().getCategoryDisplayName(category)));

        String displayName = ChatColor.WHITE + ItemNameFormatter.formatKey(itemKey);

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(displayName);
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack resolveItemStack(String itemKey) {
        if (itemKey.contains(":")) {
            String[] parts = itemKey.split(":", 2);
            Material mat = Material.matchMaterial(parts[0]);
            if (mat == null) return new ItemStack(Material.BARRIER);

            ItemStack item = new ItemStack(mat);
            ItemMeta meta = item.getItemMeta();
            if (meta instanceof PotionMeta potionMeta) {
                try {
                    PotionType type = PotionType.valueOf(parts[1]);
                    potionMeta.setBasePotionData(new PotionData(type));
                    item.setItemMeta(meta);
                } catch (IllegalArgumentException ignored) {}
            }
            return item;
        }
        Material mat = Material.matchMaterial(itemKey);
        return new ItemStack(mat != null ? mat : Material.BARRIER);
    }

    /** Cycle to the next filter category. */
    public String getNextFilter() {
        List<String> categories = plugin.getConfigManager().getCategoryOrder();
        if (FILTER_ALL.equals(filter)) {
            return categories.isEmpty() ? FILTER_ALL : categories.get(0);
        }
        int idx = -1;
        for (int i = 0; i < categories.size(); i++) {
            if (categories.get(i).equalsIgnoreCase(filter)) {
                idx = i;
                break;
            }
        }
        if (idx < 0 || idx >= categories.size() - 1) {
            return FILTER_ALL;
        }
        return categories.get(idx + 1);
    }

    // ── Navigation ───────────────────────────────────────────────────────────

    public boolean hasPrevPage() { return page > 0; }

    public boolean hasNextPage() {
        return (page + 1) * ITEMS_PER_PAGE < itemKeys.size();
    }

    public WorthGUI prevPage() {
        return new WorthGUI(plugin, player, filter, page - 1);
    }

    public WorthGUI nextPage() {
        return new WorthGUI(plugin, player, filter, page + 1);
    }

    public String getFilter() { return filter; }
    public int getPage() { return page; }

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
}
