package com.catadmirer.infuseSMP.effects;

import com.catadmirer.infuseSMP.EffectConstants;
import com.catadmirer.infuseSMP.Message;
import com.catadmirer.infuseSMP.events.TenHitsTakenEvent;
import com.catadmirer.infuseSMP.managers.CooldownManager;
import com.catadmirer.infuseSMP.managers.ParticleManager;
import com.catadmirer.infuseSMP.util.SchedulerUtil;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.WindCharge;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import java.util.UUID;

public class Feather extends InfuseEffect {
    public Feather() { this(false); }
    public Feather(boolean augmented) { super("feather", EffectConstants.Id.FEATHER, augmented, EffectConstants.PotionColor.FEATHER, EffectConstants.RitualColor.FEATHER, EffectConstants.BackgroundColor.FEATHER); }
    @Override public void equip(Player owner) {}
    @Override public void unequip(Player owner) {}
    @Override public void activateSpark(Player owner, String slot) {
        UUID playerUUID = owner.getUniqueId();
        if (CooldownManager.isOnCooldown(playerUUID, plainKey + "_" + slot)) return;
        if (!plugin.getRegionBlocker().canUseSpark(owner) || plugin.getRegionBlocker().isEffectBlocked(owner, this)) return;
        owner.getWorld().playSound(owner.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1, 1);
        ParticleManager.spawnEffectCloud(owner, Color.fromRGB(0xBEA3CA));
        owner.setVelocity(owner.getEyeLocation().getDirection().normalize().multiply(0).setY(1));
        owner.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 20, 10));
        long cooldown = plugin.getMainConfig().cooldown(this), duration = plugin.getMainConfig().duration(this);
        CooldownManager.setTimes(playerUUID, plainKey + "_" + slot, duration, cooldown);
        owner.getScheduler().runDelayed(plugin, t -> CooldownManager.setDuration(playerUUID, "feathermace", 5L), null, 10);
    }
    @Override public InfuseEffect getRegularVersion() { return new Feather(); }
    @Override public InfuseEffect getAugmentedVersion() { return new Feather(true); }
    @Override public Message getName() { return new Message(augmented ? Message.MessageType.AUG_FEATHER_NAME : Message.MessageType.FEATHER_NAME); }
    @Override public Message getLore() { return new Message(augmented ? Message.MessageType.AUG_FEATHER_LORE : Message.MessageType.FEATHER_LORE); }

    @EventHandler public void FeatherLand(PlayerMoveEvent event) {
        final Player player = event.getPlayer(); final double radius = plugin.getMainConfig().featherLandRadius();
        if (!player.isOnGround() || !CooldownManager.isEffectActive(player.getUniqueId(), "feathermace") || plugin.getRegionBlocker().isEffectBlocked(player, this)) return;
        CooldownManager.setDuration(player.getUniqueId(), "feathermace", 0L);
        Location loc = player.getLocation(); World world = player.getWorld(); UUID playerId = player.getUniqueId();
        for (Entity entity : player.getNearbyEntities(radius, radius, radius)) {
            if (!(entity instanceof LivingEntity target)) continue;
            SchedulerUtil.run(target, plugin, () -> {
                if (plugin.getRegionBlocker().isEffectBlocked(target, this) || !plugin.getRegionBlocker().canBeTargetedBySpark(target)) return;
                if (target instanceof Player targetPlayer && plugin.getTrustManager().doesTrust(playerId, targetPlayer.getUniqueId())) return;
                target.damage(plugin.getMainConfig().featherLandDamage());
                target.setVelocity(target.getVelocity().add(new Vector(0, 1, 0)));
                target.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 80, 0, false, false, false));
            });
        }
        world.spawnParticle(Particle.CLOUD, loc, 50, 0, 0, 0, 2); world.playSound(loc, Sound.ITEM_MACE_SMASH_GROUND_HEAVY, 1.5F, 1);
        SchedulerUtil.runDelayed(player, plugin, () -> player.setVelocity(player.getEyeLocation().getDirection().normalize().multiply(5)), 1L);
    }

    @EventHandler public void onTenthHit(TenHitsTakenEvent event) {
        Player player = event.getPlayer(); LivingEntity target = event.getLastAttacker();
        if (!plugin.getDataManager().hasEffect(player, this) || plugin.getRegionBlocker().isEffectBlocked(player, this)) return;
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 100, 2));
        Location chargeLocation = player.getLocation().add(0, 1, 0); WindCharge windCharge = player.getWorld().spawn(chargeLocation, WindCharge.class);
        windCharge.setVelocity(player.getLocation().subtract(0, 1, 0).toVector().subtract(chargeLocation.toVector()).normalize()); windCharge.setShooter(player); player.setVelocity(new Vector(0, 0.5, 0));
    }

    @EventHandler public void onPlayerFallDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && event.getCause() == DamageCause.FALL && plugin.getDataManager().hasEffect(player, this) && !plugin.getRegionBlocker().isEffectBlocked(player, this)) event.setCancelled(true);
    }
    @EventHandler public void onPlayerRightClickWindcharge(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getDataManager().hasEffect(player, this) || plugin.getRegionBlocker().isEffectBlocked(player, this)) return;
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType() != Material.WIND_CHARGE || player.hasCooldown(Material.WIND_CHARGE) || !event.getAction().isRightClick()) return;
        SchedulerUtil.runDelayed(player, plugin, () -> player.setCooldown(Material.WIND_CHARGE, 5), 1L);
    }
    @EventHandler public void onWindChargeLaunch(ProjectileLaunchEvent event) {
        if (event.getEntity() instanceof WindCharge windCharge && windCharge.getShooter() instanceof Player player) windCharge.setVelocity(player.getEyeLocation().getDirection().normalize().multiply(2));
    }
    @EventHandler public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker) || !plugin.getDataManager().hasEffect(attacker, this) || plugin.getRegionBlocker().isEffectBlocked(attacker, this) || attacker.getFallDistance() < 7) return;
        attacker.getWorld().playSound(attacker.getLocation(), Sound.ITEM_MACE_SMASH_AIR, 1, 1); attacker.getWorld().spawnParticle(Particle.GUST_EMITTER_SMALL, attacker.getLocation(), 1, 0, 0, 0, 0); attacker.setVelocity(new Vector(0, 1.8, 0)); event.setDamage(event.getDamage() * 1.1);
    }
}
