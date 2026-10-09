package com.yourname.sellplugin.item;

import com.yourname.sellplugin.SellPlugin;
import com.yourname.sellplugin.util.Scheduler;
import com.yourname.sellplugin.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The sell axe: right-click a chest, barrel or shulker box with it and
 * everything sellable inside is sold.
 *
 * <p>It is styled after the server's Shard Axe and Shard Pickaxe - same purple
 * name, unbreakable, and by default the same self destruct a few days after it
 * is handed out. An axe is recognised by a tag in its data rather than by its
 * look, so renaming it or changing its model in config does not orphan the
 * ones already handed out.
 */
public final class SellAxe {

    /** Marks an item as a sell axe. Not tied to a plugin instance so the price lookup can check it too. */
    private static final NamespacedKey KEY_AXE = new NamespacedKey("sellplugin", "sell_axe");
    /** Epoch millis the axe self destructs at; absent means it never does. */
    private static final NamespacedKey KEY_EXPIRY = new NamespacedKey("sellplugin", "sell_axe_expiry");
    /** The countdown last written into the lore, so an unchanged one is not rewritten. */
    private static final NamespacedKey KEY_LABEL = new NamespacedKey("sellplugin", "sell_axe_label");

    private static final Pattern DURATION_PART = Pattern.compile("(\\d+)\\s*([dhms])");
    private static final long SWEEP_PERIOD_TICKS = 20L * 20L;

    private final SellPlugin plugin;

    public SellAxe(SellPlugin plugin) {
        this.plugin = plugin;
    }

    // ---------------------------------------------------------------
    // Recognising one
    // ---------------------------------------------------------------

    public static boolean isSellAxe(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(KEY_AXE, PersistentDataType.BYTE);
    }

    /** True when the axe has a self destruct time and it has passed. */
    public static boolean isExpired(ItemStack item, long now) {
        if (!isSellAxe(item)) return false;
        Long expiry = item.getItemMeta().getPersistentDataContainer().get(KEY_EXPIRY, PersistentDataType.LONG);
        return expiry != null && now >= expiry;
    }

    // ---------------------------------------------------------------
    // Making one
    // ---------------------------------------------------------------

    /**
     * A new sell axe. {@code lifetimeMs} of zero or less makes one that never
     * self destructs.
     */
    public ItemStack create(long lifetimeMs) {
        long now = System.currentTimeMillis();
        ItemStack item = new ItemStack(configuredMaterial());
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(KEY_AXE, PersistentDataType.BYTE, (byte) 1);
        if (lifetimeMs > 0) {
            pdc.set(KEY_EXPIRY, PersistentDataType.LONG, now + lifetimeMs);
        }
        style(meta, now);
        item.setItemMeta(meta);
        return item;
    }

    /** Writes the name, lore and finish onto {@code meta}. The caller commits it. */
    private void style(ItemMeta meta, long now) {
        int modelData = plugin.getConfig().getInt("sell-axe.custom-model-data", 3003);
        if (modelData > 0) meta.setCustomModelData(modelData);

        meta.displayName(line(plugin.getConfig().getString("sell-axe.name", "<#D580FF>Shard Sell Axe")));

        List<Component> lore = new ArrayList<>();
        for (String raw : configuredLore()) lore.add(line(raw));

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        Long expiry = pdc.get(KEY_EXPIRY, PersistentDataType.LONG);
        if (expiry != null) {
            String label = remainingLabel(expiry - now);
            for (String raw : expiryLore()) lore.add(line(raw.replace("{time}", label)));
            pdc.set(KEY_LABEL, PersistentDataType.STRING, label);
        }
        meta.lore(lore);

        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
        meta.setEnchantmentGlintOverride(plugin.getConfig().getBoolean("sell-axe.glint", true));
        if (meta instanceof Damageable damageable) damageable.setDamage(0);
    }

    private Component line(String raw) {
        return Text.component(raw).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    private Material configuredMaterial() {
        Material material = Material.matchMaterial(plugin.getConfig().getString("sell-axe.material", "NETHERITE_AXE"));
        return material != null && material.isItem() ? material : Material.NETHERITE_AXE;
    }

    private List<String> configuredLore() {
        List<String> lore = plugin.getConfig().getStringList("sell-axe.lore");
        return lore.isEmpty()
                ? List.of("<#AAAAAA>Sells chests, barrels and shulker boxes")
                : lore;
    }

    private List<String> expiryLore() {
        List<String> lore = plugin.getConfig().getStringList("sell-axe.expiry-lore");
        return lore.isEmpty() ? List.of("<#FF5555>Self Destruct:", "<white>{time}") : lore;
    }

    /** The configured lifetime of a newly given axe, in millis; zero for one that never expires. */
    public long defaultLifetimeMs() {
        long parsed = parseDuration(plugin.getConfig().getString("sell-axe.lifetime", "3d"));
        return Math.max(0L, parsed);
    }

    // ---------------------------------------------------------------
    // Self destruct
    // ---------------------------------------------------------------

    /** Starts the periodic pass that expires axes and keeps their countdown current. */
    public void startSweeper() {
        Scheduler.runGlobalTimer(plugin, () -> {
            long now = System.currentTimeMillis();
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                Scheduler.runEntity(plugin, player, () -> sweep(player, now));
            }
        }, SWEEP_PERIOD_TICKS);
    }

    /**
     * One pass over a player's inventory: removes expired axes and refreshes
     * the countdown on the rest, writing an item only when its text changed.
     */
    private void sweep(Player player, long now) {
        PlayerInventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack item = inventory.getItem(i);
            if (!isSellAxe(item)) continue;

            ItemMeta meta = item.getItemMeta();
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            Long expiry = pdc.get(KEY_EXPIRY, PersistentDataType.LONG);
            if (expiry == null) continue;

            if (now >= expiry) {
                inventory.setItem(i, null);
                announceDestroyed(player);
                continue;
            }

            String label = remainingLabel(expiry - now);
            if (label.equals(pdc.get(KEY_LABEL, PersistentDataType.STRING))) continue;
            style(meta, now);
            item.setItemMeta(meta);
            inventory.setItem(i, item);
        }
    }

    public void announceDestroyed(Player player) {
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1f, 1f);
        player.sendMessage(Text.legacy(plugin.getConfig().getString(
                "messages.sell-axe.destroyed", "<red>Your Shard Sell Axe has self destructed.")));
    }

    // ---------------------------------------------------------------
    // Durations
    // ---------------------------------------------------------------

    /**
     * Reads a duration such as {@code 3d}, {@code 12h}, {@code 1d12h} or
     * {@code 90m}. {@code permanent}, {@code never} and {@code 0} mean no
     * lifetime at all (0). Anything unreadable comes back as -1.
     */
    public static long parseDuration(String raw) {
        if (raw == null) return -1L;
        String text = raw.trim().toLowerCase(Locale.ROOT);
        if (text.isEmpty()) return -1L;
        if (text.equals("0") || text.equals("permanent") || text.equals("never") || text.equals("perm")) return 0L;

        Matcher matcher = DURATION_PART.matcher(text);
        long total = 0L;
        int end = 0;
        while (matcher.find()) {
            if (!text.substring(end, matcher.start()).isBlank()) return -1L;
            long amount = Long.parseLong(matcher.group(1));
            total += switch (matcher.group(2)) {
                case "d" -> amount * 86_400_000L;
                case "h" -> amount * 3_600_000L;
                case "m" -> amount * 60_000L;
                default -> amount * 1_000L;
            };
            end = matcher.end();
        }
        if (end == 0 || !text.substring(end).isBlank()) return -1L;
        return total;
    }

    /** "2d 14h 11m" / "14h 11m" / "11m", matching the Shard tools' countdown. */
    public static String remainingLabel(long ms) {
        if (ms <= 0L) return "0m";
        long minutes = ms / 60_000L;
        long d = minutes / 1440L;
        long h = (minutes % 1440L) / 60L;
        long m = minutes % 60L;
        StringBuilder sb = new StringBuilder(12);
        if (d > 0L) sb.append(d).append("d ");
        if (d > 0L || h > 0L) sb.append(h).append("h ");
        sb.append(m).append('m');
        return sb.toString();
    }
}
