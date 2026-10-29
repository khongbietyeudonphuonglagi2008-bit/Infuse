package com.catadmirer.infuseSMP.effects;

import com.catadmirer.infuseSMP.EffectConstants;
import com.catadmirer.infuseSMP.Message;
import com.catadmirer.infuseSMP.events.TenHitsGivenEvent;
import com.catadmirer.infuseSMP.managers.CooldownManager;
import com.catadmirer.infuseSMP.util.SchedulerUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.attribute.AttributeModifier.Operation;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import java.util.UUID;

public class Heart extends InfuseEffect {
    public static final NamespacedKey heartBoost = new NamespacedKey("infuse", "heart_boost");
    public static final NamespacedKey heartSparkBoost = new NamespacedKey("infuse", "heart_spark_boost");
    public Heart() { this(false); }
    public Heart(boolean augmented) { super("heart", EffectConstants.Id.HEART, augmented, EffectConstants.PotionColor.HEART, EffectConstants.RitualColor.HEART, EffectConstants.BackgroundColor.HEART); }

    @Override public void equip(Player owner) {
        if (plugin.getRegionBlocker().isEffectBlocked(owner, this)) return;
        AttributeInstance attribute = owner.getAttribute(Attribute.MAX_HEALTH);
        if (attribute == null) return;
        if (attribute.getModifier(heartBoost) == null) attribute.addModifier(new AttributeModifier(heartBoost, 10, Operation.ADD_NUMBER));
        owner.heal(10);
    }
    @Override public void unequip(Player owner) {
        AttributeInstance attribute = owner.getAttribute(Attribute.MAX_HEALTH);
        if (attribute == null) return;
        attribute.removeModifier(heartBoost);
        attribute.removeModifier(heartSparkBoost);
    }
    @Override public void activateSpark(Player owner, String slot) {
        UUID playerUUID = owner.getUniqueId();
        if (CooldownManager.isOnCooldown(playerUUID, plainKey + "_" + slot)) return;
        if (!plugin.getRegionBlocker().canUseSpark(owner) || plugin.getRegionBlocker().isEffectBlocked(owner, this)) return;
        owner.playSound(owner.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1, 1);
        AttributeInstance attribute = owner.getAttribute(Attribute.MAX_HEALTH);
        if (attribute == null) return;
        attribute.removeModifier(heartSparkBoost);
        attribute.addModifier(new AttributeModifier(heartSparkBoost, 10, Operation.ADD_NUMBER));
        owner.heal(attribute.getValue());
        long cooldown = plugin.getMainConfig().cooldown(this);
        long duration = plugin.getMainConfig().duration(this);
        CooldownManager.setTimes(playerUUID, plainKey + "_" + slot, duration, cooldown);
        SchedulerUtil.runDelayed(owner, plugin, () -> {
            AttributeInstance current = owner.getAttribute(Attribute.MAX_HEALTH);
            if (current != null) current.removeModifier(heartSparkBoost);
        }, duration * 20L);
    }

    @Override public InfuseEffect getRegularVersion() { return new Heart(); }
    @Override public InfuseEffect getAugmentedVersion() { return new Heart(true); }
    @Override public Message getName() { return new Message(augmented ? Message.MessageType.AUG_HEART_NAME : Message.MessageType.HEART_NAME); }
    @Override public Message getLore() { return new Message(augmented ? Message.MessageType.AUG_HEART_LORE : Message.MessageType.HEART_LORE); }

    private void showAndUpdateHealthAboveEntity(LivingEntity target) {
        SchedulerUtil.run(target, plugin, () -> {
            if (!target.isValid() || target.isDead()) return;
            Location location = target.getLocation().add(0, 2.5, 0);
            TextDisplay display = location.getWorld().spawn(location, TextDisplay.class);
            display.setGravity(false);
            display.setCustomNameVisible(true);
            updateHealthDisplay(display, target);
            target.addPassenger(display);
            final long[] elapsed = {0L};
            SchedulerUtil.runAtFixedRate(target, plugin, task -> {
                elapsed[0] += 10L;
                if (elapsed[0] >= 200L || target.isDead() || !target.isValid() || !display.isValid()) {
                    task.cancel();
                    if (display.isValid()) display.remove();
                    return;
                }
                updateHealthDisplay(display, target);
            }, () -> {}, 1L, 10L);
        });
    }

    private void updateHealthDisplay(TextDisplay entity, LivingEntity player) {
        double health = player.getHealth() + player.getAbsorptionAmount();
        if (player.hasPotionEffect(PotionEffectType.ABSORPTION)) entity.customName(Message.toComponent(String.format("<yellow><b>%.1f ❤", health)));
        else entity.customName(Message.toComponent(String.format("<red><b>%.1f ❤", player.getHealth())));
    }

    @EventHandler public void heartShowTargetHealth(TenHitsGivenEvent event) {
        Player attacker = event.getPlayer();
        if (!plugin.getDataManager().hasEffect(attacker, this) || plugin.getRegionBlocker().isEffectBlocked(attacker, this)) return;
        showAndUpdateHealthAboveEntity(event.getLastTarget());
    }

    @EventHandler public void onPlayerEat(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getDataManager().hasEffect(player, this) || plugin.getRegionBlocker().isEffectBlocked(player, this)) return;
        ItemStack item = event.getItem();
        if (item.getType() == Material.ENCHANTED_GOLDEN_APPLE) player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 2400, 4));
        else player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 600, 0));
    }
}
