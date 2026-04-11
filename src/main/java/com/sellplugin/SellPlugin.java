package com.sellplugin;

import org.bukkit.plugin.java.JavaPlugin;

public class SellPlugin extends JavaPlugin {
    @Override
    public void onEnable() {
        // Load configuration
        this.saveDefaultConfig();

        // Register commands
        this.getCommand("sellall").setExecutor(new SellAllCommand());

        // Register events
        getServer().getPluginManager().registerEvents(new PlayerSellListener(), this);

        // Initialize managers
        ItemManager.initialize();
        // Add other initializations as needed
    }

    @Override
    public void onDisable() {
        // Save data or clean up resources if necessary
    }
}