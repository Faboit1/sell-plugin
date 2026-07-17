package com.yourname.sellplugin.gui;

import com.yourname.sellplugin.SellPlugin;
import com.yourname.sellplugin.manager.ConfigManager;
import com.yourname.sellplugin.manager.SellManager;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Confirm/Cancel GUI for the "sell all" action.
 * 27-slot (3 rows) GUI with Confirm (green) and Cancel (red) buttons.
 */
public class ConfirmSellAllGUI implements InventoryHolder {

    private static final int SIZE = 27;

    public static final int SLOT_CONFIRM = 11;
    public static final int SLOT_CANCEL  = 15;

    private final Inventory inv;

    public ConfirmSellAllGUI(SellPlugin plugin, Player player) {
        ConfigManager cfg = plugin.getConfigManager();
        String title = ChatColor.DARK_GRAY + "" + ChatColor.BOLD
                + SmallCaps.convert(cfg.getText("confirm-sell-all.title", "confirm sell all"));
        this.inv = Bukkit.createInventory(this, SIZE, title);
        populate(plugin, cfg, player);
    }

    private void populate(SellPlugin plugin, ConfigManager cfg, Player player) {
        // Background
        Material fillerMat = cfg.getFillerBlock();
        ItemStack bg = makeItem(fillerMat, " ", Collections.emptyList());
        for (int i = 0; i < SIZE; i++) inv.setItem(i, bg);

        // Info item in centre (slot 13)
        SellManager.SellPreview preview = plugin.getSellManager().previewSellAll(player);
        int itemCount = preview.itemCount;
        double value   = preview.value;

        String separator = cfg.getText("lore-separator", "&8━━━━━━━━━━━━━━━━━━━");
        List<String> infoLore = new ArrayList<>();
        infoLore.add(separator);
        infoLore.add(ChatColor.GRAY + " ▸ " + SmallCaps.convert(cfg.getText("confirm-sell-all.items-label", "items: "))
                + ChatColor.WHITE + NumberFormatter.format(itemCount));
        infoLore.add(ChatColor.GRAY + " ▸ " + SmallCaps.convert(cfg.getText("confirm-sell-all.value-label", "value: "))
                + ChatColor.GREEN + "$" + NumberFormatter.format(value));
        infoLore.add(separator);

        inv.setItem(13, makeItem(
                cfg.getIconMaterial("sell-all-info", Material.CHEST),
                cfg.getIconName("sell-all-info", "&f&l" + SmallCaps.convert("sell all")),
                infoLore));

        // Confirm button
        List<String> confirmLore = new ArrayList<>();
        confirmLore.add(ChatColor.GRAY + " ▸ " + SmallCaps.convert(cfg.getText("confirm-sell-all.confirm-lore", "sell all items from your inventory.")));
        if (itemCount > 0) {
            confirmLore.add(ChatColor.GREEN + " ▸ " + SmallCaps.convert(cfg.getText("confirm-sell-all.confirm-earn", "you will earn: $")) + NumberFormatter.format(value));
        }
        inv.setItem(SLOT_CONFIRM, makeItem(
                cfg.getIconMaterial("confirm", Material.LIME_STAINED_GLASS_PANE),
                cfg.getIconName("confirm", "&a&l" + SmallCaps.convert("confirm")),
                confirmLore));

        // Cancel button
        List<String> cancelLore = cfg.getIconLore("cancel",
                Collections.singletonList(ChatColor.GRAY + SmallCaps.convert(cfg.getText("confirm-sell-all.cancel-lore", "go back without selling."))));
        inv.setItem(SLOT_CANCEL, makeItem(
                cfg.getIconMaterial("cancel", Material.RED_STAINED_GLASS_PANE),
                cfg.getIconName("cancel", "&c&l" + SmallCaps.convert("cancel")),
                cancelLore));
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

