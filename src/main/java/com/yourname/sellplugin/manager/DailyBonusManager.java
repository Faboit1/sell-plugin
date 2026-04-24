package com.yourname.sellplugin.manager;

import com.yourname.sellplugin.SellPlugin;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.*;

/**
 * Manages daily category bonuses.
 * <p>
 * Each day a configurable number of random categories receive a bonus multiplier
 * that is <em>added</em> (not multiplied) to the player's current earned multiplier.
 * Bonuses are persisted to {@code daily-bonus.yml} and automatically re-rolled
 * when a new day is first detected.
 */
public class DailyBonusManager {

    private final SellPlugin plugin;
    private final File bonusFile;

    /** The calendar date (ISO string) for the currently stored bonuses. */
    private String currentDate = "";

    /** Categories that have the active daily bonus. */
    private Set<String> boostedCategories = new HashSet<>();

    public DailyBonusManager(SellPlugin plugin) {
        this.plugin = plugin;
        this.bonusFile = new File(plugin.getDataFolder(), "daily-bonus.yml");
        load();
    }

    // ── Load / save ──────────────────────────────────────────────────────────

    private void load() {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(bonusFile);
        String storedDate = config.getString("date", "");
        String today = LocalDate.now().toString();

        if (!today.equals(storedDate)) {
            rollNewBonuses(today);
        } else {
            currentDate = storedDate;
            boostedCategories = new HashSet<>(config.getStringList("boosted-categories"));
        }
    }

    private void save() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("date", currentDate);
        config.set("boosted-categories", new ArrayList<>(boostedCategories));
        try {
            config.save(bonusFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save daily-bonus.yml: " + e.getMessage());
        }
    }

    // ── Daily roll ───────────────────────────────────────────────────────────

    /**
     * Picks a new set of boosted categories for {@code date} and persists it.
     */
    private void rollNewBonuses(String date) {
        currentDate = date;
        List<String> allCategories = new ArrayList<>(plugin.getPriceManager().getCategories());
        Collections.shuffle(allCategories);

        int count = plugin.getConfigManager().getDailyBoostedCount();
        boostedCategories = new HashSet<>();
        for (int i = 0; i < Math.min(count, allCategories.size()); i++) {
            boostedCategories.add(allCategories.get(i));
        }
        save();

        plugin.getLogger().info("Daily bonus re-rolled for " + date
                + " → boosted: " + boostedCategories);
    }

    // ── Public API ───────────────────────────────────────────────────────────

    /**
     * Returns the flat multiplier bonus that should be <em>added</em> to the
     * player's earned multiplier for the given category today.
     * Returns 0.0 if the category is not boosted.
     */
    public double getDailyBonus(String category) {
        checkAndRollIfNeeded();
        return boostedCategories.contains(category)
                ? plugin.getConfigManager().getDailyBonusAmount()
                : 0.0;
    }

    /**
     * Returns an unmodifiable view of the currently boosted category IDs.
     * Triggers a lazy reset if necessary.
     */
    public Set<String> getBoostedCategories() {
        checkAndRollIfNeeded();
        return Collections.unmodifiableSet(boostedCategories);
    }

    /** Rolls new bonuses if the current stored date no longer matches today. */
    private void checkAndRollIfNeeded() {
        String today = LocalDate.now().toString();
        if (!today.equals(currentDate)) {
            rollNewBonuses(today);
        }
    }
}
