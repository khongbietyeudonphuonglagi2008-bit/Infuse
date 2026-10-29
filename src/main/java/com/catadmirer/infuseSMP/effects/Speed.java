package com.catadmirer.infuseSMP.effects;

import com.catadmirer.infuseSMP.EffectConstants;
import com.catadmirer.infuseSMP.Message;
import com.catadmirer.infuseSMP.managers.CooldownManager;
import com.catadmirer.infuseSMP.managers.ParticleManager;
import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;

public class Speed extends InfuseEffect {
    private static final Map<UUID, Integer> speedLevels = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> lastHitTime = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> bowPullStartTime = new ConcurrentHashMap<>();
    public Speed() { this(false); }
    public Speed(boolean augmented) { super("speed", EffectConstants.Id.SPEED, augmented, EffectConstants.PotionColor.SPEED, EffectConstants.RitualColor.SPEED, EffectConstants.BackgroundColor.SPEED); }
    @Override public void equip(Player owner) {
        if (plugin.getRegionBlocker().isEffectBlocked(owner, this)) return;
        speedLevels.put(owner.getUniqueId(), 0);
        owner.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, -1, 0, false, false, false));
    }
    @Override public void unequip(Player owner) { speedLevels.remove(owner.getUniqueId()); owner.removePotionEffect(PotionEffectType.SPEED); }
    @Override public void applyPassives(Player owner) {
        if (plugin.getRegionBlocker().isEffectBlocked(owner, this)) return;
        UUID uuid = owner.getUniqueId(); long lastHit = lastHitTime.getOrDefault(uuid, 0L);
        if (System.currentTimeMillis() - lastHit > 1000L) speedLevels.put(uuid, 0);
        updateSpeedEffect(owner);
    }
    @Override public void activateSpark(Player owner, String slot) {
        UUID playerUUID = owner.getUniqueId();
        if (CooldownManager.isOnCooldown(playerUUID, plainKey + "_" + slot) || plugin.getRegionBlocker().isEffectBlocked(owner, this) || !plugin.getRegionBlocker().canUseSpark(owner)) return;
        owner.getWorld().playSound(owner.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1, 1);
        ParticleManager.spawnEffectCloud(owner, Color.fromRGB(0xD1A44B));
        double rotY = Math.min(owner.getPitch(), -15), rotX = owner.getYaw(), xz = Math.cos(Math.toRadians(rotY));
        Vector boost = new Vector(-xz * Math.sin(Math.toRadians(rotX)), -Math.sin(Math.toRadians(rotY)), xz * Math.cos(Math.toRadians(rotX)));
        owner.setVelocity(owner.getVelocity().add(boost.multiply(plugin.getMainConfig().speedDashMultiplier())));
        long cooldown = plugin.getMainConfig().cooldown(this), duration = plugin.getMainConfig().duration(this);
        CooldownManager.setTimes(playerUUID, plainKey + "_" + slot, duration, cooldown);
    }
    @Override public InfuseEffect getRegularVersion() { return new Speed(); }
    @Override public InfuseEffect getAugmentedVersion() { return new Speed(true); }
    @Override public Message getName() { return new Message(augmented ? Message.MessageType.AUG_SPEED_NAME : Message.MessageType.SPEED_NAME); }
    @Override public Message getLore() { return new Message(augmented ? Message.MessageType.AUG_SPEED_LORE : Message.MessageType.SPEED_LORE); }
    @Override public char getIcon(Player user, String slot) {
        UUID uuid = user.getUniqueId(); String key = String.format("%s_%s", plainKey, slot);
        if (CooldownManager.isEffectActive(uuid, key)) return icon(true, 0);
        return super.getIcon(user, slot);
    }
    public void updateSpeedEffect(Player owner) {
        if (!speedLevels.containsKey(owner.getUniqueId())) return;
        int lvl = Math.max(0, speedLevels.get(owner.getUniqueId()));
        owner.removePotionEffect(PotionEffectType.SPEED);
        owner.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, -1, lvl * plugin.getMainConfig().speedPlayerVelocityMultiplier(), false, false, false));
    }
    @EventHandler public void onEntityShootBow(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player) || !plugin.getDataManager().hasEffect(player, this) || plugin.getRegionBlocker().isEffectBlocked(player, this)) return;
        long startTime = bowPullStartTime.getOrDefault(player.getUniqueId(), 0L);
        float pullFraction = (float)Math.min(((System.currentTimeMillis() - startTime) * 1.8) / 1000, 1);
        event.getProjectile().setVelocity(event.getProjectile().getVelocity().multiply(pullFraction));
        bowPullStartTime.remove(player.getUniqueId());
    }
    @EventHandler public void sparkMovingParticles(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getDataManager().hasEffect(player, this) || !CooldownManager.isEffectActive(player.getUniqueId(), "speed")) return;
        Particle.DustOptions dustOptions = new Particle.DustOptions(Color.fromRGB(0xE6DCAA), 1.5F);
        player.getWorld().spawnParticle(Particle.DUST, event.getFrom(), 5, 0.1, 0.05, 0.1, 0.05, dustOptions);
    }
    @EventHandler public void onEntityDamageByEntityEvent(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player) || !plugin.getDataManager().hasEffect(player, this) || plugin.getRegionBlocker().isEffectBlocked(player, this)) return;
        UUID uuid = player.getUniqueId(); long currentTime = System.currentTimeMillis(), lastHit = lastHitTime.getOrDefault(uuid, 0L);
        if (currentTime - lastHit < 50L) return;
        lastHitTime.put(uuid, currentTime); speedLevels.put(uuid, speedLevels.getOrDefault(uuid, 0) + 1);
        if (event.getEntity() instanceof LivingEntity target) target.setNoDamageTicks(target.getNoDamageTicks() / 2);
    }
}
