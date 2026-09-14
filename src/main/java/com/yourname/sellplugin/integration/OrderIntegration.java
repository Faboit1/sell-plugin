package com.yourname.sellplugin.integration;

import com.yourname.sellplugin.SellPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bridge to FoOrders' order-fill API, so selling can hand items to the players
 * who are paying above shop price for them.
 *
 * <p>FoOrders is an optional dependency, so everything here goes through
 * reflection: the API only ever passes Bukkit and JDK types, which means
 * SellPlugin compiles and runs with or without FoOrders installed. When it is
 * missing, or the API is a version this build does not understand, the whole
 * integration reports itself unavailable and selling behaves exactly as it did
 * before.
 */
public final class OrderIntegration {

    private static final String PLUGIN_NAME = "FoOrders";
    /** The API revision this build was written against. */
    private static final int SUPPORTED_API_VERSION = 1;
    /** Ceiling on cached quotes, so a long-idle server cannot grow the map forever. */
    private static final int MAX_CACHED_QUOTES = 4096;

    private final SellPlugin plugin;

    private Object api;
    private Method quoteMethod;
    private Method fillMethod;
    private Method revisionMethod;
    private boolean warnedUnavailable;

    /**
     * Quotes cached per player and item signature. The worth tooltip prices every
     * item in a player's inventory on every inventory packet, so without this
     * each of those items would walk every open order on the server, several
     * times a second, per player. FoOrders hands out a revision that moves
     * whenever an order might have, which is what the cache is keyed on.
     */
    private final Map<QuoteKey, List<OrderOffer>> quoteCache = new ConcurrentHashMap<>();
    private volatile long cachedRevision = Long.MIN_VALUE;

    public OrderIntegration(SellPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Looks FoOrders up and caches its API. Safe to call again after a reload or
     * once FoOrders has (re)enabled.
     */
    public void bind() {
        api = null;
        quoteMethod = null;
        fillMethod = null;
        revisionMethod = null;
        quoteCache.clear();

        Plugin foOrders = Bukkit.getPluginManager().getPlugin(PLUGIN_NAME);
        if (foOrders == null || !foOrders.isEnabled()) {
            return;
        }

        try {
            Object resolved = foOrders.getClass().getMethod("orderFillApi").invoke(foOrders);
            if (resolved == null) {
                return;
            }

            Class<?> apiClass = resolved.getClass();
            int version = (int) apiClass.getMethod("apiVersion").invoke(resolved);
            if (version < SUPPORTED_API_VERSION) {
                plugin.getLogger().warning(
                    "FoOrders exposes order API v" + version + " but SellPlugin needs v"
                        + SUPPORTED_API_VERSION + " or newer. Orders will not be filled by selling."
                );
                return;
            }

            quoteMethod = apiClass.getMethod(
                "openOrderQuotes", Player.class, ItemStack.class, double.class, boolean.class);
            fillMethod = apiClass.getMethod(
                "fillOrders", Player.class, ItemStack.class, int.class, double.class, boolean.class);
            revisionMethod = apiClass.getMethod("openOrderRevision");
            quoteMethod.setAccessible(true);
            fillMethod.setAccessible(true);
            revisionMethod.setAccessible(true);
            quoteCache.clear();
            cachedRevision = Long.MIN_VALUE;
            api = resolved;
            plugin.getLogger().info("Hooked into FoOrders: selling will fill open orders first.");
        } catch (ReflectiveOperationException | ClassCastException | LinkageError exception) {
            plugin.getLogger().warning(
                "Could not hook into FoOrders (" + exception.getClass().getSimpleName() + ": "
                    + exception.getMessage() + "). Orders will not be filled by selling."
            );
        }
    }

    /** Whether selling should consult open orders at all. */
    public boolean isActive() {
        return api != null && plugin.getConfigManager().isOrderFillingEnabled();
    }

    /**
     * The open orders this item could go into, best paying first.
     *
     * @param shopUnitPrice what the shop pays per unit of this item for this
     *                      player, used to skip orders that would pay them less
     */
    public List<OrderOffer> quote(Player player, ItemStack item, double shopUnitPrice) {
        List<OrderOffer> offers = allOffers(player, item);
        if (offers.isEmpty()) {
            return offers;
        }

        // Price filtering happens here rather than in FoOrders so the cached
        // answer stays usable for any item, whatever its multiplier works out to.
        double minimum = minimumPrice(shopUnitPrice);
        if (minimum <= 0D) {
            return offers;
        }
        List<OrderOffer> affordable = new ArrayList<>(offers.size());
        for (OrderOffer offer : offers) {
            if (offer.pricePerItem() >= minimum) {
                affordable.add(offer);
            }
        }
        return affordable;
    }

    /** Every open order that matches this item, at any price, best paying first. */
    private List<OrderOffer> allOffers(Player player, ItemStack item) {
        if (!isActive() || player == null || item == null) {
            return List.of();
        }

        long revision = currentRevision();
        // Nothing invalidates entries for a player who logged out, so cap the
        // map rather than letting a quiet server accumulate them indefinitely.
        if (revision != cachedRevision || quoteCache.size() > MAX_CACHED_QUOTES) {
            quoteCache.clear();
            cachedRevision = revision;
        }

        return quoteCache.computeIfAbsent(
            new QuoteKey(player.getUniqueId(), OrderQuoteLedger.signature(item)),
            ignored -> fetchOffers(player, item)
        );
    }

    private List<OrderOffer> fetchOffers(Player player, ItemStack item) {
        // The hook can be dropped mid-call by a failure on another thread, so
        // work from a snapshot rather than the field.
        Object target = api;
        if (target == null) {
            return List.of();
        }

        double[] quotes;
        try {
            quotes = (double[]) quoteMethod.invoke(target, player, item, 0D, includeOwnOrders());
        } catch (ReflectiveOperationException | ClassCastException exception) {
            reportBroken(exception);
            return List.of();
        }

        if (quotes == null || quotes.length < 1) {
            return List.of();
        }

        int count = (int) quotes[0];
        List<OrderOffer> offers = new ArrayList<>(Math.max(0, count));
        for (int i = 0; i < count; i++) {
            int priceIndex = 1 + i * 2;
            if (priceIndex + 1 >= quotes.length) {
                break;
            }
            int units = (int) quotes[priceIndex + 1];
            if (units <= 0) {
                continue;
            }
            offers.add(new OrderOffer(quotes[priceIndex], units));
        }
        return List.copyOf(offers);
    }

    private long currentRevision() {
        Object target = api;
        if (target == null) {
            return Long.MIN_VALUE;
        }
        try {
            return (long) revisionMethod.invoke(target);
        } catch (ReflectiveOperationException | ClassCastException exception) {
            reportBroken(exception);
            return Long.MIN_VALUE;
        }
    }

    /**
     * Hands up to {@code amount} units of this item to the open orders that want
     * it. FoOrders pays the seller for whatever it accepts, so the caller only
     * has to remove the accepted items and leave the rest to the shop.
     */
    public FillResult fill(Player player, ItemStack item, int amount, double shopUnitPrice) {
        if (!isActive() || player == null || item == null || amount <= 0) {
            return FillResult.NONE;
        }

        Object target = api;
        if (target == null) {
            return FillResult.NONE;
        }

        double[] result;
        try {
            result = (double[]) fillMethod.invoke(target, player, item, amount, minimumPrice(shopUnitPrice), includeOwnOrders());
        } catch (ReflectiveOperationException | ClassCastException exception) {
            reportBroken(exception);
            return FillResult.NONE;
        }

        if (result == null || result.length < 2) {
            return FillResult.NONE;
        }
        int filled = (int) result[0];
        if (filled <= 0) {
            return FillResult.NONE;
        }
        // The orders just moved; do not wait for the next revision check to
        // notice, since a preview right after a sell is the likeliest reader.
        quoteCache.clear();
        int completed = result.length > 2 ? (int) result[2] : 0;
        return new FillResult(filled, result[1], completed);
    }

    /**
     * The lowest per-item price an order has to pay before selling routes items
     * into it. Defaults to the shop's own price so a player is never paid less
     * for going through an order than they would have been for selling.
     */
    private double minimumPrice(double shopUnitPrice) {
        if (!plugin.getConfigManager().isOrderFillingOnlyWhenBetter()) {
            return 0D;
        }
        return Math.max(0D, shopUnitPrice);
    }

    private boolean includeOwnOrders() {
        return plugin.getConfigManager().isOwnOrderFillingAllowed();
    }

    /**
     * Drops the hook after an unexpected reflective failure: a broken bridge
     * would otherwise throw once per item, per sell, forever.
     */
    private void reportBroken(Exception exception) {
        api = null;
        if (warnedUnavailable) {
            return;
        }
        warnedUnavailable = true;
        plugin.getLogger().warning(
            "FoOrders order API call failed (" + exception.getClass().getSimpleName() + ": "
                + exception.getMessage() + "). Selling will stop filling orders until a reload."
        );
    }

    private record QuoteKey(UUID playerId, String itemSignature) {
    }

    /** One open order that wants this item. */
    public record OrderOffer(double pricePerItem, int unitsNeeded) {
    }

    /** What an actual fill took and paid. */
    public record FillResult(int unitsFilled, double payout, int ordersCompleted) {
        public static final FillResult NONE = new FillResult(0, 0D, 0);

        public boolean filledAnything() {
            return unitsFilled > 0;
        }
    }
}
