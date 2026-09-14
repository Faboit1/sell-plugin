package com.yourname.sellplugin.gui;

import com.yourname.sellplugin.SellPlugin;
import com.yourname.sellplugin.manager.ConfigManager;
import com.yourname.sellplugin.manager.PriceManager;
import com.yourname.sellplugin.util.ItemNameFormatter;
import com.yourname.sellplugin.util.NumberFormatter;
import com.yourname.sellplugin.util.Text;
import org.bukkit.Bukkit;
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

    // Shipped default, used only when the config key has been deleted outright.
    private static final List<String> DEFAULT_ITEM_LORE = List.of(
            "<dark_gray>━━━━━━━━━━━━━━━━━━━━━",
            "<gray> ▸ <white>Base:  <green>${base}",
            "<gray> ▸ <white>Mult:  <aqua>{multiplier}x",
            "<gray> ▸ <white>Price: <green>${price}",
            "<dark_gray>━━━━━━━━━━━━━━━━━━━━━",
            "<gray> Category: {category}"
    );

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
        String title = Text.legacy(Text.fill(
                cfg.getRawText("worth-gui.title", "<dark_gray><bold>Item Prices <gray>(Page {page})"),
                "page", page + 1));
        this.inv = Bukkit.createInventory(this, 54, title);
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
                List.of(cfg.getRawText("worth-gui.close-lore", "<gray>Close the menu.")));
        inv.setItem(SLOT_CLOSE, makeItem(
                cfg.getIconMaterial("topsell-close", Material.BARRIER),
                cfg.getIconName("topsell-close", "<red><bold>Close"),
                closeLore));

        // Previous page
        if (page > 0) {
            List<String> prevLore = cfg.getIconLore("prev-page",
                    List.of(cfg.getRawText("worth-gui.prev-page-lore", "<gray>Previous page.")));
            inv.setItem(SLOT_PREV, makeItem(
                    cfg.getIconMaterial("prev-page", Material.ARROW),
                    cfg.getIconName("prev-page", "<yellow>← Previous"),
                    prevLore));
        }

        // Filter button
        List<String> filterLore = buildFilterLore();
        String filterName = FILTER_ALL.equals(filter)
                ? cfg.getText("worth-gui.filter-all", "<yellow><bold>FILTER: <white>All")
                : Text.legacy(Text.fill(cfg.getRawText("worth-gui.filter-category", "<yellow><bold>FILTER: {category}"),
                        "category", cfg.getRawCategoryDisplayName(filter)));
        inv.setItem(SLOT_FILTER, makeItem(
                Material.HOPPER,
                filterName,
                filterLore));

        // Page indicator
        int totalPages = Math.max(1, (int) Math.ceil((double) itemKeys.size() / ITEMS_PER_PAGE));
        List<String> infoLore = List.of(Text.legacy(Text.fill(
                cfg.getRawText("worth-gui.total-items", "<gray>Total items: <white>{count}"),
                "count", itemKeys.size())));
        inv.setItem(SLOT_INFO, makeItem(
                cfg.getIconMaterial("page-indicator", Material.PAPER),
                Text.legacy(Text.fill(cfg.getRawText("worth-gui.page-indicator", "<white>Page {page} / {total}"),
                        "page", page + 1, "total", totalPages)),
                infoLore));

        // Next page
        if ((page + 1) * ITEMS_PER_PAGE < itemKeys.size()) {
            List<String> nextLore = cfg.getIconLore("next-page",
                    List.of(cfg.getRawText("worth-gui.next-page-lore", "<gray>Next page.")));
            inv.setItem(SLOT_NEXT, makeItem(
                    cfg.getIconMaterial("next-page", Material.ARROW),
                    cfg.getIconName("next-page", "<yellow>Next →"),
                    nextLore));
        }
    }

    private List<String> buildFilterLore() {
        ConfigManager cfg = plugin.getConfigManager();
        List<String> lore = new ArrayList<>();
        lore.add(cfg.getText("worth-gui.filter-hint", "<gray>Click to cycle filter."));
        lore.add("");

        String selected = cfg.getRawText("worth-gui.filter-option-selected", "<green> • {category}");
        String option = cfg.getRawText("worth-gui.filter-option", "<gray> • {category}");

        String allLabel = cfg.getRawText("worth-gui.filter-option-all", "All");
        lore.add(Text.legacy(Text.fill(FILTER_ALL.equals(filter) ? selected : option, "category", allLabel)));
        for (String cat : cfg.getCategoryOrder()) {
            lore.add(Text.legacy(Text.fill(
                    cat.equalsIgnoreCase(filter) ? selected : option,
                    "category", cfg.getRawCategoryDisplayName(cat))));
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
        for (String line : plugin.getConfigManager().getRawTextList("worth-gui.item-lore", DEFAULT_ITEM_LORE)) {
            lore.add(Text.legacy(Text.fill(line,
                    "item", ItemNameFormatter.formatKey(itemKey),
                    "base", NumberFormatter.format(base),
                    "multiplier", String.format("%.2f", effectiveMultiplier),
                    "price", NumberFormatter.format(effective),
                    "category", plugin.getConfigManager().getRawCategoryDisplayName(category))));
        }

        String displayName = Text.legacy(Text.fill(
                plugin.getConfigManager().getRawText("worth-gui.item-name", "<white>{item}"),
                "item", ItemNameFormatter.formatKey(itemKey)));

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
            if (mat == null || mat.isAir() || !mat.isItem()) return new ItemStack(Material.BARRIER);

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
        if (mat == null || mat.isAir() || !mat.isItem()) return new ItemStack(Material.BARRIER);
        return new ItemStack(mat);
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
