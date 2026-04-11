package com.yourname.sellplugin.economy;

import com.yourname.sellplugin.SellPlugin;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import su.nightexpress.coinsengine.api.CoinsEngineAPI;
import su.nightexpress.coinsengine.api.currency.ICurrency;

public class EconomyManager {
    private final SellPlugin plugin;
    private Economy vaultEconomy = null;

    public EconomyManager(SellPlugin plugin) {
        this.plugin = plugin;
        setupVault();
    }

    // Fix: Changed to boolean to stop 'void' type error in SellPlugin.java
    public boolean setupEconomy() {
        return setupVault();
    }

    private boolean setupVault() {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        vaultEconomy = rsp.getProvider();
        return vaultEconomy != null;
    }

    /**
     * Adds money to a player's balance. 
     * Returns true if successful.
     */
    public boolean deposit(Player player, double amount) {
        String economyMode = plugin.getConfig().getString("economy-mode", "VAULT").toUpperCase();

        if (economyMode.equals("COINSENGINE")) {
            // Fix: In many CoinsEngine versions, the interface is ICurrency
            // And the method is often getCurrency() or just using the API directly
            ICurrency currency = CoinsEngineAPI.getCurrencyManager().getCurrency("money"); // Try "money" as default
            if (currency == null) {
                currency = CoinsEngineAPI.getCurrencyManager().getMainCurrency();
            }
            
            if (currency != null) {
                // Fix: Using the modern addition method for CoinsEngine
                currency.add(player, amount);
                return true;
            } else {
                plugin.getLogger().warning("CoinsEngine is enabled but no Currency was found!");
                return false;
            }
        } else {
            if (vaultEconomy != null) {
                vaultEconomy.depositPlayer(player, amount);
                return true;
            }
            return false;
        }
    }
}
