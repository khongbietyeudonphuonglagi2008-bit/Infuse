package com.catadmirer.infuseSMP;

import com.catadmirer.infuseSMP.effects.Heart;
import com.catadmirer.infuseSMP.effects.InfuseEffect;
import com.catadmirer.infuseSMP.extraeffects.Apophis;
import com.catadmirer.infuseSMP.managers.ActionBarUpdater;
import com.catadmirer.infuseSMP.managers.ParticleManager;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runs player-owned passive work once per second.
 *
 * <p>One entity-scheduler task is attached to each player instead of one global BukkitScheduler task.
 * This keeps every entity/world access on the region that owns the player and lets Folia retire the
 * task automatically when the player entity retires.</p>
 */
public final class GlobalLoop implements Listener {
    private final Infuse plugin;
    private final ActionBarUpdater actionBarUpdater;
    private final Map<UUID, ScheduledTask> playerTasks = new ConcurrentHashMap<>();

    private final Set<UUID> lEffectDisabled = ConcurrentHashMap.newKeySet();
    private final Set<UUID> rEffectDisabled = ConcurrentHashMap.newKeySet();

    public GlobalLoop(Infuse plugin) {
        this.plugin = plugin;
        this.actionBarUpdater = new ActionBarUpdater(plugin);
    }

    public void start() {
        Bukkit.getGlobalRegionScheduler().run(plugin, ignored -> {
            for (Player player : Bukkit.getOnlinePlayers()) startPlayerLoop(player);
        });
    }

    public void stop() {
        playerTasks.values().forEach(ScheduledTask::cancel);
        playerTasks.clear();
        lEffectDisabled.clear();
        rEffectDisabled.clear();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        startPlayerLoop(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        ScheduledTask task = playerTasks.remove(uuid);
        if (task != null) task.cancel();
        lEffectDisabled.remove(uuid);
        rEffectDisabled.remove(uuid);
    }

    private void startPlayerLoop(Player player) {
        UUID uuid = player.getUniqueId();
        if (playerTasks.containsKey(uuid)) return;

        ScheduledTask task = player.getScheduler().runAtFixedRate(
                plugin,
                ignored -> tick(player),
                () -> {
                    playerTasks.remove(uuid);
                    lEffectDisabled.remove(uuid);
                    rEffectDisabled.remove(uuid);
                },
                1L,
                20L
        );

        if (task == null) return;
        ScheduledTask previous = playerTasks.putIfAbsent(uuid, task);
        if (previous != null) task.cancel();
    }

    private void tick(Player player) {
        if (!player.isOnline()) return;

        UUID uuid = player.getUniqueId();
        InfuseEffect lEffect = plugin.getDataManager().getEffect(uuid, "1");
        InfuseEffect rEffect = plugin.getDataManager().getEffect(uuid, "2");

        ParticleManager.spawnEffectParticles(player, lEffect);
        ParticleManager.spawnEffectParticles(player, rEffect);
        ParticleManager.spawnCursedParticles(player);

        handlePassive(player, lEffect, lEffectDisabled);
        handlePassive(player, rEffect, rEffectDisabled);
        removeStaleHealthModifiers(player);
        actionBarUpdater.update(player);
    }

    private void handlePassive(Player player, InfuseEffect effect, Set<UUID> disabled) {
        if (effect == null) return;

        UUID uuid = player.getUniqueId();
        boolean shouldBlock = plugin.getRegionBlocker().isEffectBlocked(player, effect);
        boolean blocked = disabled.contains(uuid);

        if (shouldBlock && !blocked) {
            effect.unequip(player);
            disabled.add(uuid);
            blocked = true;
        } else if (!shouldBlock && blocked) {
            effect.equip(player);
            disabled.remove(uuid);
            blocked = false;
        }

        if (!blocked) effect.applyPassives(player);
    }

    private void removeStaleHealthModifiers(Player player) {
        AttributeInstance health = player.getAttribute(Attribute.MAX_HEALTH);
        if (health == null) return;

        Apophis apophis = (Apophis) InfuseEffect.getEffect(EffectConstants.Keys.APOPHIS);
        if (apophis != null && !plugin.getDataManager().hasEffect(player, apophis)) {
            health.removeModifier(Apophis.APOPHIS_BOOST);
        }

        Heart heart = (Heart) InfuseEffect.getEffect(EffectConstants.Keys.HEART);
        if (heart != null && !plugin.getDataManager().hasEffect(player, heart)) {
            health.removeModifier(Heart.heartBoost);
        }
    }
}
