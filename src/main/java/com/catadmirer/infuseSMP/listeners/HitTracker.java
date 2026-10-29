package com.catadmirer.infuseSMP.listeners;

import com.catadmirer.infuseSMP.Infuse;
import com.catadmirer.infuseSMP.effects.InfuseEffect;
import com.catadmirer.infuseSMP.effects.Thunder;
import com.catadmirer.infuseSMP.events.TenHitsGivenEvent;
import com.catadmirer.infuseSMP.events.TenHitsTakenEvent;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks the ten-hit mechanics with a rolling time window.
 *
 * <p>The previous implementation created one async scheduled task per hit and mutated HashMaps from
 * both region and async threads.  A timestamp deque gives equivalent rolling-decay semantics with no
 * scheduler churn and keeps state isolated per player.</p>
 */
public class HitTracker implements Listener {
    private static final int REQUIRED_HITS = 10;

    private final Infuse plugin;
    private final Thunder thunder = new Thunder();

    private final Map<UUID, HitWindow> trackedHitsGiven = new ConcurrentHashMap<>();
    private final Map<UUID, HitWindow> trackedHitsTaken = new ConcurrentHashMap<>();

    public HitTracker(Infuse plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void trackHit(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player target)) return;
        if (!(event.getDamageSource().getCausingEntity() instanceof LivingEntity attacker)) return;

        if (attacker instanceof Player attackingPlayer) {
            if (plugin.getTrustManager().doesTrust(target, attackingPlayer)) return;
            if (attackingPlayer.getAttackCooldown() < 0.85F) return;
        }

        HitWindow window = trackedHitsTaken.computeIfAbsent(target.getUniqueId(), ignored -> new HitWindow());
        int hits = window.add(1, decayMillis());
        if (hits < REQUIRED_HITS) return;

        window.clear();
        new TenHitsTakenEvent(target, attacker).callEvent();
    }

    @EventHandler(ignoreCancelled = true)
    public void trackAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamageSource().getCausingEntity() instanceof Player attacker)) return;
        if (!(event.getEntity() instanceof LivingEntity target)) return;
        if (attacker.getAttackCooldown() < 0.85F) return;

        if (target instanceof Player targetPlayer && plugin.getTrustManager().doesTrust(attacker, targetPlayer)) return;
        if (event.getDamageSource().getDamageType() == DamageType.LIGHTNING_BOLT) return;

        int weight = 1;
        if (InfuseEffect.isRegistered(thunder.key())
                && plugin.getDataManager().hasEffect(attacker, thunder)
                && attacker.isInRain()) {
            weight = 2;
        }

        HitWindow window = trackedHitsGiven.computeIfAbsent(attacker.getUniqueId(), ignored -> new HitWindow());
        int hits = window.add(weight, decayMillis());
        if (hits < REQUIRED_HITS) return;

        window.clear();
        new TenHitsGivenEvent(attacker, target).callEvent();
    }

    @EventHandler
    public void removeFromHitTracker(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        trackedHitsGiven.remove(uuid);
        trackedHitsTaken.remove(uuid);
    }

    private long decayMillis() {
        return Math.max(1L, plugin.getMainConfig().hitCounterDecaySeconds()) * 1000L;
    }

    /** One deque is only mutated by the owning player's region in normal gameplay; synchronized also
     * makes unusual cross-region damage callbacks safe without a global lock. */
    private static final class HitWindow {
        private final Deque<Long> hits = new ArrayDeque<>(REQUIRED_HITS);

        synchronized int add(int amount, long decayMillis) {
            long now = System.currentTimeMillis();
            long oldestAllowed = now - decayMillis;
            while (!hits.isEmpty() && hits.peekFirst() <= oldestAllowed) hits.removeFirst();

            for (int i = 0; i < amount; i++) hits.addLast(now);
            return hits.size();
        }

        synchronized void clear() {
            hits.clear();
        }
    }
}
