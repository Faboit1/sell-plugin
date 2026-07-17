package com.yourname.sellplugin.gui;

import com.yourname.sellplugin.SellPlugin;
import com.yourname.sellplugin.manager.ConfigManager;
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

import java.util.*;

/**
 * Main 9×6 sell GUI.
 * Rows 0-4 (slots 0-44): empty area – players can place items here to sell.
 * Row 5 (slot 53):        Sell button (lime glass pane, bottom-right).
 *
 * When the GUI is closed, every sellable item left in the placement area is sold
 * automatically and non-sellable items are returned to the player.
 * Clicking the Sell button also triggers selling all items.
 */
public class ShopMainGUI implements InventoryHolder {

    private static final int ROWS = 6;
    private static final int SIZE = ROWS * 9; // 54

    /** First slot of the protected bottom row (Sell button row). */
    public static final int BOTTOM_ROW_START = 45;

    /** The Sell button slot (bottom-right corner). */
    public static final int SLOT_SELL_BUTTON = 53;

    /** Number of item placement slots (rows 0-4). */
    public static final int ITEM_AREA_END = 45;

    private final Inventory inv;
    private final SellPlugin plugin;
    private final Player player;

    public ShopMainGUI(SellPlugin plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
        // Title in small caps (configurable via messages.shop.title)
        String title = ChatColor.DARK_GRAY + "" + ChatColor.BOLD
                + SmallCaps.convert(plugin.getConfigManager().getText("shop.title", "put items here to sell"));
        this.inv = Bukkit.createInventory(this, SIZE, title);
        populate();
    }

    private void populate() {
        ConfigManager cfg = plugin.getConfigManager();

        // Rows 0-4 (slots 0-44) are left EMPTY for item placement.

        // Fill bottom row with filler glass
        Material fillerMat = cfg.getFillerBlock();
        ItemStack bg = makeItem(fillerMat, " ", Collections.emptyList());
        for (int slot = BOTTOM_ROW_START; slot < SIZE; slot++) inv.setItem(slot, bg);

        // Sell button in bottom-right corner (slot 53)
        inv.setItem(SLOT_SELL_BUTTON, buildSellButton());
    }

    /**
     * Rebuild the sell button with updated value preview.
     * Call this to refresh the hover text showing sell value.
     */
    public void refreshSellButton() {
        inv.setItem(SLOT_SELL_BUTTON, buildSellButton());
    }

    private ItemStack buildSellButton() {
        ConfigManager cfg = plugin.getConfigManager();

        // Calculate the value of items currently in the GUI
        double totalValue = calculateGuiItemsValue();

        String separator = cfg.getText("lore-separator", "&8━━━━━━━━━━━━━━━━━━━");

        List<String> lore = new ArrayList<>();
        lore.add(separator);
        if (totalValue > 0) {
            lore.add(ChatColor.GRAY + " ▸ " + SmallCaps.convert(cfg.getText("shop.sell-value-label", "value: "))
                    + ChatColor.GREEN + "$" + NumberFormatter.format(totalValue));
        } else {
            lore.add(ChatColor.GRAY + " ▸ " + SmallCaps.convert(cfg.getText("shop.sell-empty", "no sellable items")));
        }
        lore.add(separator);
        lore.add(ChatColor.YELLOW + " ✦ " + SmallCaps.convert(cfg.getText("shop.sell-click", "click to sell all items!")));

        String sellButtonName = cfg.getText("shop.sell-button-name", "&a&lSell");

        Material sellMat = cfg.getIconMaterial("sell-button", Material.LIME_STAINED_GLASS_PANE);
        return makeItem(sellMat, sellButtonName, lore);
    }

    /**
     * Calculate the total sell value of all items currently placed in the GUI.
     */
    public double calculateGuiItemsValue() {
        double totalValue = 0.0;
        for (int i = 0; i < ITEM_AREA_END; i++) {
            ItemStack item = inv.getItem(i);
            if (item == null || item.getType() == Material.AIR) continue;
            totalValue += plugin.getSellManager().calculateItemWorth(player, item);
        }
        return totalValue;
    }

    private ItemStack makeItem(Material mat, String name, List<String> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
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

    public SellPlugin getPlugin() {
        return plugin;
    }

    public Player getPlayer() {
        return player;
    }
}
