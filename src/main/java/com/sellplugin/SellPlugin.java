package com.sellplugin;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import net.milkbowl.vault.economy.Economy;
import com.sellplugin.managers.ConfigManager;
import com.sellplugin.managers.SellManager;
import com.sellplugin.listeners.GUIListener;
import com.sellplugin.commands.SellAllCommand;

public class SellPlugin extends JavaPlugin {
    private Economy economy;
    private ConfigManager configManager;
    private SellManager sellManager;

    @Override
    public void onEnable() {
        this.saveDefaultConfig();
        configManager = new ConfigManager(this);

        if (!setupEconomy()) {
            getLogger().severe("Vault and an Economy plugin are required!");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        sellManager = new SellManager(this, economy, 
            configManager.areSoundsEnabled(), 
            configManager.isPrefixEnabled());

        this.getCommand("sellall").setExecutor(new SellAllCommand());
        getServer().getPluginManager().registerEvents(new GUIListener(sellManager), this);

        getLogger().info("SellPlugin v2.0.0 has been enabled!");
    }

    @Override
    public void onDisable() {
        getLogger().info("SellPlugin has been disabled!");
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        economy = rsp.getProvider();
        return economy != null;
    }

    public Economy getEconomy() {
        return economy;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public SellManager getSellManager() {
        return sellManager;
    }
}
