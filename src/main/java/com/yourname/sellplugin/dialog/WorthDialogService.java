package com.yourname.sellplugin.dialog;

import com.yourname.sellplugin.SellPlugin;
import com.yourname.sellplugin.manager.ConfigManager;
import com.yourname.sellplugin.manager.PriceManager;
import com.yourname.sellplugin.util.ItemNameFormatter;
import com.yourname.sellplugin.util.NumberFormatter;
import com.yourname.sellplugin.util.Text;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Shows the item price list in one of Minecraft's own dialog screens instead of
 * a chest menu, so a whole page of prices is readable at a glance and a search
 * box narrows it without clicking through pages.
 *
 * <p>Requires a server with Paper's Dialog API (Minecraft 1.21.6 and up).
 * {@link #isSupported()} answers whether this server has it; when it does not,
 * the caller keeps using the paginated chest menu.
 */
public final class WorthDialogService {

    private static final String SEARCH_KEY = "search";
    private static final String DIALOG_CLASS = "io.papermc.paper.dialog.Dialog";

    private static final int BODY_WIDTH = 320;
    private static final int INPUT_WIDTH = 200;
    /**
     * A dialog crosses the wire in a single packet, so the whole price list
     * (well over a thousand entries on a stock config) cannot be sent at once.
     * A bounded batch goes out and the search box reaches the rest.
     */
    private static final int DEFAULT_MAX_BUTTONS = 256;
    private static final int DEFAULT_COLUMNS = 2;

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    // The buttons only ever reopen this same dialog, so their callbacks have to
    // outlive a single click.
    private static final ClickCallback.Options CALLBACK_OPTIONS = ClickCallback.Options.builder()
            .uses(ClickCallback.UNLIMITED_USES)
            .lifetime(Duration.ofHours(6))
            .build();

    private final SellPlugin plugin;

    public WorthDialogService(SellPlugin plugin) {
        this.plugin = plugin;
    }

    /** Whether this server understands dialogs at all. */
    public static boolean isSupported() {
        try {
            Class.forName(DIALOG_CLASS);
            return true;
        } catch (ClassNotFoundException | LinkageError exception) {
            return false;
        }
    }

    /** Whether prices should be shown as a dialog rather than the chest menu. */
    public boolean isEnabled() {
        return isSupported() && plugin.getConfig().getBoolean("worth-dialog.enabled", true);
    }

    /**
     * Opens the price list for this player.
     *
     * @return false when the dialog could not be shown, so the caller can fall
     *         back to the chest menu
     */
    public boolean open(Player player, String search) {
        if (player == null || !player.isOnline() || !isEnabled()) {
            return false;
        }
        try {
            player.showDialog(build(player, search == null ? "" : search));
            return true;
        } catch (RuntimeException | LinkageError exception) {
            plugin.getLogger().warning("Item price dialog failed, falling back to the menu: "
                    + exception.getMessage());
            return false;
        }
    }

    private Dialog build(Player player, String search) {
        List<String> matches = matchingKeys(search);
        int limit = maxButtons();
        List<String> shown = matches.size() > limit ? matches.subList(0, limit) : matches;

        return Dialog.create(factory -> factory.empty()
                .base(base(matches, shown, search))
                .type(DialogType.multiAction(buttons(player, shown), null, columns())));
    }

    private DialogBase base(List<String> matches, List<String> shown, String search) {
        ConfigManager cfg = plugin.getConfigManager();
        Component title = component(cfg.getRawText("worth-dialog.title", "<dark_gray><bold>Item Prices"));

        List<DialogBody> body = new ArrayList<>();
        if (matches.isEmpty()) {
            body.add(DialogBody.plainMessage(component(
                    cfg.getRawText("worth-dialog.no-matches", "<gray>No items match that search.")), BODY_WIDTH));
        } else if (shown.size() < matches.size()) {
            body.add(DialogBody.plainMessage(component(Text.fill(
                    cfg.getRawText("worth-dialog.truncated",
                            "<gray>Showing {shown} of {total} items. Search to narrow them down."),
                    "shown", shown.size(), "total", matches.size())), BODY_WIDTH));
        } else {
            body.add(DialogBody.plainMessage(component(Text.fill(
                    cfg.getRawText("worth-dialog.showing", "<gray>Showing all {total} items."),
                    "total", matches.size())), BODY_WIDTH));
        }

        return DialogBase.builder(title)
                .externalTitle(title)
                .canCloseWithEscape(true)
                .pause(false)
                .afterAction(DialogBase.DialogAfterAction.CLOSE)
                .body(List.copyOf(body))
                .inputs(List.of(DialogInput.text(SEARCH_KEY,
                                component(cfg.getRawText("worth-dialog.search-label", "Search")))
                        .width(INPUT_WIDTH)
                        .labelVisible(true)
                        .initial(search)
                        .maxLength(64)
                        .build()))
                .build();
    }

    private List<ActionButton> buttons(Player player, List<String> keys) {
        ConfigManager cfg = plugin.getConfigManager();
        List<ActionButton> buttons = new ArrayList<>(keys.size() + 1);

        buttons.add(ActionButton.create(
                component(cfg.getRawText("worth-dialog.search-button", "<yellow>Search")),
                component(cfg.getRawText("worth-dialog.search-tooltip", "Search the price list.")),
                80,
                DialogAction.customClick(this::reopenWithSearch, CALLBACK_OPTIONS)));

        for (String key : keys) {
            buttons.add(priceButton(player, cfg, key));
        }
        return List.copyOf(buttons);
    }

    /**
     * One priced item. The label carries the price so the list reads without
     * hovering; the tooltip carries the breakdown that the chest menu put in
     * the item's lore.
     */
    private ActionButton priceButton(Player player, ConfigManager cfg, String key) {
        PriceManager prices = plugin.getPriceManager();
        double base = prices.getPrice(key);
        String category = prices.getCategory(key);
        double multiplier = plugin.getMultiplierManager().getEffectiveMultiplier(player, category);

        Object[] placeholders = {
                "item", ItemNameFormatter.formatKey(key),
                "base", NumberFormatter.format(base),
                "multiplier", String.format("%.2f", multiplier),
                "price", NumberFormatter.format(base * multiplier),
                "category", cfg.getRawCategoryDisplayName(category)
        };

        Component label = component(Text.fill(
                cfg.getRawText("worth-dialog.entry", "<white>{item} <dark_gray>- <green>${price}"), placeholders));
        Component tooltip = component(Text.fill(
                cfg.getRawText("worth-dialog.entry-tooltip",
                        "Base ${base} x {multiplier} = ${price}"), placeholders));

        // Clicking an entry just reopens the list: the price screen is a
        // reference, not a place where anything is bought.
        return ActionButton.create(label, tooltip, buttonWidth(),
                DialogAction.customClick(this::reopenWithSearch, CALLBACK_OPTIONS));
    }

    private void reopenWithSearch(DialogResponseView view, Audience audience) {
        if (!(audience instanceof Player player)) {
            return;
        }
        String search = view.getText(SEARCH_KEY);
        // The click arrives off the player's own thread, so hop back before
        // sending them another screen.
        player.getScheduler().run(plugin, task -> open(player, search), null);
    }

    /** Every priced item whose name or category matches, cheapest lookup first. */
    private List<String> matchingKeys(String search) {
        String needle = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        PriceManager prices = plugin.getPriceManager();

        List<String> keys = new ArrayList<>();
        for (String key : prices.getAllItemKeys()) {
            if (prices.getPrice(key) <= 0) continue;
            if (!needle.isEmpty() && !matches(prices, key, needle)) continue;
            keys.add(key);
        }
        keys.sort(Comparator.comparing(ItemNameFormatter::formatKey, String.CASE_INSENSITIVE_ORDER));
        return keys;
    }

    private boolean matches(PriceManager prices, String key, String needle) {
        if (key.toLowerCase(Locale.ROOT).contains(needle)) return true;
        if (ItemNameFormatter.formatKey(key).toLowerCase(Locale.ROOT).contains(needle)) return true;
        String category = prices.getCategory(key);
        return category != null && category.toLowerCase(Locale.ROOT).contains(needle);
    }

    private Component component(String raw) {
        return LEGACY.deserialize(Text.legacy(raw));
    }

    private int maxButtons() {
        int configured = plugin.getConfig().getInt("worth-dialog.max-entries", DEFAULT_MAX_BUTTONS);
        return configured <= 0 ? DEFAULT_MAX_BUTTONS : configured;
    }

    private int columns() {
        int configured = plugin.getConfig().getInt("worth-dialog.columns", DEFAULT_COLUMNS);
        return Math.max(1, Math.min(8, configured));
    }

    private int buttonWidth() {
        int configured = plugin.getConfig().getInt("worth-dialog.entry-width", 200);
        return Math.max(40, Math.min(1024, configured));
    }
}
