package com.yourname.sellplugin.economy;

import com.yourname.sellplugin.SellPlugin;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import su.nightexpress.coinsengine.api.CoinsEngineAPI;
import su.nightexpress.coinsengine.api.currency.Currency;

public class EconomyManager {
    private final SellPlugin plugin;
    private Economy vaultEconomy = null;

    public EconomyManager(SellPlugin plugin) {
        this.plugin = plugin;
        setupVault();
    }

    // Added this back because SellPlugin.java calls it
    public void setupEconomy() {
        setupVault();
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
     * Renamed to deposit to match what GUIListener expects.
     */
    public void deposit(Player player, double amount) {
        String economyMode = plugin.getConfig().getString("economy-mode", "VAULT").toUpperCase();

        if (economyMode.equals("COINSENGINE")) {
            // Updated for the specific CoinsEngine API version in your libs
            Currency currency = CoinsEngineAPI.getCurrencyManager().getMainCurrency();
            if (currency != null) {
                // In some versions it is .addBalance(), in others .add()
                // Based on your error, we will try the direct amount addition
                currency.add(player.getUniqueId().toString(), amount);
            } else {
                plugin.getLogger().warning("CoinsEngine is enabled but no Main Currency was found!");
            }
        } else {
            if (vaultEconomy != null) {
                vaultEconomy.depositPlayer(player, amount);
            }
        }
    }
}
