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
    
    // UUID -> (Category -> Items Sold)
    private final Map<UUID, Map<String, Integer>> cache = new HashMap<>();

    public MultiplierManager(SellPlugin plugin) {
        this.plugin = plugin;
        this.dataFolder = new File(plugin.getDataFolder(), "data");
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
    }

    public void loadPlayer(UUID uuid) {
        File file = new File(dataFolder, uuid.toString() + ".yml");
        Map<String, Integer> stats = new HashMap<>();
        
        if (file.exists()) {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            if (config.contains("stats")) {
                for (String category : config.getConfigurationSection("stats").getKeys(false)) {
                    stats.put(category, config.getInt("stats." + category));
                }
            }
        }
        cache.put(uuid, stats);
    }

    public void savePlayer(UUID uuid) {
        Map<String, Integer> stats = cache.get(uuid);
        if (stats == null || stats.isEmpty()) return;

        File file = new File(dataFolder, uuid.toString() + ".yml");
        YamlConfiguration config = new YamlConfiguration();
        
        for (Map.Entry<String, Integer> entry : stats.entrySet()) {
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
        
        Map<String, Integer> stats = cache.get(p.getUniqueId());
        int itemsSold = stats.getOrDefault(category, 0);
        
        double step = plugin.getConfigManager().getMultiplierStep();
        return 1.0 + (itemsSold * step);
    }

    public void addSales(Player p, String category, int amount) {
        if (!cache.containsKey(p.getUniqueId())) loadPlayer(p.getUniqueId());
        
        Map<String, Integer> stats = cache.get(p.getUniqueId());
        stats.put(category, stats.getOrDefault(category, 0) + amount);
    }
    
    public Map<String, Integer> getStats(Player p) {
        if (!cache.containsKey(p.getUniqueId())) loadPlayer(p.getUniqueId());
        return cache.get(p.getUniqueId());
    }
}
