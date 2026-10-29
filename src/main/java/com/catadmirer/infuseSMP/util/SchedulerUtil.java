package com.catadmirer.infuseSMP.util;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.Nullable;

import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Small Folia/Paper-safe scheduling helpers.
 *
 * <p>World and entity state must be touched from the scheduler that owns that state.  These helpers
 * deliberately do not fall back to BukkitScheduler; the threaded-region schedulers are available on
 * modern Paper-compatible servers as well as Folia/Canvas.</p>
 */
public final class SchedulerUtil {
    private SchedulerUtil() {}

    public static ScheduledTask run(Entity entity, Plugin plugin, Runnable action) {
        return entity.getScheduler().run(plugin, ignored -> action.run(), null);
    }

    public static ScheduledTask run(Entity entity, Plugin plugin, Runnable action, @Nullable Runnable retired) {
        return entity.getScheduler().run(plugin, ignored -> action.run(), retired);
    }

    public static ScheduledTask runDelayed(Entity entity, Plugin plugin, Runnable action, long delayTicks) {
        return entity.getScheduler().runDelayed(plugin, ignored -> action.run(), null, Math.max(1L, delayTicks));
    }

    public static ScheduledTask runDelayed(Entity entity, Plugin plugin, Runnable action, @Nullable Runnable retired, long delayTicks) {
        return entity.getScheduler().runDelayed(plugin, ignored -> action.run(), retired, Math.max(1L, delayTicks));
    }

    public static ScheduledTask runAtFixedRate(Entity entity, Plugin plugin, Consumer<ScheduledTask> action,
                                                @Nullable Runnable retired, long initialDelayTicks, long periodTicks) {
        return entity.getScheduler().runAtFixedRate(
                plugin,
                action,
                retired,
                Math.max(1L, initialDelayTicks),
                Math.max(1L, periodTicks)
        );
    }

    public static ScheduledTask runRegion(Location location, Plugin plugin, Consumer<ScheduledTask> action) {
        return Bukkit.getRegionScheduler().run(plugin, location, action);
    }

    public static ScheduledTask runRegionDelayed(Location location, Plugin plugin, Consumer<ScheduledTask> action, long delayTicks) {
        return Bukkit.getRegionScheduler().runDelayed(plugin, location, action, Math.max(1L, delayTicks));
    }

    public static ScheduledTask runRegionAtFixedRate(Location location, Plugin plugin, Consumer<ScheduledTask> action,
                                                      long initialDelayTicks, long periodTicks) {
        return Bukkit.getRegionScheduler().runAtFixedRate(
                plugin,
                location,
                action,
                Math.max(1L, initialDelayTicks),
                Math.max(1L, periodTicks)
        );
    }

    public static ScheduledTask runGlobal(Plugin plugin, Consumer<ScheduledTask> action) {
        return Bukkit.getGlobalRegionScheduler().run(plugin, action);
    }

    public static ScheduledTask runGlobalDelayed(Plugin plugin, Consumer<ScheduledTask> action, long delayTicks) {
        return Bukkit.getGlobalRegionScheduler().runDelayed(plugin, action, Math.max(1L, delayTicks));
    }

    /**
     * Runs an action for every currently-online player on that player's owning region thread.
     */
    public static void forEachOnlinePlayer(Plugin plugin, Consumer<Player> action) {
        Bukkit.getGlobalRegionScheduler().run(plugin, ignored -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.getScheduler().run(plugin, task -> action.accept(player), null);
            }
        });
    }

    /**
     * Finds online players near an immutable location snapshot without reading another player's state
     * from the caller's region.  Each distance check and action runs on the target player's scheduler.
     */
    public static void forEachNearbyPlayer(Plugin plugin, Location center, double radius,
                                           Predicate<Player> filter, Consumer<Player> action) {
        final Location snapshot = center.clone();
        final World world = snapshot.getWorld();
        if (world == null) return;

        final UUID worldId = world.getUID();
        final double radiusSquared = radius * radius;

        forEachOnlinePlayer(plugin, player -> {
            if (!player.getWorld().getUID().equals(worldId)) return;
            if (player.getLocation().distanceSquared(snapshot) > radiusSquared) return;
            if (!filter.test(player)) return;

            action.accept(player);
        });
    }
}
