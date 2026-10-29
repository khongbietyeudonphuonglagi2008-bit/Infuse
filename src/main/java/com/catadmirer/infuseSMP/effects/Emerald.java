package com.catadmirer.infuseSMP.effects;

import com.catadmirer.infuseSMP.EffectConstants;
import com.catadmirer.infuseSMP.Infuse;
import com.catadmirer.infuseSMP.Message;
import com.catadmirer.infuseSMP.Message.MessageType;
import com.catadmirer.infuseSMP.events.TenHitsTakenEvent;
import com.catadmirer.infuseSMP.managers.CooldownManager;
import com.catadmirer.infuseSMP.util.SchedulerUtil;
import com.catadmirer.infuseSMP.util.ItemUtil;
import com.destroystokyo.paper.event.player.PlayerPickupExperienceEvent;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.Enchantable;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.enchantments.CraftEnchantment;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.craftbukkit.inventory.view.CraftEnchantmentView;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.enchantments.EnchantmentOffer;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.PrepareItemEnchantEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.List;
import java.util.Random;
import java.util.UUID;

public class Emerald extends InfuseEffect {
    public static final NamespacedKey LOOTING_KEY = new NamespacedKey("infuse", "emerald_looting");

    public Emerald() { this(false); }
    public Emerald(boolean augmented) { super("emerald", EffectConstants.Id.EMERALD, augmented, EffectConstants.PotionColor.EMERALD, EffectConstants.RitualColor.EMERALD, EffectConstants.BackgroundColor.EMERALD); }

    @Override public void equip(Player owner) {
        if (plugin.getRegionBlocker().isEffectBlocked(owner, this)) return;
        owner.addPotionEffect(new PotionEffect(PotionEffectType.HERO_OF_THE_VILLAGE, -1, 0));
        if (ItemUtil.isSword(owner.getInventory().getItemInMainHand())) ItemUtil.applySpecialEnchantment(owner.getInventory().getItemInMainHand(), LOOTING_KEY, Enchantment.LOOTING, plugin.getMainConfig().emeraldLootingLevel());
    }
    @Override public void unequip(Player owner) {
        owner.removePotionEffect(PotionEffectType.HERO_OF_THE_VILLAGE);
        for (ItemStack item : owner.getInventory()) {
            if (item != null && item.getType() != Material.AIR) ItemUtil.removeSpecialEnchant(item, Emerald.LOOTING_KEY, Enchantment.LOOTING);
        }
    }
    @Override public void activateSpark(Player owner, String slot) {
        UUID playerUUID = owner.getUniqueId();
        if (CooldownManager.isOnCooldown(playerUUID, plainKey + "_" + slot) || !plugin.getRegionBlocker().canUseSpark(owner) || plugin.getRegionBlocker().isEffectBlocked(owner, this)) return;
        owner.playSound(owner.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1, 1);
        long cooldown = plugin.getMainConfig().cooldown(this), duration = plugin.getMainConfig().duration(this);
        owner.addPotionEffect(new PotionEffect(PotionEffectType.HERO_OF_THE_VILLAGE, (int) duration * 20, 4));
        CooldownManager.setTimes(playerUUID, plainKey + "_" + slot, duration, cooldown);
    }
    @Override public InfuseEffect getRegularVersion() { return new Emerald(); }
    @Override public InfuseEffect getAugmentedVersion() { return new Emerald(true); }
    @Override public Message getName() { return new Message(augmented ? MessageType.AUG_EMERALD_NAME : MessageType.EMERALD_NAME); }
    @Override public Message getLore() { return new Message(augmented ? MessageType.AUG_EMERALD_LORE : MessageType.EMERALD_LORE); }

    @EventHandler public void enchantHeldItem(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getDataManager().hasEffect(player, this) || plugin.getRegionBlocker().isEffectBlocked(player, this)) return;
        ItemStack item = player.getInventory().getItem(event.getNewSlot());
        if (ItemUtil.isSword(item)) ItemUtil.applySpecialEnchantment(item, LOOTING_KEY, Enchantment.LOOTING, plugin.getMainConfig().emeraldLootingLevel());
    }
    @EventHandler public void removeLootingWhenStored(InventoryCloseEvent event) {
        if (event.getView().getType() == InventoryType.PLAYER) return;
        for (ItemStack item : event.getView().getTopInventory().getContents()) if (item != null && item.getType() != Material.AIR) ItemUtil.removeSpecialEnchant(item, Emerald.LOOTING_KEY, Enchantment.LOOTING);
    }
    @EventHandler public void removeLootingWhenDropped(PlayerDropItemEvent event) { ItemUtil.removeSpecialEnchant(event.getItemDrop().getItemStack(), Emerald.LOOTING_KEY, Enchantment.LOOTING); }
    @EventHandler public void tenHitEvent(TenHitsTakenEvent event) {
        if (!plugin.getDataManager().hasEffect(event.getPlayer(), this) || plugin.getRegionBlocker().isEffectBlocked(event.getPlayer(), this) || plugin.getRegionBlocker().isEffectBlocked(event.getLastAttacker(), this)) return;
        new FoodAndExpLock(plugin, event.getLastAttacker(), plugin.getMainConfig().emeraldLockDurationSeconds());
    }

    public static class FoodAndExpLock implements Listener {
        private final LivingEntity player;
        public FoodAndExpLock(Infuse plugin, LivingEntity player, double durationSeconds) {
            this.player = player;
            SchedulerUtil.runGlobal(plugin, ignored -> Bukkit.getPluginManager().registerEvents(this, plugin));
            SchedulerUtil.runGlobalDelayed(plugin, ignored -> HandlerList.unregisterAll(this), Math.max(1L, (long) (durationSeconds * 20)));
        }
        @EventHandler public void preventFoodChange(FoodLevelChangeEvent event) { if (event.getEntity().getUniqueId().equals(player.getUniqueId())) event.setCancelled(true); }
        @EventHandler public void preventExpChange(PlayerExpChangeEvent event) { if (event.getPlayer().getUniqueId().equals(player.getUniqueId())) event.setAmount(0); }
        @Override public boolean equals(Object obj) { return obj instanceof FoodAndExpLock other && other.player.getUniqueId().equals(player.getUniqueId()); }
    }

    @EventHandler public void emeraldExpMultiplier(PlayerPickupExperienceEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getDataManager().hasEffect(player, this) || plugin.getRegionBlocker().isEffectBlocked(player, this)) return;
        ExperienceOrb orb = event.getExperienceOrb();
        double multiplier = CooldownManager.isEffectActive(player.getUniqueId(), "emerald") ? plugin.getMainConfig().emeraldMultiplierUseEffect() : plugin.getMainConfig().emeraldMultiplierStandard();
        orb.setExperience((int) Math.round(orb.getExperience() * multiplier));
    }

    @SuppressWarnings({"UnstableApiUsage", "unchecked"})
    @EventHandler public void emeraldEnchantBonus(PrepareItemEnchantEvent event) {
        ItemStack item = event.getItem();
        if (!item.hasData(DataComponentTypes.ENCHANTABLE) || !item.getEnchantments().isEmpty()) return;
        Player player = event.getEnchanter();
        if (!plugin.getDataManager().hasEffect(player, this) || plugin.getRegionBlocker().isEffectBlocked(player, this)) return;
        EnchantmentOffer[] offers = event.getOffers(); Random random = new Random(player.getEnchantmentSeed());
        for (int k = 0; k < 3; k++) {
            Enchantable enchantable = item.getData(DataComponentTypes.ENCHANTABLE);
            if (enchantable == null) { offers[k] = null; continue; }
            int i = random.nextInt(1, 9) + 7 + random.nextInt(0, 16);
            int cost = k == 0 ? Math.max(i / 3, 1) : k == 1 ? i * 2 / 3 + 1 : Math.max(i, 30);
            if (cost < k + 1) { offers[k] = null; continue; }
            try {
                EnchantmentMenu menu = (EnchantmentMenu) ((CraftEnchantmentView) event.getView()).getHandle();
                Method getEnchantmentList = menu.getClass().getDeclaredMethod("getEnchantmentList", RegistryAccess.class, net.minecraft.world.item.ItemStack.class, int.class, int.class);
                getEnchantmentList.setAccessible(true);
                List<?> list = (List<?>) getEnchantmentList.invoke(menu, ((CraftWorld) player.getWorld()).getHandle().registryAccess(), CraftItemStack.asNMSCopy(item), k, cost);
                if (!list.isEmpty()) {
                    EnchantmentInstance instance = (EnchantmentInstance) list.get(random.nextInt(list.size()));
                    Holder<net.minecraft.world.item.enchantment.Enchantment> enchantment; int level;
                    Class<EnchantmentInstance> clazz = EnchantmentInstance.class;
                    if (!clazz.isRecord()) { enchantment = (Holder<net.minecraft.world.item.enchantment.Enchantment>) clazz.getField("enchantment").get(instance); level = (int) clazz.getField("level").get(instance); }
                    else { RecordComponent[] components = clazz.getRecordComponents(); enchantment = (Holder<net.minecraft.world.item.enchantment.Enchantment>) components[0].getAccessor().invoke(instance); level = (int) components[1].getAccessor().invoke(instance); }
                    offers[k] = new EnchantmentOffer(CraftEnchantment.minecraftHolderToBukkit(enchantment), level, cost);
                }
                getEnchantmentList.setAccessible(false);
            } catch (NoSuchMethodException e) { Infuse.LOGGER.error("Could not find the \"getEnchantmentList\" method in the EnchantmentMenu class"); }
            catch (Exception e) { Infuse.LOGGER.error("Error while calculating enchantments:", e); }
        }
    }

    @EventHandler public void stealExp(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player damaged) || !(event.getDamageSource().getCausingEntity() instanceof Player attacker) || !plugin.getDataManager().hasEffect(attacker, this)) return;
        if (plugin.getRegionBlocker().isEffectBlocked(attacker, this) || plugin.getRegionBlocker().isEffectBlocked(damaged, this)) return;
        int exp = damaged.getTotalExperience(), expPerHit = plugin.getMainConfig().emeraldExpPerHit();
        damaged.setTotalExperience(Math.max(exp - expPerHit, 0));
        int toGain = (int) (Math.min(expPerHit, exp) * plugin.getMainConfig().emeraldExpPercent());
        attacker.setTotalExperience(attacker.getTotalExperience() + toGain);
        new PlayerExpChangeEvent(attacker, toGain).callEvent();
    }

    @EventHandler public void emeraldPreserveConsumables(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getDataManager().hasEffect(player, this) || plugin.getRegionBlocker().isEffectBlocked(player, this)) return;
        ItemStack consumedItem = event.getItem(); if (consumedItem.getType() == Material.POTION) return;
        double chance = CooldownManager.isEffectActive(player.getUniqueId(), "emerald") ? 0.75 : 0.5;
        if (Math.random() > chance) return;
        consumedItem.add(1); event.setItem(consumedItem);
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1, 1);
        player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, player.getLocation(), 3, 1.5, 0.5, 0.5, 0.01);
    }

    @EventHandler public void expShare(PlayerExpChangeEvent event) {
        Player player = event.getPlayer();
        if (!CooldownManager.isEffectActive(player.getUniqueId(), "emerald") || plugin.getRegionBlocker().isEffectBlocked(player, this)) return;
        int toGain = (int) (event.getAmount() * plugin.getMainConfig().emeraldPercentExpToShare());
        if (toGain <= 0) return;
        for (OfflinePlayer trusted : plugin.getTrustManager().getTrusted(player)) {
            Player trustedPlayer = trusted.getPlayer(); if (trustedPlayer == null) continue;
            SchedulerUtil.run(trustedPlayer, plugin, () -> {
                if (!trustedPlayer.isOnline() || plugin.getRegionBlocker().isEffectBlocked(trustedPlayer, this)) return;
                trustedPlayer.setTotalExperience(trustedPlayer.getTotalExperience() + toGain);
            });
        }
    }
}
