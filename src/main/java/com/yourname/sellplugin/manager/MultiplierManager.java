package com.yourname.sellplugin.manager;

import com.yourname.sellplugin.SellPlugin;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class MultiplierManager {
    private final SellPlugin plugin;
    private final File dataFolder;

    // UUID -> (Category -> Money Earned)
    private final Map<UUID, Map<String, Double>> cache = new HashMap<>();

    // Leaderboard cache
    private List<LeaderboardEntry> leaderboardCache = null;
    private long leaderboardCacheTime = 0;
    private static final long LEADERBOARD_CACHE_TTL = 30_000L;

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

        // Persist player name for the leaderboard
        OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
        if (op.getName() != null) {
            config.set("name", op.getName());
        }

        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save data for " + uuid.toString());
        }

        leaderboardCache = null; // invalidate leaderboard cache
    }

    public void saveAll() {
        for (UUID uuid : cache.keySet()) {
            savePlayer(uuid);
        }
    }

    // -----------------------------------------------------------------------
    // Multiplier – geometric progression
    //
    //   cost[0]           = startMultiplier          (to reach 1.1x)
    //   cost[i]           = startMultiplier * factor^i
    //   cumulative[level] = sum of cost[0..level-1]
    //
    //   The returned value is stepped: 1.0, 1.1, 1.2, … up to maxMultiplier.
    // -----------------------------------------------------------------------
    public double getMultiplier(Player p, String category) {
        if (!cache.containsKey(p.getUniqueId())) loadPlayer(p.getUniqueId());

        double moneyEarned = cache.get(p.getUniqueId()).getOrDefault(category, 0.0);

        double start  = plugin.getConfigManager().getStartMultiplier();
        double factor = plugin.getConfigManager().getMultiplierFactor();
        double max    = plugin.getConfigManager().getMaxMultiplier();

        int maxLevel = (int) Math.round((max - 1.0) / 0.1);
        int level = 0;
        double cumulative = 0.0;
        double cost = start;

        while (level < maxLevel) {
            cumulative += cost;
            if (moneyEarned < cumulative) break;
            level++;
            cost *= factor;
        }

        return 1.0 + level * 0.1;
    }

    /**
     * Returns the player's total effective multiplier for a category,
     * which is the earned multiplier <em>plus</em> today's daily bonus (if any).
     * Use this for all sell calculations and display.
     */
    public double getEffectiveMultiplier(Player p, String category) {
        double earned = getMultiplier(p, category);
        DailyBonusManager dbm = plugin.getDailyBonusManager();
        if (dbm == null) return earned;
        return earned + dbm.getDailyBonus(category);
    }

    /**
     * Returns the cumulative money required to reach milestone {@code milestoneIndex}
     * (0-based). Index 0 = 1.0x (no cost). Index 1 = 1.1x (costs startMultiplier).
     */
    public double getCumulativeThreshold(int milestoneIndex) {
        if (milestoneIndex <= 0) return 0.0;
        double start  = plugin.getConfigManager().getStartMultiplier();
        double factor = plugin.getConfigManager().getMultiplierFactor();
        if (Math.abs(factor - 1.0) < 0.0001) {
            return start * milestoneIndex;
        }
        return start * (Math.pow(factor, milestoneIndex) - 1.0) / (factor - 1.0);
    }

    public void addEarnings(Player p, String category, double amount) {
        if (!cache.containsKey(p.getUniqueId())) loadPlayer(p.getUniqueId());

        Map<String, Double> stats = cache.get(p.getUniqueId());
        stats.put(category, stats.getOrDefault(category, 0.0) + amount);

        leaderboardCache = null; // invalidate leaderboard cache
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

    // -----------------------------------------------------------------------
    // Leaderboard
    // -----------------------------------------------------------------------

    public List<LeaderboardEntry> getLeaderboard() {
        long now = System.currentTimeMillis();
        if (leaderboardCache != null && (now - leaderboardCacheTime) < LEADERBOARD_CACHE_TTL) {
            return leaderboardCache;
        }
        leaderboardCache = buildLeaderboard();
        leaderboardCacheTime = now;
        return leaderboardCache;
    }

    private List<LeaderboardEntry> buildLeaderboard() {
        Map<UUID, Double> totals = new HashMap<>();
        Map<UUID, String> names  = new HashMap<>();

        // Add online/cached players first
        for (Map.Entry<UUID, Map<String, Double>> e : cache.entrySet()) {
            double total = e.getValue().values().stream().mapToDouble(Double::doubleValue).sum();
            totals.put(e.getKey(), total);
            OfflinePlayer op = Bukkit.getOfflinePlayer(e.getKey());
            if (op.getName() != null) names.put(e.getKey(), op.getName());
        }

        // Read remaining player files from disk
        if (dataFolder.exists()) {
            File[] files = dataFolder.listFiles((d, n) -> n.endsWith(".yml"));
            if (files != null) {
                for (File file : files) {
                    try {
                        UUID uuid = UUID.fromString(file.getName().replace(".yml", ""));
                        if (totals.containsKey(uuid)) continue;

                        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
                        double total = 0.0;
                        if (cfg.contains("stats")) {
                            for (String cat : cfg.getConfigurationSection("stats").getKeys(false)) {
                                total += cfg.getDouble("stats." + cat);
                            }
                        }
                        totals.put(uuid, total);

                        String name = cfg.getString("name");
                        if (name == null) {
                            OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
                            name = op.getName();
                        }
                        if (name != null) names.put(uuid, name);
                    } catch (IllegalArgumentException ignored) {
                        // file name isn't a valid UUID – skip
                    }
                }
            }
        }

        List<LeaderboardEntry> entries = new ArrayList<>();
        for (Map.Entry<UUID, Double> e : totals.entrySet()) {
            String name = names.getOrDefault(e.getKey(), "Unknown");
            entries.add(new LeaderboardEntry(e.getKey(), name, e.getValue()));
        }
        entries.sort((a, b) -> Double.compare(b.totalEarnings, a.totalEarnings));
        return entries;
    }

    // -----------------------------------------------------------------------
    // Inner classes
    // -----------------------------------------------------------------------

    public static class LeaderboardEntry {
        public final UUID uuid;
        public final String name;
        public final double totalEarnings;

        public LeaderboardEntry(UUID uuid, String name, double totalEarnings) {
            this.uuid = uuid;
            this.name = name;
            this.totalEarnings = totalEarnings;
        }
    }
}
