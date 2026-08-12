package com.yourname.sellplugin.util;

import com.yourname.sellplugin.SellPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;

/**
 * Thin wrapper over the Paper/Folia scheduler APIs so the plugin behaves
 * correctly on both regular Paper/Spigot and on Folia (multi-threaded regions).
 *
 * <p>On Folia there is no single "main thread": entities live in region threads
 * that can move between CPUs, so {@code Bukkit.getScheduler()} throws. The
 * region/entity/global/async schedulers used here are part of the Paper API and
 * are implemented on regular Paper too, where they simply run on the main
 * server thread — which lets the whole plugin share one code path.
 *
 * <ul>
 *   <li><b>Entity</b> tasks run on the thread that currently owns that entity's
 *       region — the only safe place to touch a player, their inventory, etc.</li>
 *   <li><b>Global</b> tasks run on the global region tick thread — for work not
 *       tied to any single entity/location.</li>
 *   <li><b>Async</b> tasks run off any region thread — for I/O and other work
 *       that must never touch game state directly.</li>
 * </ul>
 */
public final class Scheduler {

    private Scheduler() {
    }

    /**
     * Runs {@code task} on the region owning {@code entity}, {@code delayTicks}
     * later (minimum 1 tick — Folia rejects a zero/negative delay). If the
     * entity is removed before it fires, the task is silently dropped.
     */
    public static void runEntityLater(SellPlugin plugin, Entity entity, Runnable task, long delayTicks) {
        long delay = Math.max(1L, delayTicks);
        entity.getScheduler().runDelayed(plugin, scheduled -> task.run(), null, delay);
    }

    /**
     * Runs {@code task} on the region owning {@code entity} as soon as possible.
     * Dropped if the entity is removed first.
     */
    public static void runEntity(SellPlugin plugin, Entity entity, Runnable task) {
        entity.getScheduler().run(plugin, scheduled -> task.run(), null);
    }

    /** Runs {@code task} on the global region, {@code delayTicks} later (min 1). */
    public static void runGlobalLater(SellPlugin plugin, Runnable task, long delayTicks) {
        long delay = Math.max(1L, delayTicks);
        Bukkit.getGlobalRegionScheduler().runDelayed(plugin, scheduled -> task.run(), delay);
    }

    /** Runs {@code task} on the global region as soon as possible. */
    public static void runGlobal(SellPlugin plugin, Runnable task) {
        Bukkit.getGlobalRegionScheduler().run(plugin, scheduled -> task.run());
    }

    /** Runs {@code task} off any region thread (for blocking I/O and the like). */
    public static void runAsync(SellPlugin plugin, Runnable task) {
        Bukkit.getAsyncScheduler().runNow(plugin, scheduled -> task.run());
    }
}
