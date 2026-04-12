package com.sellplugin.managers;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public class ConfigManager {
    private JavaPlugin plugin;
    private FileConfiguration config;
    private boolean soundsEnabled;
    private boolean prefixEnabled;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfig();
        loadConfiguration();
    }

    private void loadConfiguration() {
        if (!config.contains("sounds-enabled")) {
            config.set("sounds-enabled", true);
        }
        if (!config.contains("prefix-enabled")) {
            config.set("prefix-enabled", true);
        }
        if (!config.contains("sound-type")) {
            config.set("sound-type", "ENTITY_PLAYER_LEVELUP");
        }
        if (!config.contains("action-bar-color")) {
            config.set("action-bar-color", "GREEN");
        }
        this.soundsEnabled = config.getBoolean("sounds-enabled", true);
        this.prefixEnabled = config.getBoolean("prefix-enabled", true);
        plugin.saveConfig();
    }

    public boolean areSoundsEnabled() {
        return soundsEnabled;
    }

    public boolean isPrefixEnabled() {
        return prefixEnabled;
    }

    public String getSoundType() {
        return config.getString("sound-type", "ENTITY_PLAYER_LEVELUP");
    }

    public String getActionBarColor() {
        return config.getString("action-bar-color", "GREEN");
    }

    public void setSoundsEnabled(boolean enabled) {
        this.soundsEnabled = enabled;
        config.set("sounds-enabled", enabled);
        plugin.saveConfig();
    }

    public void setPrefixEnabled(boolean enabled) {
        this.prefixEnabled = enabled;
        config.set("prefix-enabled", enabled);
        plugin.saveConfig();
    }
}
