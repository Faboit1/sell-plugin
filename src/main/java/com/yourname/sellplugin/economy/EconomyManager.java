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

    /**
     * Initializes the economy providers.
     * Returns true if at least one provider is found.
     */
    public boolean setupEconomy() {
        boolean vaultReady = setupVault();
        boolean coinsEngineReady = Bukkit.getPluginManager().getPlugin("CoinsEngine") != null;
        
        return vaultReady || coinsEngineReady;
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
            // CoinsEngine 1.21.1 usually uses ICurrency via getCurrencyManager
            ICurrency currency = CoinsEngineAPI.getCurrencyManager().getCurrency("money"); 
            if (currency == null) {
                currency = CoinsEngineAPI.getCurrencyManager().getMainCurrency();
            }
            
            if (currency != null) {
                // CoinsEngine .add() typically takes (Player, double)
                currency.add(player, amount);
                return true;
            } else {
                plugin.getLogger().warning("CoinsEngine is enabled but no Currency was found!");
                return false;
            }
        } else {
            // Default to Vault
            if (vaultEconomy != null) {
                vaultEconomy.depositPlayer(player, amount);
                return true;
            }
            return false;
        }
    }
}
