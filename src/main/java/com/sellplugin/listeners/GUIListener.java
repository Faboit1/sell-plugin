package com.sellplugin.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import com.sellplugin.managers.SellManager;

public class GUIListener implements Listener {
    private final SellManager sellManager;

    public GUIListener(SellManager sellManager) {
        this.sellManager = sellManager;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        String inventoryTitle = event.getView().getTitle();
        if (!inventoryTitle.contains("Sell")) return;
        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot >= 0 && slot <= 8) {
            player.sendMessage("Category clicked: " + slot);
        }
    }
}