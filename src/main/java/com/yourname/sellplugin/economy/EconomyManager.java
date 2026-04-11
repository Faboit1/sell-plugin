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
     * Adds money to a player's balance using either Vault or CoinsEngine.
     * @param player The player to reward
     * @param amount The amount of money to add
     */
    public void addMoney(Player player, double amount) {
        String economyMode = plugin.getConfig().getString("economy-mode", "VAULT").toUpperCase();

        if (economyMode.equals("COINSENGINE")) {
            // Fix: Changed getDefaultCurrency() to getMainCurrency() for modern CoinsEngine versions
            Currency currency = CoinsEngineAPI.getCurrencyManager().getMainCurrency();
            if (currency != null) {
                currency.add(player, amount);
            } else {
                plugin.getLogger().warning("CoinsEngine is enabled but no Main Currency was found!");
            }
        } else {
            // Default to Vault
            if (vaultEconomy != null) {
                vaultEconomy.depositPlayer(player, amount);
            } else {
                plugin.getLogger().severe("Vault is selected as economy but Vault/an Economy plugin is missing!");
            }
        }
    }
}
