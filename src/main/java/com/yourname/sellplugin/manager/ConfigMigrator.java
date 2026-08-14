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
    public static final int CURRENT_VERSION = 1;

    /** Keys that used to exist but have been removed from the plugin. */
    private static final List<String> OBSOLETE_KEYS = List.of(
            "daily-bonus"
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
