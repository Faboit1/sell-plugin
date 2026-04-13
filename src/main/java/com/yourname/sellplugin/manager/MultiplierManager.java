package com.yourname.sellplugin.manager;

import com.yourname.sellplugin.SellPlugin;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class MultiplierManager {
    private final SellPlugin plugin;
    private final File dataFolder;
    
    // UUID -> (Category -> Money Earned)
    private final Map<UUID, Map<String, Double>> cache = new HashMap<>();

    public MultiplierManager(SellPlugin plugin) {
        this.plugin = plugin;
        this.dataFolder = new File(plugin.getDataFolder(), "data");
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
    }

    public void loadPlayer(UUID uuid) {
        File file = new File(dataFolder, uuid.toString() + ".yml");
        Map<String, Double> stats = new HashMap<>();
        
        if (file.exists()) {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            if (config.contains("stats")) {
                for (String category : config.getConfigurationSection("stats").getKeys(false)) {
                    stats.put(category, config.getDouble("stats." + category));
                }
            }
        }
        cache.put(uuid, stats);
    }

    public void savePlayer(UUID uuid) {
        Map<String, Double> stats = cache.get(uuid);
        if (stats == null || stats.isEmpty()) return;

        File file = new File(dataFolder, uuid.toString() + ".yml");
        YamlConfiguration config = new YamlConfiguration();
        
        for (Map.Entry<String, Double> entry : stats.entrySet()) {
            config.set("stats." + entry.getKey(), entry.getValue());
        }

        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save data for " + uuid.toString());
        }
    }

    public void saveAll() {
        for (UUID uuid : cache.keySet()) {
            savePlayer(uuid);
        }
    }

    public double getMultiplier(Player p, String category) {
        if (!cache.containsKey(p.getUniqueId())) loadPlayer(p.getUniqueId());
        
        Map<String, Double> stats = cache.get(p.getUniqueId());
        double moneyEarned = stats.getOrDefault(category, 0.0);
        
        double step = plugin.getConfigManager().getMultiplierStep();
        double max = plugin.getConfigManager().getMaxMultiplier();
        double mult = 1.0 + (moneyEarned * step);
        return Math.min(mult, max);
    }

    public void addEarnings(Player p, String category, double amount) {
        if (!cache.containsKey(p.getUniqueId())) loadPlayer(p.getUniqueId());
        
        Map<String, Double> stats = cache.get(p.getUniqueId());
        stats.put(category, stats.getOrDefault(category, 0.0) + amount);
    }
    
    public Map<String, Double> getStats(Player p) {
        if (!cache.containsKey(p.getUniqueId())) loadPlayer(p.getUniqueId());
        return cache.get(p.getUniqueId());
    }

    /** Returns the total money earned in a specific category. */
    public double getMoneyEarned(Player p, String category) {
        if (!cache.containsKey(p.getUniqueId())) loadPlayer(p.getUniqueId());
        return cache.get(p.getUniqueId()).getOrDefault(category, 0.0);
    }
}
