package com.catadmirer.infuseSMP.effects;

import com.catadmirer.infuseSMP.EffectConstants;
import com.catadmirer.infuseSMP.Message;
import com.catadmirer.infuseSMP.managers.CooldownManager;
import com.catadmirer.infuseSMP.util.SchedulerUtil;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class Ocean extends InfuseEffect {
    private static final ConcurrentMap<UUID, ScheduledTask> PASSIVE_TASKS = new ConcurrentHashMap<>();

    public Ocean() { this(false); }

    public Ocean(boolean augmented) {
        super("ocean", EffectConstants.Id.OCEAN, augmented, EffectConstants.PotionColor.OCEAN, EffectConstants.RitualColor.OCEAN, EffectConstants.BackgroundColor.OCEAN);
    }

    @Override
    public void equip(Player owner) {
        if (plugin.getRegionBlocker().isEffectBlocked(owner, this)) return;
        owner.addPotionEffect(new PotionEffect(PotionEffectType.WATER_BREATHING, -1, 0, false, false));
        owner.addPotionEffect(new PotionEffect(PotionEffectType.DOLPHINS_GRACE, -1, 0, false, false));
    }

    @Override
    public void unequip(Player owner) {
        owner.removePotionEffect(PotionEffectType.WATER_BREATHING);
        owner.removePotionEffect(PotionEffectType.DOLPHINS_GRACE);
        ScheduledTask task = PASSIVE_TASKS.remove(owner.getUniqueId());
        if (task != null) task.cancel();
    }

    @Override
    public void applyPassives(Player owner) {
        UUID ownerId = owner.getUniqueId();
        if (PASSIVE_TASKS.containsKey(ownerId)) return;

        long period = Math.max(1L, plugin.getMainConfig().oceanSparkDrownInterval());
        ScheduledTask created = SchedulerUtil.runAtFixedRate(owner, plugin, task -> {
            if (!plugin.getDataManager().hasEffect(owner, this)) {
                PASSIVE_TASKS.remove(ownerId, task);
                task.cancel();
                return;
            }
            if (plugin.getRegionBlocker().isEffectBlocked(owner, this)) return;

            int strength = plugin.getMainConfig().oceanPassiveDrownStrength();
            int damage = plugin.getMainConfig().oceanPassiveDrownDamage();
            if (CooldownManager.isEffectActive(ownerId, "ocean")) {
                strength = plugin.getMainConfig().oceanSparkDrownStrength();
                damage = plugin.getMainConfig().oceanSparkDrownDamage();
            }

            final int finalStrength = strength;
            final int finalDamage = damage;
            final Location center = owner.getLocation().clone();
            final double radius = plugin.getMainConfig().oceanSparkDrownRadius();

            SchedulerUtil.forEachNearbyPlayer(plugin, center, radius,
                    target -> !target.getUniqueId().equals(ownerId),
                    target -> {
                        if (plugin.getRegionBlocker().isEffectBlocked(target, this)) return;
                        int newAir = Math.max(target.getRemainingAir() - finalStrength, -20);
                        target.setRemainingAir(newAir);
                        if (newAir <= 0 && finalDamage > 0) target.damage(finalDamage);
                    });
        }, () -> PASSIVE_TASKS.remove(ownerId), 1L, period);

        if (created == null) return;
        ScheduledTask raced = PASSIVE_TASKS.putIfAbsent(ownerId, created);
        if (raced != null) created.cancel();
    }

    @Override
    public void activateSpark(Player owner, String slot) {
        UUID ownerId = owner.getUniqueId();
        if (CooldownManager.isOnCooldown(ownerId, plainKey + "_" + slot)) return;
        if (!plugin.getRegionBlocker().canUseSpark(owner)) return;
        if (plugin.getRegionBlocker().isEffectBlocked(owner, this)) return;

        owner.playSound(owner.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1, 1);
        long cooldown = plugin.getMainConfig().cooldown(this);
        long duration = plugin.getMainConfig().duration(this);
        CooldownManager.setTimes(ownerId, plainKey + "_" + slot, duration, cooldown);

        final long durationTicks = Math.max(1L, duration * 20L);
        final long[] elapsed = {0L};
        SchedulerUtil.runAtFixedRate(owner, plugin, task -> {
            if (elapsed[0] >= durationTicks || !CooldownManager.isEffectActive(ownerId, "ocean")) {
                task.cancel();
                return;
            }

            Location center = owner.getLocation();
            double radius = 5.0;
            for (int angle = 0; angle < 360; angle += 10) {
                double rad = Math.toRadians(angle);
                Location particleLoc = center.clone().add(radius * Math.cos(rad), 0, radius * Math.sin(rad));
                center.getWorld().spawnParticle(Particle.FALLING_WATER, particleLoc, 1);
            }
            elapsed[0] += 10L;
        }, null, 1L, 10L);

        long pullPeriod = Math.max(1L, plugin.getMainConfig().oceanPullInterval());
        SchedulerUtil.runAtFixedRate(owner, plugin, task -> {
            if (!CooldownManager.isEffectActive(ownerId, "ocean")) {
                task.cancel();
                return;
            }

            Location center = owner.getLocation().clone();
            double radius = plugin.getMainConfig().oceanPullRadius();
            double strength = plugin.getMainConfig().oceanPullStrength();
            SchedulerUtil.forEachNearbyPlayer(plugin, center, radius,
                    target -> !target.getUniqueId().equals(ownerId)
                            && !plugin.getTrustManager().doesTrust(ownerId, target.getUniqueId()),
                    target -> {
                        if (!plugin.getRegionBlocker().canBeTargetedBySpark(target)) return;
                        if (plugin.getRegionBlocker().isEffectBlocked(target, this)) return;

                        Vector direction = center.toVector().subtract(target.getLocation().toVector());
                        if (direction.lengthSquared() <= 0.0001) return;
                        Vector pull = direction.normalize().multiply(strength);
                        if (Double.isFinite(pull.getX()) && Double.isFinite(pull.getY()) && Double.isFinite(pull.getZ())) {
                            target.setVelocity(pull);
                        }
                    });
        }, null, 1L, pullPeriod);
    }

    @Override public InfuseEffect getRegularVersion() { return new Ocean(); }
    @Override public InfuseEffect getAugmentedVersion() { return new Ocean(true); }
    @Override public Message getName() { return new Message(augmented ? Message.MessageType.AUG_OCEAN_NAME : Message.MessageType.OCEAN_NAME); }
    @Override public Message getLore() { return new Message(augmented ? Message.MessageType.AUG_OCEAN_LORE : Message.MessageType.OCEAN_LORE); }
}
