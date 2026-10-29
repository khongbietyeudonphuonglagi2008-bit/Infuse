package com.catadmirer.infuseSMP.effects;

import com.catadmirer.infuseSMP.EffectConstants;
import com.catadmirer.infuseSMP.Infuse;
import com.catadmirer.infuseSMP.Message;
import com.catadmirer.infuseSMP.events.EffectEquipEvent;
import com.catadmirer.infuseSMP.events.TenHitsGivenEvent;
import com.catadmirer.infuseSMP.managers.CooldownManager;
import com.catadmirer.infuseSMP.util.SchedulerUtil;
import com.destroystokyo.paper.MaterialSetTag;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class Frost extends InfuseEffect {
    private static final Set<UUID> frozenAttackers = ConcurrentHashMap.newKeySet();
    private static final Set<Location> frozenSnow = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, ScanState> lastSnowScan = new ConcurrentHashMap<>();
    private static final Map<UUID, AtomicInteger> jumpLocks = new ConcurrentHashMap<>();
    private static final Map<UUID, Double> originalJumpStrength = new ConcurrentHashMap<>();
    private static final long SNOW_SCAN_MIN_NANOS = 250_000_000L;

    public Frost() { this(false); }
    public Frost(boolean augmented) { super("frost", EffectConstants.Id.FROST, augmented, EffectConstants.PotionColor.FROST, EffectConstants.RitualColor.FROST, EffectConstants.BackgroundColor.FROST); }

    @Override public void equip(Player owner) { if (!plugin.getRegionBlocker().isEffectBlocked(owner, this)) changeToSnow(owner); }
    @Override public void unequip(Player owner) { lastSnowScan.remove(owner.getUniqueId()); }

    @Override public void applyPassives(Player owner) {
        if (plugin.getRegionBlocker().isEffectBlocked(owner, this)) return;
        Location probe = owner.getLocation().clone();
        int minY = owner.getWorld().getMinHeight();
        for (int i = 0; i < 16 && probe.getBlockY() >= minY; i++, probe.subtract(0, 1, 0)) {
            Material material = probe.getBlock().getType();
            if (!probe.getBlock().isSolid() && material != Material.SNOW) continue;
            if (MaterialSetTag.ICE.isTagged(material) || material == Material.SNOW || material == Material.SNOW_BLOCK) owner.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 40, 2, false, false));
            break;
        }
    }

    @Override public void activateSpark(Player owner, String slot) {
        UUID ownerId = owner.getUniqueId();
        if (CooldownManager.isOnCooldown(ownerId, plainKey + "_" + slot)) return;
        if (!plugin.getRegionBlocker().canUseSpark(owner) || plugin.getRegionBlocker().isEffectBlocked(owner, this)) return;
        owner.getWorld().playSound(owner.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1, 1);
        owner.addPotionEffect(new PotionEffect(PotionEffectType.UNLUCK, 300, 0));
        long cooldown = plugin.getMainConfig().cooldown(this), duration = plugin.getMainConfig().duration(this), durationTicks = Math.max(1L, duration * 20L);
        CooldownManager.setTimes(ownerId, plainKey + "_" + slot, duration, cooldown);
        Location center = owner.getLocation().clone();
        double radius = plugin.getMainConfig().frostSparkRadius();
        SchedulerUtil.forEachNearbyPlayer(plugin, center, radius,
                target -> !target.getUniqueId().equals(ownerId) && !plugin.getTrustManager().doesTrust(ownerId, target.getUniqueId()),
                target -> {
                    if (!plugin.getRegionBlocker().canBeTargetedBySpark(target) || plugin.getRegionBlocker().isEffectBlocked(target, Frost.this)) return;
                    applyJumpLock(target, durationTicks);
                });
        frozenAttackers.add(ownerId);
        SchedulerUtil.runDelayed(owner, plugin, () -> frozenAttackers.remove(ownerId), durationTicks);
    }

    private void applyJumpLock(Player target, long durationTicks) {
        AttributeInstance jump = target.getAttribute(Attribute.JUMP_STRENGTH);
        if (jump == null) return;
        UUID id = target.getUniqueId();
        jumpLocks.compute(id, (ignored, current) -> {
            if (current == null) { originalJumpStrength.put(id, jump.getBaseValue()); return new AtomicInteger(1); }
            current.incrementAndGet(); return current;
        });
        jump.setBaseValue(Math.min(jump.getBaseValue(), 0.1));
        SchedulerUtil.runDelayed(target, plugin, () -> {
            AtomicInteger active = jumpLocks.get(id);
            if (active == null || active.decrementAndGet() > 0) return;
            jumpLocks.remove(id, active);
            Double original = originalJumpStrength.remove(id);
            AttributeInstance current = target.getAttribute(Attribute.JUMP_STRENGTH);
            if (current != null && original != null) current.setBaseValue(original);
        }, durationTicks);
    }

    @Override public InfuseEffect getRegularVersion() { return new Frost(); }
    @Override public InfuseEffect getAugmentedVersion() { return new Frost(true); }
    @Override public Message getName() { return new Message(augmented ? Message.MessageType.AUG_FROST_NAME : Message.MessageType.FROST_NAME); }
    @Override public Message getLore() { return new Message(augmented ? Message.MessageType.AUG_FROST_LORE : Message.MessageType.FROST_LORE); }

    public void changeToSnow(Player player) {
        if (plugin.getRegionBlocker().isEffectBlocked(player, this)) return;
        int radius = Math.max(0, plugin.getMainConfig().frostPassiveSnowChangingRadius());
        Location center = player.getLocation().toBlockLocation();
        long now = System.nanoTime();
        ScanState next = new ScanState(center.getWorld().getUID(), center.getBlockX(), center.getBlockY(), center.getBlockZ(), now);
        ScanState previous = lastSnowScan.get(player.getUniqueId());
        if (previous != null && previous.sameBlock(next) && now - previous.nanoTime < SNOW_SCAN_MIN_NANOS) return;
        lastSnowScan.put(player.getUniqueId(), next);
        for (int dx = -radius; dx <= radius; dx++) for (int dy = -radius; dy <= radius; dy++) for (int dz = -radius; dz <= radius; dz++) {
            Block powder = center.clone().add(dx, dy, dz).getBlock();
            if (powder.getType() != Material.POWDER_SNOW || powder.getRelative(BlockFace.UP).getType() != Material.AIR) continue;
            Location blockLocation = powder.getLocation().toBlockLocation();
            if (plugin.getRegionBlocker().isEffectBlocked(blockLocation, this) || !frozenSnow.add(blockLocation)) continue;
            powder.setType(Material.SNOW_BLOCK, false);
            monitorSnow(player, blockLocation, radius);
        }
    }

    private void monitorSnow(Player owner, Location snowLocation, int radius) {
        Location immutableLocation = snowLocation.clone();
        Runnable retired = () -> restoreSnow(immutableLocation);
        SchedulerUtil.runDelayed(owner, plugin, () -> {
            boolean keep = owner.getWorld().getUID().equals(immutableLocation.getWorld().getUID()) && owner.getLocation().distanceSquared(immutableLocation) <= (double) radius * radius && plugin.getDataManager().hasEffect(owner, this);
            SchedulerUtil.runRegion(immutableLocation, plugin, ignored -> {
                Block block = immutableLocation.getBlock();
                if (block.getType() != Material.SNOW_BLOCK) { frozenSnow.remove(immutableLocation); return; }
                if (keep) monitorSnow(owner, immutableLocation, radius);
                else { block.setType(Material.POWDER_SNOW, false); frozenSnow.remove(immutableLocation); }
            });
        }, retired, 10L);
    }

    private void restoreSnow(Location location) {
        if (!plugin.isEnabled()) return;
        SchedulerUtil.runRegion(location, plugin, ignored -> {
            if (location.getBlock().getType() == Material.SNOW_BLOCK) location.getBlock().setType(Material.POWDER_SNOW, false);
            frozenSnow.remove(location);
        });
    }

    @EventHandler public void onCancelSwim(EntityToggleGlideEvent event) {
        if (event.isGliding() || !(event.getEntity() instanceof Player player)) return;
        if (!plugin.getDataManager().hasEffect(player, this) || plugin.getRegionBlocker().isEffectBlocked(player, this)) return;
        if (player.isInPowderedSnow()) event.setCancelled(true);
    }

    @EventHandler public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getDataManager().hasEffect(player, this) || plugin.getRegionBlocker().isEffectBlocked(player, this)) return;
        if (!event.hasChangedBlock() && event.getFrom().distanceSquared(event.getTo()) < 0.01) return;
        if (player.getLocation().getBlock().getType() == Material.POWDER_SNOW) player.setVelocity(player.getLocation().getDirection().normalize().multiply(plugin.getMainConfig().frostPassiveWalkSpeed()));
        else if (event.hasChangedBlock()) changeToSnow(player);
    }

    @EventHandler public void onPlayerInteractWithWindCharge(PlayerInteractEvent event) {
        Player player = event.getPlayer(); ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType() == Material.WIND_CHARGE && !plugin.getRegionBlocker().isEffectBlocked(player, this) && player.getFreezeTicks() > 1) event.setCancelled(true);
    }

    @EventHandler public void onTenthAttack(TenHitsGivenEvent event) {
        Player attacker = event.getPlayer();
        if (!plugin.getDataManager().hasEffect(attacker, this) || plugin.getRegionBlocker().isEffectBlocked(attacker, this)) return;
        var target = event.getLastTarget(); final int[] elapsed = {0};
        SchedulerUtil.runAtFixedRate(target, plugin, task -> {
            if (elapsed[0] >= 200 || !target.isValid()) { if (target.isValid()) target.setFreezeTicks(0); task.cancel(); return; }
            target.setFreezeTicks(target.getFreezeTicks() + 2); elapsed[0] += 2;
        }, null, 1L, 2L);
    }

    @EventHandler public void onPlayerJoin(EffectEquipEvent event) {
        Player player = event.getPlayer(); UUID id = player.getUniqueId(); if (!jumpLocks.containsKey(id)) return;
        AttributeInstance jump = player.getAttribute(Attribute.JUMP_STRENGTH); if (jump != null) jump.setBaseValue(0.1);
    }

    @EventHandler public void onPlayerAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker) || !attacker.hasPotionEffect(PotionEffectType.UNLUCK) || plugin.getRegionBlocker().isEffectBlocked(attacker, this)) return;
        PotionEffect effect = attacker.getPotionEffect(PotionEffectType.UNLUCK);
        if (effect != null && effect.getAmplifier() >= 0 && frozenAttackers.contains(attacker.getUniqueId()) && event.getEntity() instanceof Player target) target.setFreezeTicks(200);
    }

    private record ScanState(UUID world, int x, int y, int z, long nanoTime) {
        boolean sameBlock(ScanState other) { return world.equals(other.world) && x == other.x && y == other.y && z == other.z; }
    }
}
