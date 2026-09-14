package com.yourname.sellplugin.gui;

import com.yourname.sellplugin.SellPlugin;
import com.yourname.sellplugin.manager.SellManager;
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
 * Paginated item-list GUI – 9×6 (54 slots).
 *
 * Rows 1-5 (slots 0-44): item display area (up to 45 items per page).
 * Row 6 (slots 45-53):   navigation bar.
 *   45 – Back (return to CategoryProgressGUI)
 *   48 – Previous page  (directly left of page indicator)
 *   49 – Page indicator (paper)
 *   50 – Next page      (directly right of page indicator)
 *   53 – Sell All in category
 */
public class CategoryItemsGUI implements InventoryHolder {

    private static final int ITEMS_PER_PAGE = 45;

    // Navigation slots
    public static final int SLOT_BACK     = 45;
    public static final int SLOT_PREV     = 48;
    public static final int SLOT_INFO     = 49;
    public static final int SLOT_NEXT     = 50;
    public static final int SLOT_SELL_ALL = 53;

    // Shipped defaults, used only when a config key has been deleted outright.
    private static final List<String> DEFAULT_SELL_LORE = List.of(
            "<gray> ▸ Category: {category}",
            "<gray> ▸ Items: <white>{items}",
            "<gray> ▸ Earn: <green>{marker}${value}"
    );
    private static final List<String> DEFAULT_SELL_LORE_EMPTY = List.of(
            "<gray> ▸ Category: {category}",
            "<red> ▸ No items to sell."
    );
    private static final List<String> DEFAULT_ITEM_LORE = List.of(
            "<dark_gray>━━━━━━━━━━━━━━━━━━━━━",
            "<gray> ▸ <white>Base:  <green>${base}",
            "<gray> ▸ <white>Mult:  <aqua>{multiplier}x",
            "<gray> ▸ <white>Price: <green>${price}",
            "<dark_gray>━━━━━━━━━━━━━━━━━━━━━"
    );

    private final Inventory inv;
    private final SellPlugin plugin;
    private final Player player;
    private final String categoryId;

    /** Ordered list of all item keys in this category that have a price. */
    private final List<String> itemKeys;
    private int page; // 0-based

    public CategoryItemsGUI(SellPlugin plugin, Player player, String categoryId, int page) {
        this.plugin = plugin;
        this.player = player;
        this.categoryId = categoryId;
        this.page = page;
        this.itemKeys = buildItemKeyList();

        ConfigManager cfg = plugin.getConfigManager();
        String title = Text.legacy(Text.fill(
                cfg.getRawText("category-items.title", "{category}<dark_gray> – Items"),
                "category", cfg.getRawCategoryDisplayName(categoryId)));
        this.inv = Bukkit.createInventory(this, 54, title);
        populate();
    }

    // ── Build the sorted list of all item keys in this category ─────────────

    private List<String> buildItemKeyList() {
        PriceManager pm = plugin.getPriceManager();
        List<String> keys = new ArrayList<>();
        for (String key : pm.getAllItemKeys()) {
            if (categoryId.equalsIgnoreCase(pm.getCategory(key))) {
                keys.add(key);
            }
        }
        Collections.sort(keys);
        return keys;
    }

    // ── Populate inventory ───────────────────────────────────────────────────

    private void populate() {
        inv.clear();
        ConfigManager cfg = plugin.getConfigManager();

        // Background for navigation row
        Material fillerMat = cfg.getFillerBlock();
        ItemStack bg = makeItem(fillerMat, " ", Collections.emptyList());
        for (int i = 45; i < 54; i++) inv.setItem(i, bg);

        // Items area
        int start = page * ITEMS_PER_PAGE;
        int end   = Math.min(start + ITEMS_PER_PAGE, itemKeys.size());
        for (int i = start; i < end; i++) {
            inv.setItem(i - start, buildItemDisplay(itemKeys.get(i)));
        }
        // Fill remaining item area with gray glass
        ItemStack filler = makeItem(Material.GRAY_STAINED_GLASS_PANE, " ", Collections.emptyList());
        for (int i = (end - start); i < 45; i++) inv.setItem(i, filler);

        // ── Back button ────────────────────────────────────────────────────
        List<String> backLore = cfg.getIconLore("back", List.of("<gray>Return to category view."));
        inv.setItem(SLOT_BACK, makeItem(
                cfg.getIconMaterial("back", Material.ARROW),
                cfg.getIconName("back", "<red><bold>Back"),
                backLore));

        // ── Previous page ──────────────────────────────────────────────────
        if (page > 0) {
            List<String> prevLore = cfg.getIconLore("prev-page", List.of("<gray>Previous page."));
            inv.setItem(SLOT_PREV, makeItem(
                    cfg.getIconMaterial("prev-page", Material.ARROW),
                    cfg.getIconName("prev-page", "<yellow>← Previous"),
                    prevLore));
        }

        // ── Page indicator ─────────────────────────────────────────────────
        int totalPages = Math.max(1, (int) Math.ceil((double) itemKeys.size() / ITEMS_PER_PAGE));
        List<String> infoLore = Collections.singletonList(
                cfg.getText("category-items.total-items", "<gray>Total items: {count}")
                        .replace("{count}", String.valueOf(itemKeys.size())));
        inv.setItem(SLOT_INFO, makeItem(
                cfg.getIconMaterial("page-indicator", Material.PAPER),
                cfg.getText("category-items.page-indicator", "<white>Page {page} / {total}")
                        .replace("{page}", String.valueOf(page + 1))
                        .replace("{total}", String.valueOf(totalPages)),
                infoLore));

        // ── Next page ──────────────────────────────────────────────────────
        if ((page + 1) * ITEMS_PER_PAGE < itemKeys.size()) {
            List<String> nextLore = cfg.getIconLore("next-page", List.of("<gray>Next page."));
            inv.setItem(SLOT_NEXT, makeItem(
                    cfg.getIconMaterial("next-page", Material.ARROW),
                    cfg.getIconName("next-page", "<yellow>Next →"),
                    nextLore));
        }

        // ── Sell-All button ────────────────────────────────────────────────
        SellManager.SellPreview catPreview = plugin.getSellManager().previewCategory(player, categoryId);
        double catValue = catPreview.value;
        int catCount    = plugin.getSellManager().countCategoryItems(player, categoryId);
        // Marks the figure as an estimate when open orders make up part of it.
        String catMarker = catPreview.includesOrders ? cfg.getOrderEstimateMarker() : "";
        List<String> template = catCount > 0
                ? cfg.getRawTextList("category-items.sell-lore", DEFAULT_SELL_LORE)
                : cfg.getRawTextList("category-items.sell-lore-empty", DEFAULT_SELL_LORE_EMPTY);
        List<String> sellLore = new ArrayList<>(template.size());
        for (String line : template) {
            sellLore.add(Text.legacy(Text.fill(line,
                    "category", cfg.getRawCategoryDisplayName(categoryId),
                    "items", NumberFormatter.format(catCount),
                    "value", NumberFormatter.format(catValue),
                    "marker", catMarker)));
        }
        inv.setItem(SLOT_SELL_ALL, makeItem(
                cfg.getIconMaterial("sell-category", Material.GOLD_INGOT),
                cfg.getIconName("sell-category", "<green><bold>Sell Category"),
                sellLore));
    }

    // ── Build a display ItemStack for a price-list entry ─────────────────────

    private ItemStack buildItemDisplay(String itemKey) {
        PriceManager pm = plugin.getPriceManager();
        double base = pm.getPrice(itemKey);
        String itemCategory = pm.getCategory(itemKey);
        double effectiveMultiplier = plugin.getMultiplierManager().getEffectiveMultiplier(player, itemCategory);
        double effective = base * effectiveMultiplier;

        // Build correct ItemStack (handles potions with PotionMeta)
        ItemStack item = resolveItemStack(itemKey);

        List<String> lore = new ArrayList<>();
        for (String line : plugin.getConfigManager().getRawTextList("category-items.item-lore", DEFAULT_ITEM_LORE)) {
            lore.add(Text.legacy(Text.fill(line,
                    "item", ItemNameFormatter.formatKey(itemKey),
                    "base", NumberFormatter.format(base),
                    "multiplier", String.format("%.2f", effectiveMultiplier),
                    "price", NumberFormatter.format(effective))));
        }

        String displayName = Text.legacy(Text.fill(
                plugin.getConfigManager().getRawText("category-items.item-name", "<white>{item}"),
                "item", ItemNameFormatter.formatKey(itemKey)));

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(displayName);
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Creates an ItemStack for the given item key.
     * For potion keys (e.g. "POTION:NIGHT_VISION") the correct PotionMeta
     * is applied so the correct potion colour is shown in the GUI.
     */
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
                } catch (IllegalArgumentException ignored) {
                    // Unknown potion type – leave meta as-is
                }
            }
            return item;
        }
        Material mat = Material.matchMaterial(itemKey);
        if (mat == null || mat.isAir() || !mat.isItem()) return new ItemStack(Material.BARRIER);
        return new ItemStack(mat);
    }

    // ── Item clicked ─────────────────────────────────────────────────────────

    /**
     * Returns the item key at the given slot (0-44), or null if none.
     */
    public String getItemKeyAtSlot(int slot) {
        if (slot < 0 || slot >= 45) return null;
        int idx = page * ITEMS_PER_PAGE + slot;
        if (idx < itemKeys.size()) return itemKeys.get(idx);
        return null;
    }

    // ── Navigation ───────────────────────────────────────────────────────────

    public boolean hasPrevPage() { return page > 0; }

    public boolean hasNextPage() {
        return (page + 1) * ITEMS_PER_PAGE < itemKeys.size();
    }

    public CategoryItemsGUI prevPage() {
        return new CategoryItemsGUI(plugin, player, categoryId, page - 1);
    }

    public CategoryItemsGUI nextPage() {
        return new CategoryItemsGUI(plugin, player, categoryId, page + 1);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

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

    public int getPage() {
        return page;
    }
}
