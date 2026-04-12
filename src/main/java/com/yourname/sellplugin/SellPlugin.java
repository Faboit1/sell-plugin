package com.yourname.sellplugin;

import com.yourname.sellplugin.command.SellAllCommand;
import com.yourname.sellplugin.command.SellCommand;
import com.yourname.sellplugin.economy.EconomyManager;
import com.yourname.sellplugin.gui.GUIListener;
import com.yourname.sellplugin.manager.ConfigManager;
import com.yourname.sellplugin.manager.MultiplierManager;
import com.yourname.sellplugin.manager.PriceManager;
import org.bukkit.plugin.java.JavaPlugin;

public class SellPlugin extends JavaPlugin {

    private EconomyManager economyManager;
    private ConfigManager configManager;
    private PriceManager priceManager;
    private MultiplierManager multiplierManager;

    @Override
    public void onEnable() {
        // Initialize Config
        saveDefaultConfig();
        configManager = new ConfigManager(this);

        // Initialize Managers
        priceManager = new PriceManager(this);
        priceManager.loadPrices();
        
        multiplierManager = new MultiplierManager(this);

        economyManager = new EconomyManager(this);
        if (!economyManager.setupEconomy()) {
            getLogger().severe("No economy plugin found (Vault or CoinsEngine)! Disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Register Commands & Events
        getCommand("sell").setExecutor(new SellCommand(this));
        getCommand("sellall").setExecutor(new SellAllCommand(this));
        getServer().getPluginManager().registerEvents(new GUIListener(this), this);

        getLogger().info("SellPlugin has been enabled successfully.");
    }

    @Override
    public void onDisable() {
        if (multiplierManager != null) {
            multiplierManager.saveAll();
        }
        getLogger().info("SellPlugin has been disabled.");
    }

    public EconomyManager getEconomyManager() { return economyManager; }
    public ConfigManager getConfigManager() { return configManager; }
    public PriceManager getPriceManager() { return priceManager; }
    public MultiplierManager getMultiplierManager() { return multiplierManager; }
}
