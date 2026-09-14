package com.yourname.sellplugin.integration;

import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Tracks how much of each open order a preview has already spoken for.
 *
 * <p>Without this, previewing a player with five stacks of diamonds against one
 * order for 64 would promise that order to all five stacks and quote five times
 * the money. A ledger is scoped to a single preview - a sell button refresh, a
 * confirm screen - and is thrown away afterwards, so it never goes stale.
 *
 * <p>Stacks are pooled by a signature covering everything FoOrders matches on:
 * the material and the enchantments. Two stacks with the same signature share an
 * order's remaining units; two with different signatures are quoted separately,
 * which can double-count in the rare case where one order accepts both (an order
 * for Sharpness III matches a Sharpness V sword too). Actual selling never
 * relies on this - FoOrders clamps every fill against the live order - so the
 * worst case is a preview reading slightly high, which is why those figures are
 * shown with a "roughly" marker.
 */
public final class OrderQuoteLedger {

    private final OrderIntegration orders;
    private final Player player;
    private final Map<String, List<OrderIntegration.OrderOffer>> pools = new HashMap<>();

    public OrderQuoteLedger(OrderIntegration orders, Player player) {
        this.orders = orders;
        this.player = player;
    }

    /**
     * Books up to {@code amount} units of this item into the best paying orders
     * that still have room, and returns how many units went where.
     *
     * @param shopUnitPrice the shop's own per-unit price for this player, so
     *                      orders that pay less can be skipped
     */
    public Claim claim(ItemStack item, int amount, double shopUnitPrice) {
        if (orders == null || !orders.isActive() || item == null || amount <= 0) {
            return Claim.NONE;
        }

        List<OrderIntegration.OrderOffer> pool = pools.computeIfAbsent(
            signature(item),
            ignored -> new ArrayList<>(orders.quote(player, item, shopUnitPrice))
        );
        if (pool.isEmpty()) {
            return Claim.NONE;
        }

        int left = amount;
        int claimed = 0;
        double value = 0D;
        for (int i = 0; i < pool.size() && left > 0; i++) {
            OrderIntegration.OrderOffer offer = pool.get(i);
            if (offer.unitsNeeded() <= 0) {
                continue;
            }
            int take = Math.min(left, offer.unitsNeeded());
            left -= take;
            claimed += take;
            value += take * offer.pricePerItem();
            pool.set(i, new OrderIntegration.OrderOffer(offer.pricePerItem(), offer.unitsNeeded() - take));
        }

        return claimed > 0 ? new Claim(claimed, value) : Claim.NONE;
    }

    /**
     * The properties FoOrders matches an item on. Stacks sharing a signature draw
     * from the same pool of order capacity.
     */
    public static String signature(ItemStack item) {
        StringBuilder signature = new StringBuilder(item.getType().name());

        // Sorted so two stacks with the same enchantments in a different
        // internal order still land in the same pool.
        Map<String, Integer> enchantments = new TreeMap<>();
        item.getEnchantments().forEach((enchantment, level) -> enchantments.put(keyOf(enchantment), level));
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof EnchantmentStorageMeta storage) {
            storage.getStoredEnchants().forEach((enchantment, level) ->
                enchantments.merge(keyOf(enchantment), level, Math::max));
        }
        for (Map.Entry<String, Integer> entry : enchantments.entrySet()) {
            signature.append('|').append(entry.getKey()).append(':').append(entry.getValue());
        }

        // Custom items are matched against a saved template, so anything that
        // makes two same-material stacks look different has to split the pool.
        if (meta != null) {
            if (meta.hasDisplayName()) {
                signature.append("|name=").append(meta.getDisplayName());
            }
            if (meta.hasCustomModelData()) {
                signature.append("|model=").append(meta.getCustomModelData());
            }
            if (meta.hasLore()) {
                signature.append("|lore=").append(meta.getLore());
            }
        }
        return signature.toString();
    }

    private static String keyOf(Enchantment enchantment) {
        NamespacedKey key = enchantment.getKey();
        return key == null ? enchantment.toString() : key.toString();
    }

    /** How many units of a stack an order wants, and what they pay. */
    public record Claim(int units, double value) {
        public static final Claim NONE = new Claim(0, 0D);

        public boolean isEmpty() {
            return units <= 0;
        }
    }
}
