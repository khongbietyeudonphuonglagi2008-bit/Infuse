package com.catadmirer.infuseSMP.effects;

import com.catadmirer.infuseSMP.EffectConstants;
import com.catadmirer.infuseSMP.Message;
import com.catadmirer.infuseSMP.events.TenHitsGivenEvent;
import com.catadmirer.infuseSMP.managers.CooldownManager;
import com.catadmirer.infuseSMP.util.SchedulerUtil;
import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.CookingRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import java.util.Iterator;
import java.util.UUID;

public class Fire extends InfuseEffect {
    public Fire() { this(false); }
    public Fire(boolean augmented) { super("fire", EffectConstants.Id.FIRE, augmented, EffectConstants.PotionColor.FIRE, EffectConstants.RitualColor.FIRE, EffectConstants.BackgroundColor.FIRE); }

    @Override public void equip(Player owner) {
        if (!plugin.getRegionBlocker().isEffectBlocked(owner, this)) owner.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, -1, 0, false, false));
    }
    @Override public void unequip(Player owner) { owner.removePotionEffect(PotionEffectType.FIRE_RESISTANCE); }

    @Override public void activateSpark(Player owner, String slot) {
        final UUID playerUUID = owner.getUniqueId();
        if (CooldownManager.isOnCooldown(playerUUID, plainKey + "_" + slot)) return;
        if (!plugin.getRegionBlocker().canUseSpark(owner) || plugin.getRegionBlocker().isEffectBlocked(owner, this)) return;
        owner.getWorld().playSound(owner.getLocation(), Sound.ENTITY_BLAZE_DEATH, 1, 1);

        final double radius = plugin.getMainConfig().fireSparkRadius();
        for (Entity entity : owner.getNearbyEntities(radius, radius, radius)) {
            if (!(entity instanceof LivingEntity living) || entity.equals(owner)) continue;
            SchedulerUtil.run(living, plugin, () -> {
                if (!plugin.getRegionBlocker().canBeTargetedBySpark(living)) return;
                if (plugin.getRegionBlocker().isEffectBlocked(living, this)) return;
                living.setFireTicks(100);
            });
        }

        spawnSparkEffect(owner);
        SchedulerUtil.runDelayed(owner, plugin, () -> owner.getWorld().spawnParticle(Particle.EXPLOSION, owner.getLocation(), 1), 20L);
        long cooldown = plugin.getMainConfig().cooldown(this);
        long duration = plugin.getMainConfig().duration(this);
        CooldownManager.setTimes(playerUUID, plainKey + "_" + slot, duration, cooldown);
    }

    @Override public InfuseEffect getRegularVersion() { return new Fire(); }
    @Override public InfuseEffect getAugmentedVersion() { return new Fire(true); }
    @Override public Message getName() { return new Message(augmented ? Message.MessageType.AUG_FIRE_NAME : Message.MessageType.FIRE_NAME); }
    @Override public Message getLore() { return new Message(augmented ? Message.MessageType.AUG_FIRE_LORE : Message.MessageType.FIRE_LORE); }

    private void spawnSparkEffect(final Player caster) {
        final UUID casterId = caster.getUniqueId();
        final long[] elapsed = {0L};
        SchedulerUtil.runAtFixedRate(caster, plugin, task -> {
            elapsed[0] += 20L;
            if (elapsed[0] >= 100L) {
                Location finish = caster.getLocation().clone();
                task.cancel();
                startDarkRedDustEffect(finish, casterId);
                return;
            }
            Location center = caster.getLocation().clone();
            World world = center.getWorld();
            world.playSound(center, Sound.ENTITY_PLAYER_HURT_ON_FIRE, 1, 1);
            for (int angle = 0; angle < 360; angle += 20) {
                double rad = Math.toRadians(angle);
                world.spawnParticle(Particle.LAVA, center.clone().add(5 * Math.cos(rad), 0.1, 5 * Math.sin(rad)), 10, 0.05, 0.05, 0.05, 0.01);
            }
            SchedulerUtil.forEachNearbyPlayer(plugin, center, 5.0,
                    target -> !target.getUniqueId().equals(casterId),
                    target -> {
                        if (!plugin.getRegionBlocker().canBeTargetedBySpark(target)) return;
                        if (plugin.getRegionBlocker().isEffectBlocked(target, Fire.this)) return;
                        target.damage(8.0);
                    });
        }, null, 20L, 20L);
    }

    private void startDarkRedDustEffect(final Location startLoc, UUID casterId) {
        final World world = startLoc.getWorld();
        if (world == null) return;
        final Location center = startLoc.clone();
        final double explosionRadius = plugin.getMainConfig().fireSparkExplosionRadius();
        SchedulerUtil.forEachNearbyPlayer(plugin, center, explosionRadius,
                target -> !target.getUniqueId().equals(casterId), target -> target.setVelocity(new Vector(0, 2, 0)));
        SchedulerUtil.runRegion(center, plugin, ignored -> world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 1, 1));
        final int[] tick = {0};
        SchedulerUtil.runRegionAtFixedRate(center, plugin, task -> {
            if (tick[0] >= 60) { task.cancel(); return; }
            double particleHeightOffset = tick[0] * 3.0;
            if (particleHeightOffset > 30.0) { task.cancel(); return; }
            double circleRadius = explosionRadius + tick[0] * 0.1;
            for (int angle = 0; angle < 360; angle += 5) {
                double rad = Math.toRadians(angle);
                Location particleLoc = center.clone().add(circleRadius * Math.cos(rad), particleHeightOffset, circleRadius * Math.sin(rad));
                world.spawnParticle(Particle.DUST_PILLAR, particleLoc, 3, 0, 0, 0, 0, Material.REDSTONE_BLOCK.createBlockData());
            }
            tick[0]++;
        }, 1L, 1L);
    }

    @EventHandler public void onMove(PlayerMoveEvent event) {
        final Player player = event.getPlayer();
        if (!player.isInLava() || !plugin.getDataManager().hasEffect(player, this) || plugin.getRegionBlocker().isEffectBlocked(player, this)) return;
        if (event.getFrom().distanceSquared(event.getTo()) < 0.01) return;
        player.setVelocity(player.getLocation().getDirection().normalize().multiply(plugin.getMainConfig().firePassiveWalkSpeed()));
    }

    @EventHandler public void onEntityShootBow(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!plugin.getDataManager().hasEffect(player, this) || plugin.getRegionBlocker().isEffectBlocked(player, this)) return;
        if (event.getForce() >= 1 && event.getProjectile() instanceof Projectile projectile) projectile.setFireTicks(100);
    }

    @EventHandler public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || event.getCause() != DamageCause.FALL) return;
        if (!plugin.getDataManager().hasEffect(player, this) || plugin.getRegionBlocker().isEffectBlocked(player, this)) return;
        final Material blockType = player.getLocation().getBlock().getType();
        if (blockType == Material.LAVA || blockType == Material.LAVA_CAULDRON) event.setCancelled(true);
    }

    @EventHandler public void fireCombustTarget(TenHitsGivenEvent event) {
        final Player attacker = event.getPlayer();
        if (!plugin.getDataManager().hasEffect(attacker, this) || plugin.getRegionBlocker().isEffectBlocked(attacker, this)) return;
        if (plugin.getRegionBlocker().isEffectBlocked(event.getLastTarget(), this)) return;
        event.getLastTarget().setFireTicks(100);
    }

    @EventHandler public void onBreak(BlockBreakEvent event) {
        if (!plugin.getDataManager().hasEffect(event.getPlayer(), this) || plugin.getRegionBlocker().isEffectBlocked(event.getPlayer(), this)) return;
        final Iterator<Recipe> recipes = Bukkit.recipeIterator();
        while (recipes.hasNext()) {
            if (!(recipes.next() instanceof CookingRecipe<?> recipe) || !event.getBlock().getType().isItem() || !recipe.getInputChoice().test(ItemStack.of(event.getBlock().getType()))) continue;
            event.setDropItems(false);
            event.getBlock().getWorld().dropItemNaturally(event.getBlock().getLocation().add(new Vector(0.5, 0.5, 0.5)), recipe.getResult());
            return;
        }
    }
}
