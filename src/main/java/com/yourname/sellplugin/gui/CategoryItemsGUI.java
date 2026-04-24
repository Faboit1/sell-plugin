package com.yourname.sellplugin.gui;

import com.yourname.sellplugin.SellPlugin;
import com.yourname.sellplugin.manager.ConfigManager;
import com.yourname.sellplugin.manager.PriceManager;
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
        String title = cfg.getCategoryDisplayName(categoryId)
                + ChatColor.DARK_GRAY + " – Items";
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
        List<String> backLore = cfg.getIconLore("back",
                Collections.singletonList(ChatColor.GRAY + "Return to category view."));
        inv.setItem(SLOT_BACK, makeItem(
                cfg.getIconMaterial("back", Material.ARROW),
                cfg.getIconName("back", "&c&lBack"),
                backLore));

        // ── Previous page ──────────────────────────────────────────────────
        if (page > 0) {
            List<String> prevLore = cfg.getIconLore("prev-page",
                    Collections.singletonList(ChatColor.GRAY + "Previous page."));
            inv.setItem(SLOT_PREV, makeItem(
                    cfg.getIconMaterial("prev-page", Material.ARROW),
                    cfg.getIconName("prev-page", "&e← Previous"),
                    prevLore));
        }

        // ── Page indicator ─────────────────────────────────────────────────
        int totalPages = Math.max(1, (int) Math.ceil((double) itemKeys.size() / ITEMS_PER_PAGE));
        List<String> infoLore = Collections.singletonList(
                ChatColor.GRAY + "Total items: " + itemKeys.size());
        inv.setItem(SLOT_INFO, makeItem(
                cfg.getIconMaterial("page-indicator", Material.PAPER),
                ChatColor.WHITE + "Page " + (page + 1) + " / " + totalPages,
                infoLore));

        // ── Next page ──────────────────────────────────────────────────────
        if ((page + 1) * ITEMS_PER_PAGE < itemKeys.size()) {
            List<String> nextLore = cfg.getIconLore("next-page",
                    Collections.singletonList(ChatColor.GRAY + "Next page."));
            inv.setItem(SLOT_NEXT, makeItem(
                    cfg.getIconMaterial("next-page", Material.ARROW),
                    cfg.getIconName("next-page", "&eNext →"),
                    nextLore));
        }

        // ── Sell-All button ────────────────────────────────────────────────
        double catValue = plugin.getSellManager().calculateCategoryValue(player, categoryId);
        int catCount    = plugin.getSellManager().countCategoryItems(player, categoryId);
        List<String> sellLore = new ArrayList<>();
        sellLore.add(ChatColor.GRAY + " ▸ " + SmallCaps.convert("category: ")
                + ChatColor.WHITE + categoryId);
        if (catCount > 0) {
            sellLore.add(ChatColor.GRAY + " ▸ " + SmallCaps.convert("items: ")
                    + ChatColor.WHITE + NumberFormatter.format(catCount));
            sellLore.add(ChatColor.GRAY + " ▸ " + SmallCaps.convert("earn: ")
                    + ChatColor.GREEN + "$" + NumberFormatter.format(catValue));
        } else {
            sellLore.add(ChatColor.RED + " ▸ " + SmallCaps.convert("no items to sell."));
        }
        inv.setItem(SLOT_SELL_ALL, makeItem(
                cfg.getIconMaterial("sell-category", Material.GOLD_INGOT),
                cfg.getIconName("sell-category", "&a&lSell Category"),
                sellLore));
    }

    // ── Build a display ItemStack for a price-list entry ─────────────────────

    private ItemStack buildItemDisplay(String itemKey) {
        PriceManager pm = plugin.getPriceManager();
        double base = pm.getPrice(itemKey);
        double earned = plugin.getMultiplierManager().getMultiplier(player, categoryId);
        double daily  = plugin.getDailyBonusManager().getDailyBonus(categoryId);
        double mult   = earned + daily;
        double effective = base * mult;

        // Build correct ItemStack (handles potions with PotionMeta)
        ItemStack item = resolveItemStack(itemKey);

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.DARK_GRAY + "━━━━━━━━━━━━━━━━━━━━━");
        lore.add(ChatColor.GRAY + " ▸ " + ChatColor.WHITE + "Base:  "
                + ChatColor.GREEN + "$" + NumberFormatter.format(base));
        if (daily > 0) {
            lore.add(ChatColor.GRAY + " ▸ " + ChatColor.WHITE + "Mult:  "
                    + ChatColor.AQUA + String.format("%.2f", earned) + "x"
                    + ChatColor.GOLD + " (+" + String.format("%.2f", daily) + "x today)");
        } else {
            lore.add(ChatColor.GRAY + " ▸ " + ChatColor.WHITE + "Mult:  "
                    + ChatColor.AQUA + String.format("%.2fx", mult));
        }
        lore.add(ChatColor.GRAY + " ▸ " + ChatColor.WHITE + "Price: "
                + ChatColor.GREEN + "$" + NumberFormatter.format(effective));
        lore.add(ChatColor.DARK_GRAY + "━━━━━━━━━━━━━━━━━━━━━");

        String displayName = ChatColor.WHITE + formatItemName(itemKey);

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
            if (mat == null) return new ItemStack(Material.BARRIER);

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
        return new ItemStack(mat != null ? mat : Material.BARRIER);
    }

    private String formatItemName(String key) {
        return key.replace("_", " ").replace(":", " – ");
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

