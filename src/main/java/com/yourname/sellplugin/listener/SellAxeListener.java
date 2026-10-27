package com.yourname.sellplugin.listener;

import com.yourname.sellplugin.SellPlugin;
import com.yourname.sellplugin.item.SellAxe;
import com.yourname.sellplugin.manager.SellManager;
import com.yourname.sellplugin.util.NumberFormatter;
import com.yourname.sellplugin.util.Text;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.Barrel;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.Container;
import org.bukkit.block.ShulkerBox;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Sells the contents of a chest, barrel or shulker box when a player
 * right-clicks it with the sell axe.
 *
 * <p>Runs late, after protection plugins have had their say: a click that
 * WorldGuard, a claim or a lock plugin refused to let through to the block is
 * refused here too, so the axe can only sell what the player could already
 * have opened and emptied by hand.
 */
public class SellAxeListener implements Listener {

    private static final BlockData AMETHYST_DUST = Material.AMETHYST_BLOCK.createBlockData();

    private final SellPlugin plugin;
    private final SellAxe sellAxe;
    /** Last use per player, so one click cannot be counted twice. */
    private final Map<UUID, Long> lastUse = new ConcurrentHashMap<>();

    public SellAxeListener(SellPlugin plugin, SellAxe sellAxe) {
        this.plugin = plugin;
        this.sellAxe = sellAxe;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        Player player = event.getPlayer();
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (!SellAxe.isSellAxe(tool)) return;

        Block block = event.getClickedBlock();
        if (block == null) return;
        BlockState state = block.getState(false);
        if (!(state instanceof Chest || state instanceof Barrel || state instanceof ShulkerBox)) return;
        Container container = (Container) state;

        if (!plugin.getConfig().getBoolean("sell-axe.enabled", true)) return;
        if (plugin.getConfig().getBoolean("sell-axe.require-sneak", false) && !player.isSneaking()) return;
        if (player.getGameMode() == GameMode.SPECTATOR) return;

        // Past this point the click is the axe's, never an ordinary open.
        boolean protectedBlock = event.useInteractedBlock() == Event.Result.DENY;
        event.setCancelled(true);

        if (SellAxe.isExpired(tool, System.currentTimeMillis())) {
            player.getInventory().setItemInMainHand(null);
            sellAxe.announceDestroyed(player);
            return;
        }

        if (!player.hasPermission("sellplugin.sellaxe.use")) {
            player.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
            return;
        }
        if (protectedBlock || container.isLocked()) {
            player.sendMessage(message("protected", "<red>You can't sell from that container."));
            return;
        }

        long now = System.currentTimeMillis();
        long cooldown = Math.max(0L, plugin.getConfig().getLong("sell-axe.cooldown-ms", 500L));
        Long previous = lastUse.get(player.getUniqueId());
        if (previous != null && now - previous < cooldown) return;
        lastUse.put(player.getUniqueId(), now);

        SellManager.SellResult result = plugin.getSellManager().sellContainer(player, container.getInventory());
        if (!result.success) return;

        playEffects(block);
        if (plugin.getConfig().getBoolean("sell-axe.log-sales", true)) {
            Location at = block.getLocation();
            plugin.getLogger().info("[SellAxe] " + player.getName() + " sold " + result.itemsSold
                    + " item(s) from " + block.getType() + " at " + at.getWorld().getName() + " "
                    + at.getBlockX() + "," + at.getBlockY() + "," + at.getBlockZ()
                    + " for $" + NumberFormatter.format(result.earned));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastUse.remove(event.getPlayer().getUniqueId());
    }

    /** The Shard tools' amethyst chime and dust, at the container. */
    private void playEffects(Block block) {
        if (!plugin.getConfig().getBoolean("sell-axe.effects", true)) return;
        Location center = block.getLocation().add(0.5, 0.5, 0.5);
        float pitch = 0.8f + ThreadLocalRandom.current().nextFloat() * 1.5f;
        block.getWorld().playSound(center, "block.amethyst_block.chime", 2.0f, pitch);
        block.getWorld().spawnParticle(Particle.FALLING_DUST, center, 25, 0.4, 0.4, 0.4, 0.0, AMETHYST_DUST);
    }

    private String message(String key, String def) {
        return Text.legacy(plugin.getConfig().getString("messages.sell-axe." + key, def));
    }
}
