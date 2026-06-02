package com.yourname.sellplugin.listener;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.events.PacketListener;
import com.yourname.sellplugin.SellPlugin;
import com.yourname.sellplugin.gui.CategoryItemsGUI;
import com.yourname.sellplugin.gui.CategoryProgressGUI;
import com.yourname.sellplugin.gui.ConfirmSellAllGUI;
import com.yourname.sellplugin.gui.ConfirmSellGUI;
import com.yourname.sellplugin.gui.SellAllGUI;
import com.yourname.sellplugin.gui.ShopMainGUI;
import com.yourname.sellplugin.gui.TopSellGUI;
import com.yourname.sellplugin.util.NumberFormatter;
import org.bukkit.block.DoubleChest;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.BlockInventoryHolder;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class WorthPacketListener {

    private final SellPlugin plugin;
    private PacketListener packetListener;

    public WorthPacketListener(SellPlugin plugin) {
        this.plugin = plugin;
    }

    public void register() {
        if (plugin.getServer().getPluginManager().getPlugin("ProtocolLib") == null) {
            plugin.getLogger().warning("ProtocolLib not found; sell worth tooltips are disabled.");
            return;
        }

        ProtocolManager protocolManager = ProtocolLibrary.getProtocolManager();
        packetListener = new PacketAdapter(plugin, ListenerPriority.NORMAL,
                PacketType.Play.Server.SET_SLOT,
                PacketType.Play.Server.WINDOW_ITEMS) {

            @Override
            public void onPacketSending(PacketEvent event) {
                if (!plugin.getConfigManager().isWorthEnabled()) return;
                if (!shouldDecorate(event.getPlayer())) return;

                if (event.getPacketType() == PacketType.Play.Server.SET_SLOT) {
                    if (event.getPacket().getItemModifier().size() <= 0) return;
                    ItemStack item = event.getPacket().getItemModifier().read(0);
                    ItemStack updated = addWorthLore(event.getPlayer(), item);
                    if (updated != item) {
                        event.getPacket().getItemModifier().write(0, updated);
                    }
                    return;
                }

                if (event.getPacket().getItemListModifier().size() <= 0) return;
                List<ItemStack> items = event.getPacket().getItemListModifier().read(0);
                if (items == null || items.isEmpty()) return;

                boolean changed = false;
                List<ItemStack> updatedItems = new ArrayList<>(items.size());
                for (ItemStack item : items) {
                    ItemStack updated = addWorthLore(event.getPlayer(), item);
                    updatedItems.add(updated);
                    changed |= updated != item;
                }

                if (changed) {
                    event.getPacket().getItemListModifier().write(0, updatedItems);
                }
            }
        };
        protocolManager.addPacketListener(packetListener);
    }

    public void unregister() {
        if (packetListener == null) return;
        ProtocolLibrary.getProtocolManager().removePacketListener(packetListener);
        packetListener = null;
    }

    private ItemStack addWorthLore(Player player, ItemStack original) {
        if (original == null || original.getType().isAir()) return original;

        double worth = plugin.getSellManager().calculateItemWorth(player, original);
        if (worth <= 0) return original;

        ItemStack clone = original.clone();
        ItemMeta meta = clone.getItemMeta();
        if (meta == null) return original;

        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        if (!lore.isEmpty()) {
            lore.add("");
        }
        lore.add(plugin.getConfigManager().getWorthFormat()
                .replace("{worth}", NumberFormatter.format(worth)));
        meta.setLore(lore);
        clone.setItemMeta(meta);
        return clone;
    }

    private boolean shouldDecorate(Player player) {
        Inventory topInventory = player.getOpenInventory().getTopInventory();
        InventoryHolder holder = topInventory.getHolder();

        if (isPluginGui(holder)) return false;

        InventoryType type = topInventory.getType();
        if (type == InventoryType.CRAFTING
                || type == InventoryType.CREATIVE
                || type == InventoryType.PLAYER) {
            return true;
        }

        if (holder instanceof BlockInventoryHolder || holder instanceof DoubleChest) {
            return true;
        }

        return switch (type) {
            case ANVIL, BEACON, BLAST_FURNACE, BREWING, CARTOGRAPHY, CRAFTER,
                    ENCHANTING, FURNACE, GRINDSTONE, LOOM, MERCHANT,
                    SMITHING, SMOKER, STONECUTTER -> true;
            default -> false;
        };
    }

    private boolean isPluginGui(InventoryHolder holder) {
        return holder instanceof ShopMainGUI
                || holder instanceof CategoryProgressGUI
                || holder instanceof CategoryItemsGUI
                || holder instanceof SellAllGUI
                || holder instanceof ConfirmSellGUI
                || holder instanceof ConfirmSellAllGUI
                || holder instanceof TopSellGUI;
    }
}
