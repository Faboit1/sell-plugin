package com.yourname.sellplugin.manager;

import com.yourname.sellplugin.SellPlugin;
import com.yourname.sellplugin.util.Scheduler;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks which players have chosen to hide the cosmetic worth tooltip
 * ({@code /showworth}). Worth is shown by default (subject to the global
 * {@code worth.enabled} config), so we only need to persist the set of players
 * who have opted <em>out</em>.
 *
 * <p>Reads happen from the ProtocolLib packet thread, so the backing set is
 * concurrent; writes to disk are pushed off-thread via the async scheduler so
 * this is safe on Folia as well as Paper.
 */
public class WorthVisibilityManager {

    private final SellPlugin plugin;
    private final File file;

    /** UUIDs of players who have hidden the worth tooltip. */
    private final Set<UUID> hidden = ConcurrentHashMap.newKeySet();

    public WorthVisibilityManager(SellPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "worth-visibility.yml");
        load();
    }

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        for (String raw : config.getStringList("hidden")) {
            try {
                hidden.add(UUID.fromString(raw));
            } catch (IllegalArgumentException ignored) {
                // skip malformed UUID entries
            }
        }
    }

    /** Persists the current set to disk on the calling thread. */
    public void saveNow() {
        YamlConfiguration config = new YamlConfiguration();
        List<String> list = new ArrayList<>(hidden.size());
        for (UUID uuid : hidden) {
            list.add(uuid.toString());
        }
        config.set("hidden", list);
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save worth-visibility.yml: " + e.getMessage());
        }
    }

    private void saveAsync() {
        Scheduler.runAsync(plugin, this::saveNow);
    }

    /** @return {@code true} if the player has hidden the worth tooltip. */
    public boolean isHidden(UUID uuid) {
        return hidden.contains(uuid);
    }

    /** @return {@code true} if the worth tooltip should be shown to this player. */
    public boolean isVisible(UUID uuid) {
        return plugin.getConfigManager().isWorthEnabled() && !hidden.contains(uuid);
    }

    /**
     * Sets whether the worth tooltip is shown for {@code uuid}.
     *
     * @return {@code true} if this changed the stored state.
     */
    public boolean setVisible(UUID uuid, boolean visible) {
        boolean changed = visible ? hidden.remove(uuid) : hidden.add(uuid);
        if (changed) {
            saveAsync();
        }
        return changed;
    }

    /**
     * Flips the current preference for {@code uuid}.
     *
     * @return the new visibility state ({@code true} = now shown).
     */
    public boolean toggle(UUID uuid) {
        boolean nowVisible = hidden.contains(uuid); // was hidden -> becomes visible
        setVisible(uuid, nowVisible);
        return nowVisible;
    }
}
