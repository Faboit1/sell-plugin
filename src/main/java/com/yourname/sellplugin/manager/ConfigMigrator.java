package com.yourname.sellplugin.manager;

import com.yourname.sellplugin.SellPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Automatically brings an existing {@code config.yml} up to date whenever the
 * bundled schema version is newer than the one on disk (or the file has no
 * version at all, which is treated as "very old").
 *
 * <p>Before changing anything it writes a timestamped backup next to the config
 * so nothing is ever lost. Migration then:
 * <ul>
 *   <li>adds any options the user is missing (copied from the bundled defaults),</li>
 *   <li>removes options that no longer exist, and</li>
 *   <li>stamps the current schema version.</li>
 * </ul>
 *
 * <p>Note: rewriting the file through Bukkit strips hand-written comments. The
 * pre-migration backup keeps the original (comments and all) intact.
 */
public class ConfigMigrator {

    /** Bump this whenever the bundled config.yml gains or drops options. */
    public static final int CURRENT_VERSION = 2;

    /** Keys that used to exist but have been removed from the plugin. */
    private static final List<String> OBSOLETE_KEYS = List.of(
            "daily-bonus",
            // The one-button /sellall menu is gone; /sellall opens the
            // confirmation directly now.
            "sell-all-gui",
            // Menu titles moved under messages.* along with everything else.
            "gui",
            "sell-multi-gui",
            "worth-gui",
            // Lore is written a whole line at a time now, so the fragments
            // these keys held no longer have anywhere to go.
            "messages.shop.sell-value-label",
            "messages.shop.sell-empty",
            "messages.shop.sell-click",
            "messages.sellmulti.earned-label",
            "messages.sellmulti.effective-label",
            "messages.sellmulti.click-to-view",
            "messages.category-items.title-suffix",
            "messages.category-items.category-label",
            "messages.category-items.items-label",
            "messages.category-items.earn-label",
            "messages.category-items.no-items-to-sell",
            "messages.category-progress.back-lore",
            "messages.category-progress.node-multiplier-suffix",
            "messages.category-progress.node-status-label",
            "messages.category-progress.node-status-completed",
            "messages.category-progress.node-status-in-progress",
            "messages.category-progress.node-status-locked",
            "messages.category-progress.node-click-to-view",
            "messages.category-progress.node-earned-label",
            "messages.category-progress.node-required-label",
            "messages.category-progress.node-progress-label",
            "messages.category-progress.node-need-label",
            "messages.category-progress.node-need-suffix",
            "messages.confirm-sell.title-prefix",
            "messages.confirm-sell.items-label",
            "messages.confirm-sell.value-label",
            "messages.confirm-sell.confirm-lore-line1",
            "messages.confirm-sell.confirm-lore-line2",
            "messages.confirm-sell.confirm-lore-line3",
            "messages.confirm-sell.confirm-earn",
            "messages.confirm-sell-all.items-label",
            "messages.confirm-sell-all.value-label",
            "messages.confirm-sell-all.confirm-earn",
            "messages.top-sell.total-earned-label"
    );

    /**
     * Text the plugin used to ship, paired with the key that held it.
     *
     * <p>Small caps and boldness used to be applied in code, so these values
     * were written as bare lowercase and only looked right once the plugin had
     * decorated them. Now that nothing is decorated, a value left at one of
     * these is cleared so the new MiniMessage default takes its place. A value
     * somebody actually chose does not match, and is kept untouched.
     */
    private static final Map<String, String> SUPERSEDED_DEFAULTS = Map.ofEntries(
            Map.entry("messages.shop.title", "put items here to sell"),
            Map.entry("messages.shop.sell-button-name", "&a&lSell"),
            Map.entry("messages.sellmulti.title", "&8&lMultipliers"),
            Map.entry("messages.category-items.total-items", "&7Total items: {count}"),
            Map.entry("messages.category-items.page-indicator", "&fPage {page} / {total}"),
            Map.entry("messages.confirm-sell-all.title", "confirm sell all"),
            Map.entry("messages.confirm-sell.cancel-lore", "go back without selling."),
            Map.entry("messages.confirm-sell-all.cancel-lore", "go back without selling."),
            Map.entry("messages.top-sell.title", "top sellers"),
            Map.entry("messages.top-sell.close-lore", "close the leaderboard."),
            Map.entry("messages.top-sell.prev-page-lore", "previous page."),
            Map.entry("messages.top-sell.next-page-lore", "next page."),
            Map.entry("messages.top-sell.total-players", "total players: "),
            Map.entry("messages.top-sell.page-indicator", "page {page} / {total}"),
            Map.entry("worth.format", "&7Worth &a&l${worth}"),
            Map.entry("worth.order-format", "&7Worth &a&l~${worth}")
    );

    private final SellPlugin plugin;

    public ConfigMigrator(SellPlugin plugin) {
        this.plugin = plugin;
    }

    /** Runs migration if needed. Safe to call every startup. */
    public void migrate() {
        File configFile = new File(plugin.getDataFolder(), "config.yml");
        // saveDefaultConfig() runs before us, so a brand-new install already has
        // the current file – nothing to migrate.
        if (!configFile.exists()) return;

        FileConfiguration config = plugin.getConfig();
        int version = config.getInt("config-version", 0);
        if (version >= CURRENT_VERSION) return;

        plugin.getLogger().info("Old config detected (version " + version
                + "); migrating to version " + CURRENT_VERSION + ".");

        backup(configFile, version);

        // Clear values still sitting at a superseded default, so the merge
        // below replaces them with the current one.
        for (Map.Entry<String, String> superseded : SUPERSEDED_DEFAULTS.entrySet()) {
            if (superseded.getValue().equals(config.getString(superseded.getKey()))) {
                config.set(superseded.getKey(), null);
            }
        }

        // Merge any missing options from the bundled defaults.
        try (InputStream defStream = plugin.getResource("config.yml")) {
            if (defStream != null) {
                YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                        new InputStreamReader(defStream, StandardCharsets.UTF_8));
                config.setDefaults(defaults);
                config.options().copyDefaults(true);
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Could not read bundled config defaults: " + e.getMessage());
        }

        // Drop options that no longer exist.
        for (String key : OBSOLETE_KEYS) {
            config.set(key, null);
        }

        // Stamp the new version and persist.
        config.set("config-version", CURRENT_VERSION);
        plugin.saveConfig();

        plugin.getLogger().info("Config migration complete. A backup of your old "
                + "config was saved in the plugin folder.");
    }

    private void backup(File configFile, int version) {
        try {
            String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
            File backup = new File(plugin.getDataFolder(),
                    "config-backup-v" + version + "-" + stamp + ".yml");
            Files.copy(configFile.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
            plugin.getLogger().info("Backed up existing config to " + backup.getName() + ".");
        } catch (IOException e) {
            plugin.getLogger().warning("Could not back up config.yml before migrating: "
                    + e.getMessage());
        }
    }
}
