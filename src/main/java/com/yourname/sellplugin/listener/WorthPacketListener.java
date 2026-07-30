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

    // Invisible marker prefixed to the worth line we inject. It is built from
    // valid formatting codes only, so it renders no glyphs, but lets us reliably
    // recognise (and strip) our own line — both to avoid duplicates and to keep
    // it out of items the client sends back to the server (creative mode).
    private static final char SECTION = '\u00A7';
    private static final String WORTH_MARKER =
            "" + SECTION + '9' + SECTION + '8' + SECTION + '9' + SECTION + '8' + SECTION + 'r';

    private final SellPlugin plugin;
    private PacketListener packetListener;
    private PacketListener creativeListener;

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
                if (!WorthPacketListener.this.plugin.getConfigManager().isWorthEnabled()) return;
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

        // Creative-mode clients echo the items they see back to the server. Strip
        // our injected worth line from those inbound items so it never gets baked
        // into the real ItemStack (which would otherwise produce duplicate lines).
        creativeListener = new PacketAdapter(plugin, ListenerPriority.NORMAL,
                PacketType.Play.Client.SET_CREATIVE_SLOT) {

            @Override
            public void onPacketReceiving(PacketEvent event) {
                if (event.getPacket().getItemModifier().size() <= 0) return;
                ItemStack item = event.getPacket().getItemModifier().read(0);
                ItemStack cleaned = stripWorthLore(item);
                if (cleaned != item) {
                    event.getPacket().getItemModifier().write(0, cleaned);
                }
            }
        };
        protocolManager.addPacketListener(creativeListener);
    }

    public void unregister() {
        ProtocolManager protocolManager = ProtocolLibrary.getProtocolManager();
        if (packetListener != null) {
            protocolManager.removePacketListener(packetListener);
            packetListener = null;
        }
        if (creativeListener != null) {
            protocolManager.removePacketListener(creativeListener);
            creativeListener = null;
        }
    }

    private ItemStack addWorthLore(Player player, ItemStack original) {
        if (original == null || original.getType().isAir()) return original;

        double worth = plugin.getSellManager().calculateItemWorth(player, original);

        ItemMeta meta = original.getItemMeta();
        boolean hadWorthLine = meta != null && meta.hasLore() && loreHasWorthLine(meta.getLore());

        // Nothing to add and nothing stale to clean up -> leave the item untouched.
        if (worth <= 0 && !hadWorthLine) return original;

        ItemStack clone = original.clone();
        meta = clone.getItemMeta();
        if (meta == null) return original;

        // Always start from lore without any previously injected/baked worth line
        // so we never stack duplicates.
        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        removeWorthLines(lore);

        if (worth > 0) {
            if (!lore.isEmpty() && !isBlank(lore.get(lore.size() - 1))) {
                lore.add("");
            }
            lore.add(WORTH_MARKER + plugin.getConfigManager().getWorthFormat()
                    .replace("{worth}", NumberFormatter.format(worth)));
        }

        meta.setLore(lore.isEmpty() ? null : lore);
        clone.setItemMeta(meta);
        return clone;
    }

    /**
     * Returns a copy of {@code item} with any injected worth line removed, or the
     * original reference if it carried none.
     */
    private ItemStack stripWorthLore(ItemStack item) {
        if (item == null || item.getType().isAir()) return item;

        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasLore()) return item;

        List<String> lore = new ArrayList<>(meta.getLore());
        if (!removeWorthLines(lore)) return item;

        ItemStack clone = item.clone();
        ItemMeta cloneMeta = clone.getItemMeta();
        cloneMeta.setLore(lore.isEmpty() ? null : lore);
        clone.setItemMeta(cloneMeta);
        return clone;
    }

    private boolean loreHasWorthLine(List<String> lore) {
        for (String line : lore) {
            if (line != null && line.contains(WORTH_MARKER)) return true;
        }
        return false;
    }

    /**
     * Removes every injected worth line (and the blank separator we place before
     * it) from {@code lore} in place. Returns {@code true} if anything changed.
     */
    private boolean removeWorthLines(List<String> lore) {
        boolean changed = false;
        for (int i = lore.size() - 1; i >= 0; i--) {
            String line = lore.get(i);
            // Use contains() rather than startsWith(): when a creative client
            // echoes our lore back it can arrive with an extra leading colour
            // code (e.g. "§f") prepended, which would defeat a prefix match and
            // let the line bake in / duplicate.
            if (line == null || !line.contains(WORTH_MARKER)) continue;
            lore.remove(i);
            changed = true;
            // Drop the blank separator we added directly before the worth line.
            if (i - 1 >= 0 && isBlank(lore.get(i - 1))) {
                lore.remove(i - 1);
            }
        }
        return changed;
    }

    /**
     * Treats a line as blank if, after stripping any formatting codes, nothing
     * printable remains. Round-tripped separators can come back as "§f" etc.
     */
    private boolean isBlank(String line) {
        if (line == null || line.isEmpty()) return true;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == SECTION) {
                i++; // skip the code character that follows the section sign
                continue;
            }
            if (!Character.isWhitespace(c)) return false;
        }
        return true;
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
