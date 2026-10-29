package com.catadmirer.infuseSMP.effects;

import com.catadmirer.infuseSMP.EffectConstants;
import com.catadmirer.infuseSMP.Message;
import com.catadmirer.infuseSMP.Message.MessageType;
import com.catadmirer.infuseSMP.events.TenHitsGivenEvent;
import com.catadmirer.infuseSMP.managers.CooldownManager;
import com.catadmirer.infuseSMP.util.SchedulerUtil;
import io.papermc.paper.event.entity.EntityEquipmentChangedEvent;
import io.papermc.paper.event.player.PlayerClientLoadedWorldEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class Invis extends InfuseEffect {
    private static final Map<UUID, UUID> vanished = new ConcurrentHashMap<>();
    public static final MiniMessage mm = MiniMessage.miniMessage();

    public Invis() { this(false); }
    public Invis(boolean augmented) { super("invis", EffectConstants.Id.INVIS, augmented, EffectConstants.PotionColor.INVIS, EffectConstants.RitualColor.INVIS, EffectConstants.BackgroundColor.INVIS); }

    @Override public void equip(Player owner) {
        if (!plugin.getRegionBlocker().isEffectBlocked(owner, this)) owner.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, -1, 0, false, false));
    }
    @Override public void unequip(Player owner) { owner.removePotionEffect(PotionEffectType.INVISIBILITY); }

    @Override public void activateSpark(Player owner, String slot) {
        UUID ownerId = owner.getUniqueId();
        if (CooldownManager.isOnCooldown(ownerId, plainKey + "_" + slot)) return;
        if (!plugin.getRegionBlocker().canUseSpark(owner) || plugin.getRegionBlocker().isEffectBlocked(owner, this)) return;
        owner.playSound(owner.getLocation(), Sound.ENTITY_WITHER_SHOOT, 1, 1);
        long cooldown = plugin.getMainConfig().cooldown(this), duration = plugin.getMainConfig().duration(this);
        CooldownManager.setTimes(ownerId, plainKey + "_" + slot, duration, cooldown);

        final double radius = 10.0;
        final long durationTicks = Math.max(1L, duration * 20L);
        vanished.put(ownerId, ownerId);
        hidePlayer(owner);

        Location initialCenter = owner.getLocation().clone();
        SchedulerUtil.forEachNearbyPlayer(plugin, initialCenter, radius,
                player -> !player.getUniqueId().equals(ownerId) && plugin.getTrustManager().doesTrust(ownerId, player.getUniqueId()),
                player -> {
                    if (plugin.getRegionBlocker().isEffectBlocked(player, this)) return;
                    vanished.put(player.getUniqueId(), ownerId);
                    hidePlayer(player);
                });

        final long[] elapsed = {0L};
        SchedulerUtil.runAtFixedRate(owner, plugin, task -> {
            if (elapsed[0] >= durationTicks || !CooldownManager.isEffectActive(ownerId, "invis")) {
                task.cancel(); unhidePlayersOwnedBy(ownerId); return;
            }
            Location center = owner.getLocation().clone();
            Particle.DustOptions dust = new Particle.DustOptions(Color.BLACK, 4);
            for (int angle = 0; angle < 360; angle += 5) {
                double rad = Math.toRadians(angle);
                center.getWorld().spawnParticle(Particle.DUST, center.clone().add(radius * Math.cos(rad), 0, radius * Math.sin(rad)), 1, dust);
            }
            SchedulerUtil.forEachNearbyPlayer(plugin, center, radius,
                    target -> !target.getUniqueId().equals(ownerId) && !plugin.getTrustManager().doesTrust(ownerId, target.getUniqueId()),
                    target -> {
                        if (!plugin.getRegionBlocker().canBeTargetedBySpark(target) || plugin.getRegionBlocker().isEffectBlocked(target, Invis.this)) return;
                        target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 40, 0, false, false));
                        target.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 11, 0, false, false, false));
                    });
            elapsed[0] += 10L;
        }, () -> unhidePlayersOwnedBy(ownerId), 1L, 10L);
    }

    private void unhidePlayersOwnedBy(UUID ownerId) {
        Set<UUID> removed = new HashSet<>();
        vanished.forEach((hidden, source) -> { if (source.equals(ownerId) && vanished.remove(hidden, source)) removed.add(hidden); });
        if (removed.isEmpty() || !plugin.isEnabled()) return;
        SchedulerUtil.forEachOnlinePlayer(plugin, player -> { if (removed.contains(player.getUniqueId())) showPlayer(player); });
    }

    @Override public InfuseEffect getRegularVersion() { return new Invis(); }
    @Override public InfuseEffect getAugmentedVersion() { return new Invis(true); }
    @Override public Message getName() { return new Message(augmented ? MessageType.AUG_INVIS_NAME : MessageType.INVIS_NAME); }
    @Override public Message getLore() { return new Message(augmented ? MessageType.AUG_INVIS_LORE : MessageType.INVIS_LORE); }

    private void spawnBlackParticles(final Entity target, final int durationInSeconds) {
        final int maxTicks = Math.max(1, durationInSeconds * 20); final int[] elapsed = {0};
        SchedulerUtil.runAtFixedRate(target, plugin, task -> {
            if (elapsed[0] >= maxTicks || !target.isValid()) { task.cancel(); return; }
            target.getWorld().spawnParticle(Particle.SQUID_INK, target.getLocation().add(0, 1, 0), 3, 0.5, 0.5, 0.5, 0);
            elapsed[0] += 5;
        }, null, 1L, 5L);
    }

    public void hidePlayer(Player toHide) {
        UUID hiddenId = toHide.getUniqueId(); UUID worldId = toHide.getWorld().getUID();
        SchedulerUtil.forEachOnlinePlayer(plugin, viewer -> {
            if (!viewer.getWorld().getUID().equals(worldId) || plugin.getTrustManager().doesTrust(hiddenId, viewer.getUniqueId())) return;
            viewer.sendEquipmentChange(toHide, EquipmentSlot.HEAD, null);
            viewer.sendEquipmentChange(toHide, EquipmentSlot.CHEST, null);
            viewer.sendEquipmentChange(toHide, EquipmentSlot.LEGS, null);
            viewer.sendEquipmentChange(toHide, EquipmentSlot.FEET, null);
        });
    }

    public void showPlayer(Player toShow) {
        UUID worldId = toShow.getWorld().getUID();
        ItemStack head = toShow.getEquipment().getItem(EquipmentSlot.HEAD).clone();
        ItemStack chest = toShow.getEquipment().getItem(EquipmentSlot.CHEST).clone();
        ItemStack legs = toShow.getEquipment().getItem(EquipmentSlot.LEGS).clone();
        ItemStack feet = toShow.getEquipment().getItem(EquipmentSlot.FEET).clone();
        SchedulerUtil.forEachOnlinePlayer(plugin, viewer -> {
            if (!viewer.getWorld().getUID().equals(worldId)) return;
            viewer.sendEquipmentChange(toShow, EquipmentSlot.HEAD, head);
            viewer.sendEquipmentChange(toShow, EquipmentSlot.CHEST, chest);
            viewer.sendEquipmentChange(toShow, EquipmentSlot.LEGS, legs);
            viewer.sendEquipmentChange(toShow, EquipmentSlot.FEET, feet);
        });
    }

    @EventHandler public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity(); Player killer = victim.getKiller();
        if (killer == null || plugin.getRegionBlocker().isEffectBlocked(killer, this)) return;
        String victimName = plugin.getMainConfig().invisHideDeaths() && plugin.getDataManager().hasEffect(killer, this) ? "<gray><obf>Someone" : mm.serialize(victim.displayName());
        String killerName = plugin.getMainConfig().invisHideKills() && plugin.getDataManager().hasEffect(killer, this) ? "<gray><obf>Someone" : mm.serialize(killer.displayName());
        Message msg = new Message(MessageType.DEATH_MESSAGE); msg.applyPlaceholder("victim", victimName); msg.applyPlaceholder("killer", killerName); event.deathMessage(msg.toComponent());
    }

    @EventHandler public void onProjectileHit(ProjectileHitEvent event) {
        if (!(event.getEntity().getShooter() instanceof Player shooter) || !plugin.getDataManager().hasEffect(shooter, this) || plugin.getRegionBlocker().isEffectBlocked(shooter, this)) return;
        if (!(event.getEntity() instanceof Arrow) || !(event.getHitEntity() instanceof Player target) || plugin.getRegionBlocker().isEffectBlocked(target, this)) return;
        target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 80, 0, false, false)); spawnBlackParticles(target, 4);
    }

    @EventHandler public void onTenHits(TenHitsGivenEvent event) {
        Player attacker = event.getPlayer();
        if (!plugin.getDataManager().hasEffect(attacker, this) || plugin.getRegionBlocker().isEffectBlocked(attacker, this)) return;
        LivingEntity target = event.getLastTarget(); if (plugin.getRegionBlocker().isEffectBlocked(target, this)) return;
        target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 80, 0, false, false)); spawnBlackParticles(target, 4);
    }

    @EventHandler public void onEntityTarget(EntityTargetEvent event) {
        if (!(event.getTarget() instanceof Player target) || !plugin.getDataManager().hasEffect(target, this) || plugin.getRegionBlocker().isEffectBlocked(target, this)) return;
        event.setCancelled(true);
    }

    @EventHandler public void onJoin(PlayerClientLoadedWorldEvent event) {
        Player viewer = event.getPlayer();
        SchedulerUtil.runGlobal(plugin, ignored -> {
            for (Player hidden : org.bukkit.Bukkit.getOnlinePlayers()) {
                if (!vanished.containsKey(hidden.getUniqueId())) continue;
                viewer.getScheduler().run(plugin, task -> {
                    if (plugin.getTrustManager().doesTrust(hidden.getUniqueId(), viewer.getUniqueId())) return;
                    viewer.sendEquipmentChange(hidden, EquipmentSlot.HEAD, null);
                    viewer.sendEquipmentChange(hidden, EquipmentSlot.CHEST, null);
                    viewer.sendEquipmentChange(hidden, EquipmentSlot.LEGS, null);
                    viewer.sendEquipmentChange(hidden, EquipmentSlot.FEET, null);
                }, null);
            }
        });
    }

    @EventHandler public void onEquipmentChange(EntityEquipmentChangedEvent event) {
        if (event.getEntity() instanceof Player player && vanished.containsKey(player.getUniqueId())) hidePlayer(player);
    }
}
