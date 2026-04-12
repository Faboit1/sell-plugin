package com.yourname.sellplugin.gui;

import com.yourname.sellplugin.SellPlugin;
import com.yourname.sellplugin.manager.ConfigManager;
import com.yourname.sellplugin.manager.PriceManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * Paginated item-list GUI – 9×6 (54 slots).
 *
 * Rows 1-5 (slots 0-44): item display area (up to 45 items per page).
 * Row 6 (slots 45-53):   navigation bar.
 *   45 – Back (return to CategoryProgressGUI)
 *   46 – Previous page
 *   49 – Page indicator
 *   52 – Next page
 *   53 – Sell All in category
 */
public class CategoryItemsGUI implements InventoryHolder {

    private static final int ITEMS_PER_PAGE = 45;

    // Navigation slots
    public static final int SLOT_BACK     = 45;
    public static final int SLOT_PREV     = 46;
    public static final int SLOT_INFO     = 49;
    public static final int SLOT_NEXT     = 52;
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

        // Background for navigation row
        ItemStack bg = makeItem(Material.BLACK_STAINED_GLASS_PANE, " ", Collections.emptyList());
        for (int i = 45; i < 54; i++) inv.setItem(i, bg);

        // Items area
        int start = page * ITEMS_PER_PAGE;
        int end   = Math.min(start + ITEMS_PER_PAGE, itemKeys.size());
        for (int i = start; i < end; i++) {
            int slot = i - start;
            inv.setItem(slot, buildItemDisplay(itemKeys.get(i)));
        }
        // Fill remaining item area with gray glass
        ItemStack filler = makeItem(Material.GRAY_STAINED_GLASS_PANE, " ", Collections.emptyList());
        for (int i = (end - start); i < 45; i++) inv.setItem(i, filler);

        // Navigation buttons
        List<String> backLore = Collections.singletonList(ChatColor.GRAY + "Return to category view.");
        inv.setItem(SLOT_BACK, makeItem(Material.ARROW,
                ChatColor.RED + "" + ChatColor.BOLD + "Back", backLore));

        if (page > 0) {
            List<String> prevLore = Collections.singletonList(ChatColor.GRAY + "Previous page.");
            inv.setItem(SLOT_PREV, makeItem(Material.ARROW,
                    ChatColor.YELLOW + "← Previous Page", prevLore));
        }

        int totalPages = Math.max(1, (int) Math.ceil((double) itemKeys.size() / ITEMS_PER_PAGE));
        List<String> infoLore = Collections.singletonList(
                ChatColor.GRAY + "Total items: " + itemKeys.size());
        inv.setItem(SLOT_INFO, makeItem(Material.PAPER,
                ChatColor.WHITE + "Page " + (page + 1) + "/" + totalPages, infoLore));

        if ((page + 1) * ITEMS_PER_PAGE < itemKeys.size()) {
            List<String> nextLore = Collections.singletonList(ChatColor.GRAY + "Next page.");
            inv.setItem(SLOT_NEXT, makeItem(Material.ARROW,
                    ChatColor.YELLOW + "Next Page →", nextLore));
        }

        double catValue = plugin.getSellManager().calculateCategoryValue(player, categoryId);
        int catCount    = plugin.getSellManager().countCategoryItems(player, categoryId);
        List<String> sellLore = new ArrayList<>();
        sellLore.add(ChatColor.GRAY + "Sell all " + ChatColor.WHITE + categoryId
                + ChatColor.GRAY + " items from inventory.");
        if (catCount > 0) {
            sellLore.add(ChatColor.GREEN + "You will earn: $" + String.format("%.2f", catValue));
        } else {
            sellLore.add(ChatColor.RED + "No items to sell.");
        }
        inv.setItem(SLOT_SELL_ALL, makeItem(Material.GOLD_INGOT,
                ChatColor.GREEN + "" + ChatColor.BOLD + "Sell Category", sellLore));
    }

    // ── Build a display ItemStack for a price-list entry ─────────────────────

    private ItemStack buildItemDisplay(String itemKey) {
        PriceManager pm = plugin.getPriceManager();
        double base = pm.getPrice(itemKey);
        double mult = plugin.getMultiplierManager().getMultiplier(player, categoryId);
        double effective = base * mult;

        // Resolve material (handle "MAT:POTIONTYPE" keys)
        Material mat = resolveMaterial(itemKey);
        if (mat == null) mat = Material.BARRIER;

        // Count how many the player holds
        int playerHas = countInInventory(itemKey);

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.DARK_GRAY + "─────────────────────");
        lore.add(ChatColor.GRAY + "Base price:   " + ChatColor.GOLD + "$" + String.format("%.2f", base));
        lore.add(ChatColor.GRAY + "Multiplier:   " + ChatColor.AQUA + String.format("%.2fx", mult));
        lore.add(ChatColor.GRAY + "Sell price:   " + ChatColor.GREEN + "$" + String.format("%.2f", effective));
        lore.add(ChatColor.DARK_GRAY + "─────────────────────");
        lore.add(ChatColor.GRAY + "You have: " + ChatColor.WHITE + playerHas);
        if (playerHas > 0) {
            lore.add(ChatColor.YELLOW + "Click to sell all " + playerHas + "x");
        }

        String displayName = ChatColor.WHITE + formatItemName(itemKey);
        return makeItem(mat, displayName, lore);
    }

    private Material resolveMaterial(String itemKey) {
        // Keys can be "MATERIAL" or "MATERIAL:POTIONTYPE"
        String base = itemKey.contains(":") ? itemKey.split(":")[0] : itemKey;
        return Material.matchMaterial(base);
    }

    private int countInInventory(String itemKey) {
        int total = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null || item.getType() == Material.AIR) continue;
            String key = plugin.getPriceManager().getItemKey(item);
            if (itemKey.equalsIgnoreCase(key)) total += item.getAmount();
        }
        return total;
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
