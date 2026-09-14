package com.yourname.sellplugin.gui;

import com.yourname.sellplugin.SellPlugin;
import com.yourname.sellplugin.integration.OrderQuoteLedger;
import com.yourname.sellplugin.manager.ConfigManager;
import com.yourname.sellplugin.manager.SellManager;
import com.yourname.sellplugin.util.NumberFormatter;
import com.yourname.sellplugin.util.Text;
import org.bukkit.Bukkit;
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

    // Shipped defaults, used only when a config key has been deleted outright.
    private static final String DEFAULT_TITLE = "<dark_gray><bold>Put items here to sell";
    private static final String DEFAULT_BUTTON_NAME = "<green><bold>Sell";
    private static final List<String> DEFAULT_VALUE_LORE = List.of(
            "<dark_gray>\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501",
            "<gray> \u25b8 Value: <green>{marker}${value}",
            "<dark_gray>\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501",
            "<yellow> \u2726 Click to sell all items!"
    );
    private static final List<String> DEFAULT_EMPTY_LORE = List.of(
            "<dark_gray>\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501",
            "<gray> \u25b8 No sellable items",
            "<dark_gray>\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501",
            "<yellow> \u2726 Click to sell all items!"
    );

    private final Inventory inv;
    private final SellPlugin plugin;
    private final Player player;

    public ShopMainGUI(SellPlugin plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
        String title = plugin.getConfigManager().getText("shop.title", DEFAULT_TITLE);
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

    /**
     * The Sell button, whose every line comes from config.
     *
     * <p>Two lore lists are kept rather than one with conditional lines: what a
     * server wants to say about an empty GUI rarely resembles what it says
     * about a full one, and a single list would have meant inventing rules
     * about which lines to hide.
     */
    private ItemStack buildSellButton() {
        ConfigManager cfg = plugin.getConfigManager();

        // What the items in the GUI are actually worth, open orders included, so
        // the button agrees with what clicking it pays out.
        SellManager.SaleTally tally = valueGuiItems();
        double totalValue = tally.totalEarned();
        boolean empty = totalValue <= 0;

        List<String> template = empty
                ? cfg.getRawTextList("shop.sell-button-lore-empty", DEFAULT_EMPTY_LORE)
                : cfg.getRawTextList("shop.sell-button-lore", DEFAULT_VALUE_LORE);

        List<String> lore = new ArrayList<>(template.size());
        for (String line : template) {
            lore.add(Text.legacy(Text.fill(line,
                    "value", NumberFormatter.format(totalValue),
                    "marker", tally.usedOrders() ? cfg.getOrderEstimateMarker() : "",
                    "items", NumberFormatter.format(tally.items))));
        }

        return makeItem(
                cfg.getIconMaterial("sell-button", Material.LIME_STAINED_GLASS_PANE),
                cfg.getText("shop.sell-button-name", DEFAULT_BUTTON_NAME),
                lore);
    }

    /**
     * What everything currently placed in the GUI would fetch. One ledger spans
     * the whole area so an order that several stacks match is only counted once.
     */
    public SellManager.SaleTally valueGuiItems() {
        SellManager.SaleTally tally = new SellManager.SaleTally();
        OrderQuoteLedger ledger = plugin.getSellManager().newLedger(player);
        for (int i = 0; i < ITEM_AREA_END; i++) {
            ItemStack item = inv.getItem(i);
            if (item == null || item.getType() == Material.AIR) continue;
            tally.add(plugin.getSellManager().evaluateItemWorth(player, item, ledger));
        }
        return tally;
    }

    /** Total sell value of everything placed in the GUI, orders included. */
    public double calculateGuiItemsValue() {
        return valueGuiItems().totalEarned();
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
