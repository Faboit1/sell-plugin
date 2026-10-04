package com.yourname.sellplugin.manager;

import com.yourname.sellplugin.SellPlugin;
import com.yourname.sellplugin.integration.OrderIntegration;
import com.yourname.sellplugin.integration.OrderQuoteLedger;
import com.yourname.sellplugin.util.NumberFormatter;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.ShulkerBox;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.Map;

public class SellManager {

    private static final String FALLBACK_SOUND = "ENTITY_EXPERIENCE_ORB_PICKUP";

    private final SellPlugin plugin;

    public SellManager(SellPlugin plugin) {
        this.plugin = plugin;
    }

    // ---------------------------------------------------------------
    // Valuing one stack, open orders first
    // ---------------------------------------------------------------

    /**
     * What one stack is worth to this player right now.
     *
     * <p>Open FoOrders orders are served before the shop, because an order pays
     * its own per-item price rather than the shop's. Whatever the orders do not
     * want - no order matches it, they are already full, or they pay less than
     * the shop - falls through to the shop at the usual enchantment-adjusted,
     * multiplier-adjusted price. An item the shop does not buy at all can still
     * fill an order; only the part an order took is then consumed.
     *
     * @param commit whether to actually hand the items over. Committing pays the
     *               seller the order money through FoOrders there and then; the
     *               shop half is still the caller's to deposit.
     * @param ledger ignored when committing, because FoOrders clamps every fill
     *               against the live order itself. For a preview spanning several
     *               stacks pass one shared ledger, so a single order is not
     *               promised to every stack that matches it.
     */
    public StackSale valueStack(Player player, ItemStack item, String itemKey, boolean commit, OrderQuoteLedger ledger) {
        if (item == null || item.getType() == Material.AIR || itemKey == null) {
            return StackSale.NONE;
        }

        double base = plugin.getPriceManager().getPrice(itemKey);
        boolean shopBuys = base > 0;
        String category = shopBuys ? plugin.getPriceManager().getCategory(itemKey) : null;
        double shopUnitPrice = 0.0;
        if (shopBuys) {
            double multiplier = plugin.getMultiplierManager().getEffectiveMultiplier(player, category);
            shopUnitPrice = enchantedUnitPrice(item, base) * multiplier;
        }

        int amount = item.getAmount();
        OrderClaim claim = claimOrders(player, item, amount, shopUnitPrice, commit, ledger);
        if (!shopBuys && claim.units <= 0) {
            return StackSale.NONE;
        }

        double shopEarned = shopUnitPrice * (amount - claim.units);
        return new StackSale(category, amount, shopBuys, shopEarned, claim.units, claim.value,
                shopUnitPrice * claim.units);
    }

    private OrderClaim claimOrders(Player player, ItemStack item, int amount, double shopUnitPrice,
                                   boolean commit, OrderQuoteLedger ledger) {
        OrderIntegration orders = plugin.getOrderIntegration();
        if (orders == null || !orders.isActive() || amount <= 0) {
            return OrderClaim.NONE;
        }

        if (commit) {
            OrderIntegration.FillResult fill = orders.fill(player, item, amount, shopUnitPrice);
            return new OrderClaim(Math.min(fill.unitsFilled(), amount), fill.payout());
        }

        // A preview with no ledger is a single stack looked at on its own, so
        // give it a ledger of its own rather than special-casing it.
        OrderQuoteLedger scope = ledger != null ? ledger : new OrderQuoteLedger(orders, player);
        OrderQuoteLedger.Claim claimed = scope.claim(item, amount, shopUnitPrice);
        return new OrderClaim(Math.min(claimed.units(), amount), claimed.value());
    }

    /** Preview-values a stack, sharing {@code ledger} with the rest of the batch. */
    public StackSale previewStack(Player player, ItemStack item, OrderQuoteLedger ledger) {
        return valueStack(player, item, plugin.getPriceManager().getItemKey(item), false, ledger);
    }

    /** A ledger for previewing several stacks together. */
    public OrderQuoteLedger newLedger(Player player) {
        OrderIntegration orders = plugin.getOrderIntegration();
        return orders == null ? null : new OrderQuoteLedger(orders, player);
    }

    /**
     * Sells one stack held in {@code inventory-like} storage, taking out only
     * what was actually bought and leaving any remainder in place. Returns what
     * the sale was worth.
     */
    private StackSale sellStackAt(Player player, ItemStack item, String itemKey, SlotWriter slot) {
        StackSale sale = valueStack(player, item, itemKey, true, null);
        int consumed = sale.unitsConsumed();
        if (consumed <= 0) {
            return StackSale.NONE;
        }
        if (consumed >= item.getAmount()) {
            slot.write(null);
        } else {
            ItemStack remainder = item.clone();
            remainder.setAmount(item.getAmount() - consumed);
            slot.write(remainder);
        }
        return sale;
    }

    // ---------------------------------------------------------------
    // Sell entire player inventory
    // ---------------------------------------------------------------
    public SellResult sellAll(Player player) {
        return sellInventory(player, null, null);
    }

    // ---------------------------------------------------------------
    // Sell only items in a specific category
    // ---------------------------------------------------------------
    public SellResult sellCategory(Player player, String category) {
        return sellInventory(player, category, null);
    }

    // ---------------------------------------------------------------
    // Sell all stacks of a specific item type/key
    // ---------------------------------------------------------------
    public SellResult sellItemType(Player player, String itemKey) {
        return sellInventory(player, null, itemKey);
    }

    /** Sweeps the player's inventory, optionally narrowed to a category or item key. */
    private SellResult sellInventory(Player player, String categoryFilter, String itemKeyFilter) {
        // Checked up front: once items have gone to orders or left the inventory
        // there is nothing to roll back to if the payout then fails.
        if (!plugin.getEconomyManager().isAvailable()) {
            player.sendMessage(plugin.getConfigManager().getMessage("economy-error"));
            return new SellResult(0, 0, false);
        }

        SaleTally tally = new SaleTally();

        int storageSize = player.getInventory().getStorageContents().length;
        for (int i = 0; i < storageSize; i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (item == null || item.getType() == Material.AIR) continue;

            if (sellShulker(item)) {
                tally.add(sellShulkerContents(player, item, categoryFilter, itemKeyFilter));
                continue;
            }

            String key = plugin.getPriceManager().getItemKey(item);
            if (key == null) continue;
            if (itemKeyFilter != null && !itemKeyFilter.equalsIgnoreCase(key)) continue;
            if (categoryFilter != null && !categoryFilter.equalsIgnoreCase(plugin.getPriceManager().getCategory(key))) continue;

            int slot = i;
            tally.add(sellStackAt(player, item, key, replacement -> player.getInventory().setItem(slot, replacement)));
        }

        return finalizeSell(player, tally);
    }

    // ---------------------------------------------------------------
    // Preview result (item count + value) for selling all items
    // ---------------------------------------------------------------
    public SellPreview previewSellAll(Player player) {
        SaleTally tally = previewInventory(player, null);
        return new SellPreview(tally.items, tally.totalEarned(), tally.orderUnits > 0);
    }

    /**
     * Values everything the player is carrying without touching it, optionally
     * narrowed to one category. One ledger covers the whole sweep so a single
     * order is not counted once per matching stack.
     */
    private SaleTally previewInventory(Player player, String categoryFilter) {
        SaleTally tally = new SaleTally();
        OrderQuoteLedger ledger = newLedger(player);

        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item == null || item.getType() == Material.AIR) continue;

            if (sellShulker(item)) {
                tally.add(peekShulkerContents(player, item, categoryFilter, null, ledger));
                continue;
            }

            String key = plugin.getPriceManager().getItemKey(item);
            if (key == null) continue;
            if (categoryFilter != null && !categoryFilter.equalsIgnoreCase(plugin.getPriceManager().getCategory(key))) continue;

            tally.add(valueStack(player, item, key, false, ledger));
        }
        return tally;
    }

    // ---------------------------------------------------------------
    // Count how many sellable items of a category the player has
    // ---------------------------------------------------------------
    public int countCategoryItems(Player player, String category) {
        int total = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item == null || item.getType() == Material.AIR) continue;

            if (sellShulker(item)) {
                total += peekShulkerContents(player, item, category, null, null).items;
                continue;
            }

            String key = plugin.getPriceManager().getItemKey(item);
            if (key == null) continue;
            String cat = plugin.getPriceManager().getCategory(key);
            if (category.equalsIgnoreCase(cat)) total += item.getAmount();
        }
        return total;
    }

    // ---------------------------------------------------------------
    // Calculate value of sellable items in a category
    // ---------------------------------------------------------------
    public double calculateCategoryValue(Player player, String category) {
        return previewInventory(player, category).totalEarned();
    }

    /** Category value plus whether open orders contribute to it. */
    public SellPreview previewCategory(Player player, String category) {
        SaleTally tally = previewInventory(player, category);
        return new SellPreview(tally.items, tally.totalEarned(), tally.orderUnits > 0);
    }

    /**
     * What the shop alone pays for this stack, ignoring orders entirely. This is
     * the figure the price browser and any plain shop-value display want.
     */
    public double calculateItemWorth(Player player, ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return 0.0;

        if (isShulkerBox(item)) {
            return peekShulkerContents(player, item, null, null, null).shopEarned;
        }

        String key = plugin.getPriceManager().getItemKey(item);
        if (key == null) return 0.0;

        double base = plugin.getPriceManager().getPrice(key);
        if (base <= 0) return 0.0;

        String category = plugin.getPriceManager().getCategory(key);
        double multiplier = plugin.getMultiplierManager().getEffectiveMultiplier(player, category);
        return enchantedUnitPrice(item, base) * multiplier * item.getAmount();
    }

    /**
     * What this stack is really worth to the player, open orders included. Pass a
     * shared {@code ledger} when several stacks are being valued together.
     */
    public StackSale evaluateItemWorth(Player player, ItemStack item, OrderQuoteLedger ledger) {
        if (item == null || item.getType() == Material.AIR) return StackSale.NONE;

        if (isShulkerBox(item)) {
            return peekShulkerContents(player, item, null, null, ledger).asSale();
        }
        return previewStack(player, item, ledger);
    }

    // ---------------------------------------------------------------
    // Finalize a sell operation
    // ---------------------------------------------------------------
    private SellResult finalizeSell(Player player, SaleTally tally) {
        if (tally.totalEarned() <= 0) {
            player.sendMessage(plugin.getConfigManager().getMessage("nothing-to-sell"));
            return new SellResult(0, 0, false);
        }

        // FoOrders has already paid the seller for the part the orders took, so
        // only the shop's half goes through our own economy.
        if (tally.shopEarned > 0) {
            boolean ok = plugin.getEconomyManager().deposit(player, tally.shopEarned);
            if (!ok) {
                player.sendMessage(plugin.getConfigManager().getMessage("economy-error"));
                return new SellResult(tally.orderEarned, tally.orderUnits, tally.orderUnits > 0);
            }
        }

        Map<String, Double> tracked = new HashMap<>(tally.categoryEarnings);
        if (plugin.getConfigManager().doOrderEarningsCountTowardMultipliers()) {
            tally.categoryOrderEarnings.forEach((cat, value) -> tracked.merge(cat, value, Double::sum));
        }
        for (Map.Entry<String, Double> e : tracked.entrySet()) {
            plugin.getMultiplierManager().addEarnings(player, e.getKey(), e.getValue());
        }

        sendSellNotification(player, tally.totalEarned(), tally.items);
        return new SellResult(tally.totalEarned(), tally.items, true);
    }

    /** Applies a finished tally: deposits, tracks earnings and notifies. Used by the GUI flows. */
    public SellResult completeSale(Player player, SaleTally tally) {
        return finalizeSell(player, tally);
    }

    // ---------------------------------------------------------------
    // Notification: action bar only (+$amount in lime/green color)
    // Title and chat are optional via config
    // ---------------------------------------------------------------
    public void sendSellNotification(Player player, double amount, int itemCount) {
        String formatted = NumberFormatter.format(amount);

        // Action bar: "+$amount" (toggleable)
        if (plugin.getConfigManager().isActionBarEnabled()) {
            String actionBarText = plugin.getConfigManager().getText("action-bar", "&a+${amount}")
                    .replace("{amount}", formatted);
            player.sendActionBar(actionBarText);
        }

        // Title notification: only if enabled in config
        if (plugin.getConfigManager().isTitleNotificationEnabled()) {
            String titleText = plugin.getConfigManager().getText("sell-title", "&a+${amount}")
                    .replace("{amount}", formatted);
            String subtitleText = plugin.getConfigManager().getText("sell-subtitle", "&7You sold {count} item(s)")
                    .replace("{count}", NumberFormatter.format(itemCount));
            player.sendTitle(titleText, subtitleText, 10, 40, 20);
        }

        // Play sound if enabled
        String soundName = plugin.getConfigManager().getSoundType();
        if (plugin.getConfigManager().areSoundsEnabled() && soundName != null) {
            try {
                Sound sound = Sound.valueOf(soundName);
                player.playSound(player.getLocation(), sound, 1.0f, 1.2f);
            } catch (IllegalArgumentException ignored) {
                player.playSound(player.getLocation(), Sound.valueOf(FALLBACK_SOUND), 1.0f, 1.2f);
            }
        }

        // Chat message: only if prefix enabled
        if (plugin.getConfigManager().isPrefixEnabled()) {
            String msg = plugin.getConfigManager().getMessage("sold-items")
                    .replace("{amount}", NumberFormatter.format(itemCount))
                    .replace("{price}", formatted);
            player.sendMessage(msg);
        }
    }

    // ---------------------------------------------------------------
    // Shulker box helpers
    // ---------------------------------------------------------------

    /**
     * Returns true if the item is a shulker box of any colour, including the
     * uncoloured {@code SHULKER_BOX} (whose name does <em>not</em> end with
     * {@code _SHULKER_BOX}, which is why it used to be skipped).
     */
    public static boolean isShulkerBox(ItemStack item) {
        if (item == null) return false;
        Material type = item.getType();
        return type == Material.SHULKER_BOX || type.name().endsWith("_SHULKER_BOX");
    }

    /** True when the item should be dived into and its contents sold. */
    private boolean sellShulker(ItemStack item) {
        return plugin.getConfigManager().isShulkerSellingEnabled() && isShulkerBox(item);
    }

    // ---------------------------------------------------------------
    // Enchantment-aware unit pricing
    // ---------------------------------------------------------------

    /**
     * Adjusts a single item's base price for its enchantments: each
     * enchantment's configured value (× its level) is added to the base, then
     * the total is multiplied by a factor (default 1.1) for every distinct
     * enchantment on the item. Unenchanted items return the base unchanged.
     */
    public double enchantedUnitPrice(ItemStack item, double base) {
        if (item == null || !plugin.getConfigManager().isEnchantmentPricingEnabled()) return base;

        Map<Enchantment, Integer> enchants = collectEnchantments(item);
        if (enchants.isEmpty()) return base;

        double added = 0.0;
        int count = 0;
        for (Map.Entry<Enchantment, Integer> e : enchants.entrySet()) {
            String key = e.getKey().getKey().getKey(); // e.g. "sharpness"
            added += plugin.getConfigManager().getEnchantValue(key) * e.getValue();
            count++;
        }

        double factor = plugin.getConfigManager().getEnchantMultiplierPerEnchantment();
        return (base + added) * Math.pow(factor, count);
    }

    /** Merges an item's applied enchantments with any stored (book) enchantments. */
    private Map<Enchantment, Integer> collectEnchantments(ItemStack item) {
        Map<Enchantment, Integer> merged = new HashMap<>(item.getEnchantments());
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof EnchantmentStorageMeta storage) {
            storage.getStoredEnchants().forEach((ench, lvl) -> merged.merge(ench, lvl, Math::max));
        }
        return merged;
    }

    /**
     * Sell sellable items inside a shulker box, modifying its inventory in place.
     * Filters by category and/or itemKey when non-null.
     * The shulker box item itself is never consumed.
     */
    public SaleTally sellShulkerContents(Player player, ItemStack shulkerItem,
                                          String categoryFilter, String itemKeyFilter) {
        SaleTally tally = new SaleTally();
        if (!(shulkerItem.getItemMeta() instanceof BlockStateMeta bsm)) return tally;
        if (!(bsm.getBlockState() instanceof ShulkerBox shulker)) return tally;

        ItemStack[] contents = shulker.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack inner = contents[i];
            if (inner == null || inner.getType() == Material.AIR) continue;

            String key = plugin.getPriceManager().getItemKey(inner);
            if (key == null) continue;
            if (itemKeyFilter != null && !itemKeyFilter.equalsIgnoreCase(key)) continue;
            if (categoryFilter != null && !categoryFilter.equalsIgnoreCase(plugin.getPriceManager().getCategory(key))) continue;

            int slot = i;
            tally.add(sellStackAt(player, inner, key, replacement -> shulker.getInventory().setItem(slot, replacement)));
        }

        if (tally.items > 0) {
            bsm.setBlockState(shulker);
            shulkerItem.setItemMeta(bsm);
        }

        return tally;
    }

    /**
     * Peek at sellable items inside a shulker box without modifying it.
     * Filters by category and/or itemKey when non-null.
     */
    private SaleTally peekShulkerContents(Player player, ItemStack shulkerItem, String categoryFilter,
                                          String itemKeyFilter, OrderQuoteLedger ledger) {
        SaleTally tally = new SaleTally();
        if (!(shulkerItem.getItemMeta() instanceof BlockStateMeta bsm)) return tally;
        if (!(bsm.getBlockState() instanceof ShulkerBox shulker)) return tally;

        for (ItemStack inner : shulker.getInventory().getContents()) {
            if (inner == null || inner.getType() == Material.AIR) continue;

            String key = plugin.getPriceManager().getItemKey(inner);
            if (key == null) continue;
            if (itemKeyFilter != null && !itemKeyFilter.equalsIgnoreCase(key)) continue;
            if (categoryFilter != null && !categoryFilter.equalsIgnoreCase(plugin.getPriceManager().getCategory(key))) continue;

            tally.add(valueStack(player, inner, key, false, ledger));
        }

        return tally;
    }

    /** Somewhere a sold-off remainder can be written back to. */
    @FunctionalInterface
    private interface SlotWriter {
        void write(ItemStack replacement);
    }

    private record OrderClaim(int units, double value) {
        static final OrderClaim NONE = new OrderClaim(0, 0.0);
    }

    // ---------------------------------------------------------------
    // What one stack of items came to
    // ---------------------------------------------------------------
    public static final class StackSale {
        public static final StackSale NONE = new StackSale(null, 0, false, 0.0, 0, 0.0);

        /** Price category, or null when the shop does not buy this item. */
        public final String category;
        public final int stackAmount;
        public final boolean shopBuys;
        /** Owed by the shop, to be deposited by this plugin. */
        public final double shopEarned;
        public final int orderUnits;
        /** Paid by FoOrders when committing, or projected when previewing. */
        public final double orderEarned;
        /** What the shop would have paid for the units the orders took. */
        public final double orderShopValue;

        public StackSale(String category, int stackAmount, boolean shopBuys,
                         double shopEarned, int orderUnits, double orderEarned) {
            this(category, stackAmount, shopBuys, shopEarned, orderUnits, orderEarned, 0.0);
        }

        public StackSale(String category, int stackAmount, boolean shopBuys,
                         double shopEarned, int orderUnits, double orderEarned, double orderShopValue) {
            this.category = category;
            this.stackAmount = stackAmount;
            this.shopBuys = shopBuys;
            this.shopEarned = shopEarned;
            this.orderUnits = orderUnits;
            this.orderEarned = orderEarned;
            this.orderShopValue = orderShopValue;
        }

        public double totalEarned() {
            return shopEarned + orderEarned;
        }

        /**
         * How many items leave the player. The shop takes the whole stack when it
         * buys the item at all; otherwise only the part an order wanted goes.
         */
        public int unitsConsumed() {
            return shopBuys ? stackAmount : Math.min(orderUnits, stackAmount);
        }

        public boolean usedOrders() {
            return orderUnits > 0;
        }
    }

    // ---------------------------------------------------------------
    // Running total across many stacks
    // ---------------------------------------------------------------
    public static final class SaleTally {
        public double shopEarned;
        public double orderEarned;
        public int items;
        public int orderUnits;
        /** Shop money per price category, for multiplier progress. */
        public final Map<String, Double> categoryEarnings = new HashMap<>();
        /** Order money per price category, kept apart so it can be opted out of. */
        public final Map<String, Double> categoryOrderEarnings = new HashMap<>();

        public void add(StackSale sale) {
            if (sale == null) return;
            int consumed = sale.unitsConsumed();
            if (consumed <= 0) return;

            shopEarned += sale.shopEarned;
            orderEarned += sale.orderEarned;
            items += consumed;
            orderUnits += sale.orderUnits;
            if (sale.category != null) {
                categoryEarnings.merge(sale.category, sale.shopEarned, Double::sum);
                // Counted at most at what the shop would have paid for those
                // units. An order's price is whatever its owner typed, so
                // counting it in full would let two accounts pass money back
                // and forth through an overpriced order and raise a multiplier
                // as far as they like without selling anything of real worth.
                double countable = Math.min(sale.orderEarned, sale.orderShopValue);
                if (countable > 0) {
                    categoryOrderEarnings.merge(sale.category, countable, Double::sum);
                }
            }
        }

        public void add(SaleTally other) {
            if (other == null) return;
            shopEarned += other.shopEarned;
            orderEarned += other.orderEarned;
            items += other.items;
            orderUnits += other.orderUnits;
            other.categoryEarnings.forEach((cat, value) -> categoryEarnings.merge(cat, value, Double::sum));
            other.categoryOrderEarnings.forEach((cat, value) -> categoryOrderEarnings.merge(cat, value, Double::sum));
        }

        public double totalEarned() {
            return shopEarned + orderEarned;
        }

        public boolean usedOrders() {
            return orderUnits > 0;
        }

        /** Flattens the tally back into a single sale, for callers that want one figure. */
        public StackSale asSale() {
            return new StackSale(null, items, true, shopEarned, orderUnits, orderEarned);
        }
    }

    // ---------------------------------------------------------------
    // Simple inner result class
    // ---------------------------------------------------------------
    public static class SellResult {
        public final double earned;
        public final int itemsSold;
        public final boolean success;

        public SellResult(double earned, int itemsSold, boolean success) {
            this.earned = earned;
            this.itemsSold = itemsSold;
            this.success = success;
        }
    }

    // ---------------------------------------------------------------
    // Preview result for sell-all (no inventory modification)
    // ---------------------------------------------------------------
    public static class SellPreview {
        public final int itemCount;
        public final double value;
        /** True when open orders make up part of {@link #value}, so it is an estimate. */
        public final boolean includesOrders;

        public SellPreview(int itemCount, double value, boolean includesOrders) {
            this.itemCount = itemCount;
            this.value = value;
            this.includesOrders = includesOrders;
        }
    }
}
