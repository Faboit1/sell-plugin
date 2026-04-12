// Complete plugin initialization for SellPlugin.java

import org.bukkit.plugin.java.JavaPlugin;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.RegisteredServiceProvider;

public class SellPlugin extends JavaPlugin {

    private static Economy economy;

    @Override
    public void onEnable() {
        // Setup Vault Economy
        if (!setupEconomy()) {
            getLogger().severe("No Vault dependency found!");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Initialize managers
        // Your initialization code for managers here...

        // Register commands and listeners
        this.getCommand("sell").setExecutor(new SellCommand());
        getServer().getPluginManager().registerEvents(new SellListener(), this);

        getLogger().info("SellPlugin has been enabled.");
    }

    @Override
    public void onDisable() {
        getLogger().info("SellPlugin has been disabled.");
    }

    private boolean setupEconomy() {
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        economy = rsp != null ? rsp.getProvider() : null;
        return economy != null;
    }

    public static Economy getEconomy() {
        return economy;
    }
}