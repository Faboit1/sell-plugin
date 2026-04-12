package com.yourname.sellplugin.manager;

import com.yourname.sellplugin.SellPlugin;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class PriceManager {
    private final SellPlugin plugin;
    private final Map<String, Double> prices = new HashMap<>();
    private final Map<String, String> itemCategories = new HashMap<>();
    private final Set<String> categories = new java.util.HashSet<>();

    public PriceManager(SellPlugin plugin) {
        this.plugin = plugin;
    }

    public void loadPrices() {
        prices.clear();
        itemCategories.clear();
        categories.clear();

        File file = new File(plugin.getDataFolder(), "price.yml");
        if (!file.exists()) {
            plugin.saveResource("price.yml", false); // Creates a default if it doesn't exist
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);

        for (String category : config.getKeys(false)) {
            categories.add(category);
            for (String itemKey : config.getConfigurationSection(category).getKeys(false)) {
                double price = config.getDouble(category + "." + itemKey);
                // Store exactly as it is in the config (e.g., "DIAMOND_SWORD" or "LINGERING_POTION:NIGHT_VISION")
                String formattedKey = itemKey.toUpperCase();
                prices.put(formattedKey, price);
                itemCategories.put(formattedKey, category);
            }
        }
        plugin.getLogger().info("Loaded " + prices.size() + " prices from price.yml.");
    }

    // Identifies the string key from the ItemStack
    public String getItemKey(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return null;

        String key = item.getType().name();
        ItemMeta meta = item.getItemMeta();

        // Check for Potions to match your config layout (LINGERING_POTION:NIGHT_VISION)
        if (meta instanceof PotionMeta) {
            try {
                PotionMeta pMeta = (PotionMeta) meta;
                // Note: getBasePotionData is deprecated in 1.20+. 
                // Let me know if your server is 1.20.5+ and this throws an error.
                if (pMeta.getBasePotionData() != null) {
                    key = key + ":" + pMeta.getBasePotionData().getType().name();
                }
            } catch (Exception e) {
                // Failsafe fallback
            }
        }

        return key;
    }

    public double getPrice(String itemKey) {
        return prices.getOrDefault(itemKey, 0.0);
    }

    public String getCategory(String itemKey) {
        return itemCategories.get(itemKey);
    }
    
    public Set<String> getCategories() {
        return categories;
    }

    /** Returns all loaded item keys (e.g. "DIAMOND", "LINGERING_POTION:NIGHT_VISION"). */
    public Set<String> getAllItemKeys() {
        return prices.keySet();
    }
}
