package com.yourname.sellplugin.listener;

import com.yourname.sellplugin.SellPlugin;
import com.yourname.sellplugin.util.Scheduler;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * The worth line is applied purely through outgoing item packets, so it only
 * appears on items the client is (re)sent while a decoratable inventory is open.
 * Items that arrive in other situations — picked up, bought from a shop GUI,
 * handed over with /give while a menu is open — would otherwise stay bare until
 * the next full inventory resend (i.e. a relog).
 *
 * <p>This listener nudges the client to redraw shortly after those moments by
 * re-sending the player's inventory, which the packet listener then decorates.
 */
public class WorthRefreshListener implements Listener {

    private final SellPlugin plugin;

    public WorthRefreshListener(SellPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player) {
            refresh(player);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            refresh(player);
        }
    }

    /**
     * Picking an item onto the cursor and putting it back down are ordinary
     * clicks; without a redraw the moved item (and the cursor item) come through
     * bare. Re-sending the inventory the tick after any click keeps the worth
     * line visible while items are held and replaced.
     */
    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            refresh(player);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        refresh(event.getPlayer());
    }

    private void refresh(Player player) {
        if (!plugin.getConfigManager().isWorthEnabled()) return;
        // Run next tick, on the player's own region thread (Folia-safe), so the
        // inventory reflects the change that triggered us.
        Scheduler.runEntityLater(plugin, player, () -> {
            if (player.isOnline()) {
                player.updateInventory();
            }
        }, 1L);
    }
}
