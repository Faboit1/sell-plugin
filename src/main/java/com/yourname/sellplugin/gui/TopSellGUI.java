package com.yourname.sellplugin.gui;

import com.yourname.sellplugin.SellPlugin;
import com.yourname.sellplugin.manager.ConfigManager;
import com.yourname.sellplugin.manager.MultiplierManager.LeaderboardEntry;
import com.yourname.sellplugin.util.SmallCaps;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Top-sellers leaderboard GUI – full double-chest (54 slots).
 *
 * Rows 0-4 (slots 0-44): up to 36 player entries per page (4 rows × 9).
 * Row 5  (slots 45-53):  navigation bar.
 *   45 – Close
 *   48 – Previous page  (directly left of page indicator)
 *   49 – Page indicator
 *   50 – Next page      (directly right of page indicator)
 *
 * Each entry is a player-head ItemStack with:
 *   - Display name: rank + player name
 *   - Lore: total earnings
 *
 * Player head skins are set via SkullMeta#setOwningPlayer(OfflinePlayer).
 * The Minecraft client resolves and caches the actual texture, so the server
 * itself does not directly call the Mojang API per-request.
 * To further protect against any server-side profile lookups, heads are
 * scheduled with a 2-tick delay between each other.
 */
public class TopSellGUI implements InventoryHolder {

    private static final int SIZE          = 54;
    private static final int ENTRIES_PER_PAGE = 36; // rows 0-3 (4 × 9)

    public static final int SLOT_CLOSE = 45;
    public static final int SLOT_PREV  = 48;
    public static final int SLOT_INFO  = 49;
    public static final int SLOT_NEXT  = 50;

    private final Inventory inv;
    private final SellPlugin plugin;
    private final Player viewer;
    private final List<LeaderboardEntry> entries;
    private final int page; // 0-based

    public TopSellGUI(SellPlugin plugin, Player viewer, int page) {
        this.plugin  = plugin;
        this.viewer  = viewer;
        this.entries = plugin.getMultiplierManager().getLeaderboard();
        this.page    = page;

        ConfigManager cfg = plugin.getConfigManager();
        String title = ChatColor.DARK_GRAY + "" + ChatColor.BOLD
                + SmallCaps.convert("top sellers");
        this.inv = Bukkit.createInventory(this, SIZE, title);
        populate();
    }

    // ── Layout ───────────────────────────────────────────────────────────────

    private void populate() {
        ConfigManager cfg = plugin.getConfigManager();

        // Fill entire GUI with filler
        Material fillerMat = cfg.getFillerBlock();
        ItemStack bg = makeItem(fillerMat, " ", Collections.emptyList());
        for (int i = 0; i < SIZE; i++) inv.setItem(i, bg);

        // ── Player entries (rows 0-3) ────────────────────────────────────────
        int start = page * ENTRIES_PER_PAGE;
        int end   = Math.min(start + ENTRIES_PER_PAGE, entries.size());

        for (int i = start; i < end; i++) {
            int slot = i - start;
            LeaderboardEntry entry = entries.get(i);
            int rank = i + 1;
            // Schedule each skull with a small staggered delay to avoid any
            // potential server-side profile look-up spikes (2 ticks apart).
            final int finalSlot = slot;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (viewer.isOnline()) {
                    inv.setItem(finalSlot, buildEntryHead(entry, rank));
                }
            }, (long) (slot) * 2L);
        }

        // ── Navigation bar (row 5) ──────────────────────────────────────────

        // Close button
        List<String> closeLore = cfg.getIconLore("topsell-close",
                Collections.singletonList(ChatColor.GRAY + SmallCaps.convert("close the leaderboard.")));
        inv.setItem(SLOT_CLOSE, makeItem(
                cfg.getIconMaterial("topsell-close", Material.BARRIER),
                cfg.getIconName("topsell-close", "&c&l" + SmallCaps.convert("close")),
                closeLore));

        // Previous page
        if (page > 0) {
            List<String> prevLore = cfg.getIconLore("prev-page",
                    Collections.singletonList(ChatColor.GRAY + SmallCaps.convert("previous page.")));
            inv.setItem(SLOT_PREV, makeItem(
                    cfg.getIconMaterial("prev-page", Material.ARROW),
                    cfg.getIconName("prev-page", "&e← " + SmallCaps.convert("previous")),
                    prevLore));
        }

        // Page indicator
        int totalPages = Math.max(1, (int) Math.ceil((double) entries.size() / ENTRIES_PER_PAGE));
        List<String> infoLore = Collections.singletonList(
                ChatColor.GRAY + SmallCaps.convert("total players: ") + entries.size());
        inv.setItem(SLOT_INFO, makeItem(
                cfg.getIconMaterial("page-indicator", Material.PAPER),
                ChatColor.WHITE + SmallCaps.convert("page ") + (page + 1) + " / " + totalPages,
                infoLore));

        // Next page
        if ((page + 1) * ENTRIES_PER_PAGE < entries.size()) {
            List<String> nextLore = cfg.getIconLore("next-page",
                    Collections.singletonList(ChatColor.GRAY + SmallCaps.convert("next page.")));
            inv.setItem(SLOT_NEXT, makeItem(
                    cfg.getIconMaterial("next-page", Material.ARROW),
                    cfg.getIconName("next-page", "&e" + SmallCaps.convert("next") + " →"),
                    nextLore));
        }
    }

    // ── Entry head builder ───────────────────────────────────────────────────

    private ItemStack buildEntryHead(LeaderboardEntry entry, int rank) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta  = (SkullMeta) skull.getItemMeta();
        if (meta == null) return skull;

        // Set skin (uses server's cached profile data; client fetches texture)
        OfflinePlayer op = Bukkit.getOfflinePlayer(entry.uuid);
        meta.setOwningPlayer(op);

        // Display name: rank + player name
        ChatColor rankColour = rankColour(rank);
        meta.setDisplayName(rankColour + "#" + rank + " " + ChatColor.WHITE + entry.name);

        // Lore: total earnings
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.DARK_GRAY + "───────────────────");
        lore.add(ChatColor.GRAY + SmallCaps.convert("total earned: ")
                + ChatColor.GREEN + "$" + String.format("%.2f", entry.totalEarnings));
        lore.add(ChatColor.DARK_GRAY + "───────────────────");
        meta.setLore(lore);

        skull.setItemMeta(meta);
        return skull;
    }

    private ChatColor rankColour(int rank) {
        if (rank == 1) return ChatColor.GOLD;
        if (rank == 2) return ChatColor.GRAY;
        if (rank == 3) return ChatColor.DARK_RED;
        return ChatColor.WHITE;
    }

    // ── Navigation helpers ───────────────────────────────────────────────────

    public boolean hasPrevPage() { return page > 0; }

    public boolean hasNextPage() {
        return (page + 1) * ENTRIES_PER_PAGE < entries.size();
    }

    public TopSellGUI prevPage() {
        return new TopSellGUI(plugin, viewer, page - 1);
    }

    public TopSellGUI nextPage() {
        return new TopSellGUI(plugin, viewer, page + 1);
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
}
