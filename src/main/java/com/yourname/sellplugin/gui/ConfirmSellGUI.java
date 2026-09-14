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
 * Confirm/Cancel GUI for selling all items in a category.
 * 27-slot (3 rows) GUI with Confirm and Cancel buttons.
 */
public class ConfirmSellGUI implements InventoryHolder {

    private static final int SIZE = 27;

    public static final int SLOT_CONFIRM = 11;
    public static final int SLOT_CANCEL = 15;
    public static final int SLOT_INFO = 13;

    // Shipped defaults, used only when a config key has been deleted outright.
    private static final String DEFAULT_TITLE = "<dark_gray><bold>Sell your {category}";
    private static final List<String> DEFAULT_INFO_LORE = List.of(
            "<dark_gray>━━━━━━━━━━━━━━━━━━━",
            "<gray> ▸ Items: <white>{items}",
            "<gray> ▸ Value: <green>{marker}${value}",
            "<dark_gray>━━━━━━━━━━━━━━━━━━━"
    );
    private static final List<String> DEFAULT_CONFIRM_LORE = List.of(
            "<gray> ▸ Sell all {category}<gray> items",
            "<gray> ▸ from your inventory.",
            "<green> ▸ You will earn: {marker}${value}"
    );
    private static final List<String> DEFAULT_CANCEL_LORE = List.of(
            "<gray> ▸ Go back without selling."
    );

    private final Inventory inv;
    private final SellPlugin plugin;
    private final String categoryId;
    private final int returnPage;

    public ConfirmSellGUI(SellPlugin plugin, Player player, String categoryId, int returnPage) {
        this.plugin = plugin;
        this.categoryId = categoryId;
        this.returnPage = returnPage;

        ConfigManager cfg = plugin.getConfigManager();
        String title = Text.legacy(Text.fill(
                cfg.getRawText("confirm-sell.title", DEFAULT_TITLE),
                "category", cfg.getRawCategoryDisplayName(categoryId)));
        this.inv = Bukkit.createInventory(this, SIZE, title);
        populate(player);
    }

    private void populate(Player player) {
        ConfigManager cfg = plugin.getConfigManager();

        ItemStack bg = makeItem(cfg.getFillerBlock(), " ", Collections.emptyList());
        for (int i = 0; i < SIZE; i++) inv.setItem(i, bg);

        SellManager.SellPreview preview = plugin.getSellManager().previewCategory(player, categoryId);
        int itemCount = plugin.getSellManager().countCategoryItems(player, categoryId);
        // Marks the figure as an estimate when open orders make up part of it.
        String marker = preview.includesOrders ? cfg.getOrderEstimateMarker() : "";
        Object[] placeholders = {
                "category", cfg.getRawCategoryDisplayName(categoryId),
                "items", NumberFormatter.format(itemCount),
                "value", NumberFormatter.format(preview.value),
                "marker", marker
        };

        inv.setItem(SLOT_INFO, makeItem(
                cfg.getCategoryMaterial(categoryId),
                cfg.getCategoryDisplayName(categoryId),
                render(cfg.getRawTextList("confirm-sell.info-lore", DEFAULT_INFO_LORE), placeholders)));

        // The "you will earn" line only makes sense with something to sell, so
        // any line mentioning the value drops out of an empty confirmation.
        List<String> confirmLore = cfg.getRawTextList("confirm-sell.confirm-lore", DEFAULT_CONFIRM_LORE);
        if (itemCount <= 0) {
            confirmLore = confirmLore.stream().filter(line -> !Text.mentions(line, "value")).toList();
        }
        inv.setItem(SLOT_CONFIRM, makeItem(
                cfg.getIconMaterial("confirm", Material.LIME_STAINED_GLASS_PANE),
                cfg.getIconName("confirm", "<green><bold>Confirm"),
                render(confirmLore, placeholders)));

        inv.setItem(SLOT_CANCEL, makeItem(
                cfg.getIconMaterial("cancel", Material.RED_STAINED_GLASS_PANE),
                cfg.getIconName("cancel", "<red><bold>Cancel"),
                render(cfg.getRawTextList("confirm-sell.cancel-lore", DEFAULT_CANCEL_LORE), placeholders)));
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

    public String getCategoryId() {
        return categoryId;
    }

    public int getReturnPage() {
        return returnPage;
    }
}
