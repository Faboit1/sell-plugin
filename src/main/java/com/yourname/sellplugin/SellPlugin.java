package com.yourname.sellplugin;

import com.yourname.sellplugin.command.SellAllCommand;
import com.yourname.sellplugin.command.SellCommand;
import com.yourname.sellplugin.command.SellMultiCommand;
import com.yourname.sellplugin.command.FastSellAllCommand;
import com.yourname.sellplugin.command.ShowWorthCommand;
import com.yourname.sellplugin.command.TopSellCommand;
import com.yourname.sellplugin.command.WorthCommand;
import com.yourname.sellplugin.dialog.WorthDialogService;
import com.yourname.sellplugin.economy.EconomyManager;
import com.yourname.sellplugin.gui.GUIListener;
import com.yourname.sellplugin.integration.OrderIntegration;
import com.yourname.sellplugin.listener.WorthPacketListener;
import com.yourname.sellplugin.listener.WorthRefreshListener;
import com.yourname.sellplugin.manager.ConfigManager;
import com.yourname.sellplugin.manager.ConfigMigrator;
import com.yourname.sellplugin.manager.MultiplierManager;
import com.yourname.sellplugin.manager.PriceManager;
import com.yourname.sellplugin.manager.SellManager;
import com.yourname.sellplugin.manager.WorthVisibilityManager;
import com.yourname.sellplugin.util.Scheduler;
import org.bukkit.plugin.java.JavaPlugin;

public class SellPlugin extends JavaPlugin {

    private EconomyManager economyManager;
    private ConfigManager configManager;
    private PriceManager priceManager;
    private MultiplierManager multiplierManager;
    private SellManager sellManager;
    private WorthVisibilityManager worthVisibilityManager;
    private WorthPacketListener worthPacketListener;
    private OrderIntegration orderIntegration;
    private WorthDialogService worthDialogService;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        new ConfigMigrator(this).migrate();
        configManager = new ConfigManager(this);

        priceManager = new PriceManager(this);
        priceManager.loadPrices();

        multiplierManager = new MultiplierManager(this);
        sellManager = new SellManager(this);
        worthVisibilityManager = new WorthVisibilityManager(this);

        worthDialogService = new WorthDialogService(this);
        if (!WorthDialogService.isSupported()) {
            getLogger().info("This server predates Minecraft 1.21.6, so item prices open as a menu rather than a dialog.");
        }

        economyManager = new EconomyManager(this);
        if (!economyManager.setupEconomy()) {
            getLogger().severe("No economy plugin found (Vault or CoinsEngine)! Disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        getCommand("sell").setExecutor(new SellCommand(this));
        getCommand("sellall").setExecutor(new SellAllCommand(this));
        getCommand("fastsellall").setExecutor(new FastSellAllCommand(this));
        getCommand("topsell").setExecutor(new TopSellCommand(this));
        getCommand("sellmulti").setExecutor(new SellMultiCommand(this));
        getCommand("sellworth").setExecutor(new WorthCommand(this));
        ShowWorthCommand showWorthCommand = new ShowWorthCommand(this);
        getCommand("showworth").setExecutor(showWorthCommand);
        getCommand("showworth").setTabCompleter(showWorthCommand);
        getServer().getPluginManager().registerEvents(new GUIListener(this), this);
        getServer().getPluginManager().registerEvents(new WorthRefreshListener(this), this);

        // Checked before the listener is even constructed: its fields and methods
        // are typed against ProtocolLib, so loading the class at all throws
        // NoClassDefFoundError when ProtocolLib is absent - long before the
        // listener's own guard could report it politely.
        if (getServer().getPluginManager().getPlugin("ProtocolLib") == null) {
            getLogger().warning("ProtocolLib not found; sell worth tooltips are disabled.");
        } else {
            worthPacketListener = new WorthPacketListener(this);
            worthPacketListener.register();
        }

        // FoOrders loads independently of us, so bind once the whole server is
        // up rather than racing its own onEnable.
        orderIntegration = new OrderIntegration(this);
        Scheduler.runGlobalLater(this, orderIntegration::bind, 1L);

        getLogger().info("SellPlugin has been enabled successfully.");
    }

    @Override
    public void onDisable() {
        if (multiplierManager != null) {
            multiplierManager.saveAll();
        }
        if (worthVisibilityManager != null) {
            worthVisibilityManager.saveNow();
        }
        if (worthPacketListener != null) {
            worthPacketListener.unregister();
        }
        getLogger().info("SellPlugin has been disabled.");
    }

    public EconomyManager getEconomyManager() { return economyManager; }
    public ConfigManager getConfigManager()    { return configManager; }
    public PriceManager getPriceManager()      { return priceManager; }
    public MultiplierManager getMultiplierManager() { return multiplierManager; }
    public SellManager getSellManager()        { return sellManager; }
    public WorthVisibilityManager getWorthVisibilityManager() { return worthVisibilityManager; }
    public OrderIntegration getOrderIntegration() { return orderIntegration; }
    public WorthDialogService getWorthDialogService() { return worthDialogService; }
}
