package com.yourname.sellplugin.economy;

import com.yourname.sellplugin.SellPlugin;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

// Note: To compile this cleanly with both dependencies without errors,
// Ensure you have VaultAPI and CoinsEngineAPI in your build path.
import su.nightexpress.coinsengine.api.CoinsEngineAPI;
import su.nightexpress.coinsengine.api.currency.Currency;

public class EconomyManager {
    private final SellPlugin plugin;
    private Economy vaultEcon = null;
    private boolean useCoinsEngine = false;

    public EconomyManager(SellPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean setupEconomy() {
        if (Bukkit.getPluginManager().getPlugin("CoinsEngine") != null) {
            useCoinsEngine = true;
            plugin.getLogger().info("Successfully hooked into CoinsEngine!");
            return true;
        }

        if (Bukkit.getPluginManager().getPlugin("Vault") != null) {
            RegisteredServiceProvider<Economy> rsp = plugin.getServer().getServicesManager().getRegistration(Economy.class);
            if (rsp != null) {
                vaultEcon = rsp.getProvider();
                plugin.getLogger().info("Successfully hooked into Vault!");
                return true;
            }
        }
        return false;
    }

    public boolean deposit(Player player, double amount) {
        try {
            if (useCoinsEngine) {
                Currency currency = CoinsEngineAPI.getCurrencyManager().getDefaultCurrency();
                if (currency != null) {
                    CoinsEngineAPI.addBalance(player, currency, amount);
                    return true;
                }
            } else if (vaultEcon != null) {
                return vaultEcon.depositPlayer(player, amount).transactionSuccess();
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to process economy transaction for " + player.getName() + "!");
            e.printStackTrace();
        }
        return false;
    }
}
