package com.yourname.sellplugin.economy;

import com.yourname.sellplugin.SellPlugin;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
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

    private Currency getCoinsEngineCurrency() {
        FileConfiguration config = plugin.getConfig();

        // Set this in config.yml, for example:
        // economy-mode: VAULT
        // coinsengine-currency-id: coins
        String currencyId = config.getString("coinsengine-currency-id", "coins");

        if (currencyId == null || currencyId.isBlank()) {
            currencyId = "coins";
        }

        return CoinsEngineAPI.getCurrency(currencyId);
    }

    /**
     * Whether the configured provider could pay out right now.
     *
     * <p>Selling checks this before it takes anything, because once items have
     * been handed to open orders or removed from the inventory there is nothing
     * sensible left to roll back to.
     */
    public boolean isAvailable() {
        String economyMode = plugin.getConfig().getString("economy-mode", "VAULT").toUpperCase();
        if (economyMode.equals("COINSENGINE")) {
            return Bukkit.getPluginManager().getPlugin("CoinsEngine") != null && getCoinsEngineCurrency() != null;
        }
        return vaultEconomy != null;
    }

    /**
     * Adds money to a player's balance.
     * Returns true if successful.
     */
    public boolean deposit(Player player, double amount) {
        String economyMode = plugin.getConfig().getString("economy-mode", "VAULT").toUpperCase();

        if (economyMode.equals("COINSENGINE")) {
            if (Bukkit.getPluginManager().getPlugin("CoinsEngine") == null) {
                plugin.getLogger().warning("CoinsEngine is not installed.");
                return false;
            }

            Currency currency = getCoinsEngineCurrency();
            if (currency == null) {
                plugin.getLogger().warning("CoinsEngine currency not found. Check coinsengine-currency-id in config.yml.");
                return false;
            }

            CoinsEngineAPI.addBalance(player, currency, amount);
            return true;
        }

        if (vaultEconomy != null) {
            vaultEconomy.depositPlayer(player, amount);
            return true;
        }

        return false;
    }
}
