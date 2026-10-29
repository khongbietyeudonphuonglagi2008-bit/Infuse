package com.catadmirer.infuseSMP.effects;

import com.catadmirer.infuseSMP.EffectConstants;
import com.catadmirer.infuseSMP.Message;
import com.catadmirer.infuseSMP.events.TenHitsGivenEvent;
import com.catadmirer.infuseSMP.managers.CooldownManager;
import com.catadmirer.infuseSMP.util.SchedulerUtil;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Trident;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class Thunder extends InfuseEffect {
    public Thunder() { this(false); }

    public Thunder(boolean augmented) {
        super("thunder", EffectConstants.Id.THUNDER, augmented, EffectConstants.PotionColor.THUNDER, EffectConstants.RitualColor.THUNDER, EffectConstants.BackgroundColor.THUNDER);
    }

    @Override public void equip(Player owner) {}
    @Override public void unequip(Player owner) {}

    @Override
    public void activateSpark(Player owner, String slot) {
        UUID ownerId = owner.getUniqueId();
        if (CooldownManager.isOnCooldown(ownerId, plainKey + "_" + slot)) return;
        if (plugin.getRegionBlocker().isEffectBlocked(owner, this)) return;
        if (!plugin.getRegionBlocker().canUseSpark(owner)) return;

        owner.getWorld().playSound(owner.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1, 1);
        long cooldown = plugin.getMainConfig().cooldown(this);
        long duration = plugin.getMainConfig().duration(this);
        CooldownManager.setTimes(ownerId, plainKey + "_" + slot, duration, cooldown);

        long durationTicks = Math.max(1L, duration * 20L);
        double baseRadius = plugin.getMainConfig().thunderSparkBaseRadius();
        double boostPerPlayer = plugin.getMainConfig().thunderSparkPerPlayerBoostRadius();
        int maxHits = plugin.getMainConfig().thunderSparkStrikesPerPlayer();
        Map<UUID, Integer> hitCount = new ConcurrentHashMap<>();
        long[] elapsed = {0L};

        SchedulerUtil.runAtFixedRate(owner, plugin, task -> {
            if (elapsed[0] >= durationTicks || !CooldownManager.isEffectActive(ownerId, "thunder")) {
                task.cancel();
                return;
            }
            elapsed[0] += 20L;
            runStormPulse(owner, ownerId, baseRadius, boostPerPlayer, maxHits, hitCount);
        }, null, 1L, 20L);
    }

    private void runStormPulse(Player owner, UUID ownerId, double baseRadius, double boostPerPlayer,
                               int maxHits, Map<UUID, Integer> hitCount) {
        Location center = owner.getLocation().clone();
        UUID worldId = center.getWorld().getUID();

        Bukkit.getGlobalRegionScheduler().run(plugin, ignored -> {
            List<Player> candidates = new ArrayList<>(Bukkit.getOnlinePlayers());
            if (candidates.isEmpty()) return;

            double maxRadius = baseRadius + boostPerPlayer * candidates.size();
            double maxRadiusSquared = maxRadius * maxRadius;
            Map<UUID, Double> distances = new ConcurrentHashMap<>();
            AtomicInteger remaining = new AtomicInteger(candidates.size());
            AtomicBoolean finalized = new AtomicBoolean(false);

            Runnable finishOne = () -> {
                if (remaining.decrementAndGet() != 0 || !finalized.compareAndSet(false, true)) return;
                SchedulerUtil.run(owner, plugin, () -> finalizeStormPulse(ownerId, baseRadius, boostPerPlayer,
                        maxHits, hitCount, distances));
            };

            for (Player candidate : candidates) {
                candidate.getScheduler().run(plugin, task -> {
                    if (candidate.getWorld().getUID().equals(worldId)) {
                        double distanceSquared = candidate.getLocation().distanceSquared(center);
                        if (distanceSquared <= maxRadiusSquared) distances.put(candidate.getUniqueId(), distanceSquared);
                    }
                    finishOne.run();
                }, finishOne);
            }
        });
    }

    private void finalizeStormPulse(UUID ownerId, double baseRadius, double boostPerPlayer, int maxHits,
                                    Map<UUID, Integer> hitCount, Map<UUID, Double> distances) {
        double radius = baseRadius;
        for (int i = 0; i < 16; i++) {
            double radiusSquared = radius * radius;
            long nearby = distances.entrySet().stream()
                    .filter(entry -> !entry.getKey().equals(ownerId))
                    .filter(entry -> entry.getValue() <= radiusSquared)
                    .count();
            double next = baseRadius + boostPerPlayer * nearby;
            if (Math.abs(next - radius) < 1.0E-6) break;
            radius = next;
        }

        double radiusSquared = radius * radius;
        for (Map.Entry<UUID, Double> entry : distances.entrySet()) {
            UUID targetId = entry.getKey();
            if (targetId.equals(ownerId) || entry.getValue() > radiusSquared) continue;
            if (plugin.getTrustManager().doesTrust(targetId, ownerId)) continue;

            Player target = Bukkit.getPlayer(targetId);
            if (target == null) continue;
            target.getScheduler().run(plugin, task -> {
                if (!plugin.getRegionBlocker().canBeTargetedBySpark(target)) return;
                if (plugin.getRegionBlocker().isEffectBlocked(target, this)) return;
                int count = hitCount.merge(targetId, 1, Integer::sum);
                if (count <= maxHits) strikeLightning(target);
            }, null);
        }
    }

    @Override public InfuseEffect getRegularVersion() { return new Thunder(); }
    @Override public InfuseEffect getAugmentedVersion() { return new Thunder(true); }
    @Override public Message getName() { return new Message(augmented ? Message.MessageType.AUG_THUNDER_NAME : Message.MessageType.THUNDER_NAME); }
    @Override public Message getLore() { return new Message(augmented ? Message.MessageType.AUG_THUNDER_LORE : Message.MessageType.THUNDER_LORE); }

    public void strikeLightning(LivingEntity target) {
        target.getWorld().strikeLightningEffect(target.getLocation());
        DamageSource source = DamageSource.builder(DamageType.LIGHTNING_BOLT).build();
        target.damage(plugin.getMainConfig().thunderThunderDamage(), source);
        target.getWorld().spawnParticle(Particle.DUST, target.getLocation().add(0, 1, 0), 10,
                0.5, 0.5, 0.5, 0, new Particle.DustOptions(Color.YELLOW, 1.5F));
    }

    private void chainLightning(UUID attackerId, Player lastTarget, Set<UUID> hitTargets) {
        if (hitTargets.size() >= 10 || !lastTarget.isValid()) return;
        double radius = plugin.getMainConfig().thunderPassiveChainLightningRadius();
        Location center = lastTarget.getLocation().clone();
        AtomicBoolean selected = new AtomicBoolean(false);

        SchedulerUtil.forEachNearbyPlayer(plugin, center, radius,
                target -> !target.getUniqueId().equals(attackerId)
                        && !hitTargets.contains(target.getUniqueId())
                        && !plugin.getTrustManager().doesTrust(attackerId, target.getUniqueId()),
                target -> {
                    if (!selected.compareAndSet(false, true)) return;
                    if (plugin.getRegionBlocker().isEffectBlocked(target, this)) return;
                    strikeLightning(target);
                    Set<UUID> next = new HashSet<>(hitTargets);
                    next.add(target.getUniqueId());
                    SchedulerUtil.runDelayed(target, plugin, () -> chainLightning(attackerId, target, next), 20L);
                });
    }

    @EventHandler
    public void onTenHitEvent(TenHitsGivenEvent event) {
        Player attacker = event.getPlayer();
        if (!plugin.getDataManager().hasEffect(attacker, this)) return;
        if (plugin.getRegionBlocker().isEffectBlocked(attacker, this)) return;
        if (!(event.getLastTarget() instanceof Player target)) return;
        if (plugin.getRegionBlocker().isEffectBlocked(target, this)) return;

        strikeLightning(target);
        Set<UUID> hit = new HashSet<>();
        hit.add(target.getUniqueId());
        SchedulerUtil.runDelayed(target, plugin, () -> chainLightning(attacker.getUniqueId(), target, hit), 20L);
    }

    @EventHandler
    public void thunderAutoChanneling(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Trident trident)) return;
        if (!(trident.getShooter() instanceof Player attacker)) return;
        if (!plugin.getDataManager().hasEffect(attacker, this)) return;
        if (plugin.getRegionBlocker().isEffectBlocked(attacker, this)) return;
        if (!(event.getEntity() instanceof LivingEntity target)) return;
        if (target instanceof Player p && plugin.getDataManager().doesTrust(attacker, p)) return;
        if (plugin.getRegionBlocker().isEffectBlocked(target, this)) return;
        strikeLightning(target);
    }
}
