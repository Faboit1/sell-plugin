package com.yourname.sellplugin.gui;

import com.yourname.sellplugin.SellPlugin;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Confirm/Cancel GUI for the "sell all" action, opened straight from /sellall.
 * 27-slot (3 rows) GUI with Confirm and Cancel buttons.
 */
public class ConfirmSellAllGUI implements InventoryHolder {

    private static final int SIZE = 27;

    public static final int SLOT_CONFIRM = 11;
    public static final int SLOT_CANCEL  = 15;
    public static final int SLOT_INFO    = 13;

    // Shipped defaults, used only when a config key has been deleted outright.
    private static final String DEFAULT_TITLE = "<dark_gray><bold>Confirm sell all";
    private static final String DEFAULT_INFO_NAME = "<white><bold>Sell all";
    private static final List<String> DEFAULT_INFO_LORE = List.of(
            "<dark_gray>━━━━━━━━━━━━━━━━━━━",
            "<gray> ▸ Items: <white>{items}",
            "<gray> ▸ Value: <green>{marker}${value}",
            "<dark_gray>━━━━━━━━━━━━━━━━━━━"
    );
    private static final List<String> DEFAULT_CONFIRM_LORE = List.of(
            "<gray> ▸ Sell all items from your inventory.",
            "<green> ▸ You will earn: {marker}${value}"
    );
    private static final List<String> DEFAULT_CANCEL_LORE = List.of(
            "<gray> ▸ Go back without selling."
    );

    private final Inventory inv;

    public ConfirmSellAllGUI(SellPlugin plugin, Player player) {
        ConfigManager cfg = plugin.getConfigManager();
        this.inv = Bukkit.createInventory(this, SIZE, cfg.getText("confirm-sell-all.title", DEFAULT_TITLE));
        populate(plugin, cfg, player);
    }

    private void populate(SellPlugin plugin, ConfigManager cfg, Player player) {
        // Background
        ItemStack bg = makeItem(cfg.getFillerBlock(), " ", Collections.emptyList());
        for (int i = 0; i < SIZE; i++) inv.setItem(i, bg);

        SellManager.SellPreview preview = plugin.getSellManager().previewSellAll(player);
        // Marks the figure as an estimate when open orders make up part of it.
        String marker = preview.includesOrders ? cfg.getOrderEstimateMarker() : "";
        Object[] placeholders = {
                "items", NumberFormatter.format(preview.itemCount),
                "value", NumberFormatter.format(preview.value),
                "marker", marker
        };

        inv.setItem(SLOT_INFO, makeItem(
                cfg.getIconMaterial("sell-all-info", Material.CHEST),
                cfg.getText("confirm-sell-all.info-name", DEFAULT_INFO_NAME),
                render(cfg.getRawTextList("confirm-sell-all.info-lore", DEFAULT_INFO_LORE), placeholders)));

        // The "you will earn" line only makes sense with something to sell, so
        // any line mentioning the value drops out of an empty confirmation.
        List<String> confirmLore = cfg.getRawTextList("confirm-sell-all.confirm-lore", DEFAULT_CONFIRM_LORE);
        if (preview.itemCount <= 0) {
            confirmLore = confirmLore.stream().filter(line -> !Text.mentions(line, "value")).toList();
        }
        inv.setItem(SLOT_CONFIRM, makeItem(
                cfg.getIconMaterial("confirm", Material.LIME_STAINED_GLASS_PANE),
                cfg.getIconName("confirm", "<green><bold>Confirm"),
                render(confirmLore, placeholders)));

        inv.setItem(SLOT_CANCEL, makeItem(
                cfg.getIconMaterial("cancel", Material.RED_STAINED_GLASS_PANE),
                cfg.getIconName("cancel", "<red><bold>Cancel"),
                render(cfg.getRawTextList("confirm-sell-all.cancel-lore", DEFAULT_CANCEL_LORE), placeholders)));
    }

    private List<String> render(List<String> template, Object... placeholders) {
        List<String> lore = new ArrayList<>(template.size());
        for (String line : template) lore.add(Text.legacy(Text.fill(line, placeholders)));
        return lore;
    }

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
