package com.catadmirer.infuseSMP.effects;

import com.catadmirer.infuseSMP.EffectConstants;
import com.catadmirer.infuseSMP.Message;
import com.catadmirer.infuseSMP.events.TenHitsGivenEvent;
import com.catadmirer.infuseSMP.managers.CooldownManager;
import com.catadmirer.infuseSMP.util.SchedulerUtil;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.FoodProperties;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import java.util.UUID;

public class Regen extends InfuseEffect {
    public Regen() { this(false); }
    public Regen(boolean augmented) { super("regen", EffectConstants.Id.REGEN, augmented, EffectConstants.PotionColor.REGEN, EffectConstants.RitualColor.REGEN, EffectConstants.BackgroundColor.REGEN); }
    @Override public void equip(Player owner) {
        if (!plugin.getRegionBlocker().isEffectBlocked(owner, this)) owner.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, -1, 0, false, false));
    }
    @Override public void unequip(Player owner) { owner.removePotionEffect(PotionEffectType.REGENERATION); }
    @Override public void activateSpark(Player owner, String slot) {
        UUID playerUUID = owner.getUniqueId();
        if (CooldownManager.isOnCooldown(playerUUID, plainKey + "_" + slot) || !plugin.getRegionBlocker().canUseSpark(owner) || plugin.getRegionBlocker().isEffectBlocked(owner, this)) return;
        long cooldown = plugin.getMainConfig().cooldown(this), duration = plugin.getMainConfig().duration(this);
        CooldownManager.setTimes(playerUUID, plainKey + "_" + slot, duration, cooldown);
        owner.getWorld().playSound(owner.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1, 1);
    }
    @Override public InfuseEffect getRegularVersion() { return new Regen(); }
    @Override public InfuseEffect getAugmentedVersion() { return new Regen(true); }
    @Override public Message getName() { return new Message(augmented ? Message.MessageType.AUG_REGEN_NAME : Message.MessageType.REGEN_NAME); }
    @Override public Message getLore() { return new Message(augmented ? Message.MessageType.AUG_REGEN_LORE : Message.MessageType.REGEN_LORE); }

    @EventHandler public void regenRegenerateOnHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player) || !plugin.getDataManager().hasEffect(player, this) || plugin.getRegionBlocker().isEffectBlocked(player, this)) return;
        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 60, 1, false, false));
        if (CooldownManager.isEffectActive(player.getUniqueId(), "regen")) {
            double radius = plugin.getMainConfig().regenSparkHealTrustedRadius(), healAmount = event.getDamage() / 2.0; UUID ownerId = player.getUniqueId();
            SchedulerUtil.forEachNearbyPlayer(plugin, player.getLocation().clone(), radius,
                    other -> !other.getUniqueId().equals(ownerId) && plugin.getTrustManager().doesTrust(ownerId, other.getUniqueId()),
                    other -> { if (!plugin.getRegionBlocker().isEffectBlocked(other, this)) other.heal(healAmount); });
        }
    }
    @EventHandler public void consume(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer(); if (!plugin.getDataManager().hasEffect(player, this) || plugin.getRegionBlocker().isEffectBlocked(player, this)) return; player.setSaturation(player.getSaturation() + 6);
    }
    @SuppressWarnings("UnstableApiUsage")
    @EventHandler public void regenCanAlwaysEat(PlayerInteractEvent event) {
        if (!event.getAction().isRightClick()) return; Player player = event.getPlayer(); ItemStack item = event.getItem();
        if (item == null || !item.getType().isEdible() || item.getType().getDefaultData(DataComponentTypes.FOOD).canAlwaysEat()) return;
        if (plugin.getDataManager().hasEffect(player, this) && !plugin.getRegionBlocker().isEffectBlocked(player, this)) {
            FoodProperties properties = item.getData(DataComponentTypes.FOOD);
            properties = properties.toBuilder().canAlwaysEat(true).build(); item.setData(DataComponentTypes.FOOD, properties);
        } else item.resetData(DataComponentTypes.FOOD);
    }
    @EventHandler public void onTenthAttack(TenHitsGivenEvent event) {
        if (!plugin.getDataManager().hasEffect(event.getPlayer(), this) || plugin.getRegionBlocker().isEffectBlocked(event.getPlayer(), this)) return;
        if (!(event.getLastTarget() instanceof Player target) || plugin.getRegionBlocker().isEffectBlocked(target, this)) return;
        target.setFoodLevel(target.getFoodLevel() - 2);
    }
    @EventHandler public void regenPreserveHunger(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player && plugin.getDataManager().hasEffect(player, this) && !plugin.getRegionBlocker().isEffectBlocked(player, this)) event.setFoodLevel(20);
    }
}
