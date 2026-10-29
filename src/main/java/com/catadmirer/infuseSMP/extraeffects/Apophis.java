package com.catadmirer.infuseSMP.extraeffects;

import com.catadmirer.infuseSMP.EffectConstants;
import com.catadmirer.infuseSMP.Infuse;
import com.catadmirer.infuseSMP.Message;
import com.catadmirer.infuseSMP.effects.Emerald.FoodAndExpLock;
import com.catadmirer.infuseSMP.effects.InfuseEffect;
import com.catadmirer.infuseSMP.events.TenHitsGivenEvent;
import com.catadmirer.infuseSMP.events.TenHitsTakenEvent;
import com.catadmirer.infuseSMP.managers.CooldownManager;
import com.catadmirer.infuseSMP.util.ItemUtil;
import com.catadmirer.infuseSMP.util.SchedulerUtil;
import com.destroystokyo.paper.event.player.PlayerPickupExperienceEvent;
import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.Enchantable;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.attribute.AttributeModifier.Operation;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.enchantments.CraftEnchantment;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.craftbukkit.inventory.view.CraftEnchantmentView;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.enchantments.EnchantmentOffer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.enchantment.PrepareItemEnchantEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Scanner;
import java.util.UUID;

public class Apophis extends InfuseEffect {
    public static final NamespacedKey LOOTING_KEY = new NamespacedKey("infuse", "apophis_looting");
    public static final NamespacedKey APOPHIS_BOOST = new NamespacedKey("infuse", "apophis_boost");
    public static final NamespacedKey APOPHIS_SPARK_BOOST = new NamespacedKey("infuse", "apophis_spark_boost");
    private static final MiniMessage mm = MiniMessage.miniMessage();
    private static final Component APOPHIS_NAME = Component.text("Apophis", NamedTextColor.DARK_PURPLE);
    private static final ProfileProperty APOPHIS_SKIN = new ProfileProperty("textures",
        "ewogICJ0aW1lc3RhbXAiIDogMTcxNzg4NTA2MDQwNywKICAicHJvZmlsZUlkIiA6ICJlZGUyYzdhMGFjNjM0MTNiYjA5ZDNmMGJlZTllYzhlYyIsCiAgInByb2ZpbGVOYW1lIiA6ICJ0aGVEZXZKYWRlIiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwKICAidGV4dHVyZXMiIDogewogICAgIlNLSU4iIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlL2MwOTBmY2NjMjBmMWM3ZWMyMDBkNGVkMDUxMjQwNjM3ZmRmNjE5ZDg1Nzg0NWZhNWRmNWJkMzM1MWJiMjBkOCIKICAgIH0KICB9Cn0=",
        "mBgGwS28lqNz7rJCysD9SElJpA5q+34uTZK68JFXIFzuoN31KQg2VHjVDz+/nAr0yXdRwOrgL5rnRb2NbKBPyKSWdcB8A1nVHeNMpoJ5c5CzEERyOROUiTRxge/MIhYL7Fkj67fkh7Sc/l7BwDAf7/7OIgiAIleUTLZ9COnIN15gylTBldOo3JOka8TTNrI1i4QmnMsbgT0luQZzrUMRtZxIHNwx+26IevzCE+hpNdwiYqnDVZdayDLPVy1vv+i3C7AJGd9b7/2/qv0YmWxvT3uKrPR8+9fbSWltGx9ikrdXO17FrGc5u0gqmPWAaSSWw/NJmMhPenILh7/MvXA8mO2m7JeuhnM/EYzdOMB3qzvkUEVddFIngPl6LNE8XG1R+APFBsbpnpybB7dQphSud5DNfuZijqLDd735kykYlRMzw5VVGf7fONheLzSV42XRsIU+5IazHvmAZ4pxr72+r9bbS9vRW38ZgQIy6p8r4tLv9jfmqmcS9lEn1CAgDLAqZWGzIWeIgOdDsrWH4ia/1gj6oZVefRCr2dAS84NsOQUdoJDbS8G0+ArN+CWgnlcwOJCS6MB5kBmQl2FPvwLcSnnRcS66XKfH28Bu2/J3Hu5zRWbONuOLQTbYFxwftUtvS1IORKBCfWvlJTx5G/mz1KOGW89iOCpW8jdx8EmzpRI=");

    public Apophis() { this(false); }
    public Apophis(boolean augmented) { super("apophis", EffectConstants.Id.APOPHIS, augmented, EffectConstants.PotionColor.APOPHIS, EffectConstants.RitualColor.APOPHIS, EffectConstants.BackgroundColor.APOPHIS); }

    @Override public void equip(Player owner) {
        if (plugin.getRegionBlocker().isEffectBlocked(owner, this)) return;
        owner.addPotionEffect(new PotionEffect(PotionEffectType.HERO_OF_THE_VILLAGE, -1, 0));
        owner.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, -1, 0, false, false));
        if (ItemUtil.isSword(owner.getInventory().getItemInMainHand())) ItemUtil.applySpecialEnchantment(owner.getInventory().getItemInMainHand(), LOOTING_KEY, Enchantment.LOOTING, plugin.getMainConfig().apophisLootingLevel());
        AttributeInstance attribute = owner.getAttribute(Attribute.MAX_HEALTH);
        if (attribute != null) { if (attribute.getModifier(APOPHIS_BOOST) == null) attribute.addModifier(new AttributeModifier(APOPHIS_BOOST, 10, Operation.ADD_NUMBER)); owner.heal(10); }
        disguise(owner);
    }

    @Override public void unequip(Player owner) {
        owner.removePotionEffect(PotionEffectType.HERO_OF_THE_VILLAGE);
        owner.removePotionEffect(PotionEffectType.FIRE_RESISTANCE);
        for (ItemStack item : owner.getInventory()) if (item != null && item.getType() != Material.AIR) ItemUtil.removeSpecialEnchant(item, LOOTING_KEY, Enchantment.LOOTING);
        AttributeInstance attribute = owner.getAttribute(Attribute.MAX_HEALTH);
        if (attribute != null) { attribute.removeModifier(APOPHIS_BOOST); attribute.removeModifier(APOPHIS_SPARK_BOOST); }
        removeDisguise(owner);
    }

    @Override public void activateSpark(Player owner, String slot) {
        UUID playerUUID = owner.getUniqueId();
        if (CooldownManager.isOnCooldown(playerUUID, plainKey + "_" + slot) || !plugin.getRegionBlocker().canUseSpark(owner) || plugin.getRegionBlocker().isEffectBlocked(owner, this)) return;
        owner.playSound(owner.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1, 1);
        long cooldown = plugin.getMainConfig().cooldown(this), duration = plugin.getMainConfig().duration(this);
        CooldownManager.setTimes(playerUUID, plainKey + "_" + slot, duration, cooldown);
        owner.addPotionEffect(new PotionEffect(PotionEffectType.HERO_OF_THE_VILLAGE, (int) duration * 20, 4));
        AttributeInstance attribute = owner.getAttribute(Attribute.MAX_HEALTH);
        if (attribute != null) { attribute.removeModifier(APOPHIS_SPARK_BOOST); attribute.addModifier(new AttributeModifier(APOPHIS_SPARK_BOOST, 10, Operation.ADD_NUMBER)); owner.heal(10); }
        SchedulerUtil.runDelayed(owner, plugin, () -> { AttributeInstance current = owner.getAttribute(Attribute.MAX_HEALTH); if (current != null) current.removeModifier(APOPHIS_SPARK_BOOST); }, duration * 20L);
        final double radius = plugin.getMainConfig().apophisSparkRadius();
        for (Entity entity : owner.getNearbyEntities(radius, radius, radius)) {
            if (!(entity instanceof LivingEntity living) || entity == owner) continue;
            SchedulerUtil.run(living, plugin, () -> { if (!plugin.getRegionBlocker().canBeTargetedBySpark(living) || plugin.getRegionBlocker().isEffectBlocked(living, this)) return; living.setFireTicks(100); });
        }
        spawnSparkEffect(owner);
        SchedulerUtil.runDelayed(owner, plugin, () -> owner.getWorld().spawnParticle(Particle.EXPLOSION, owner.getLocation(), 1), 20L);
    }

    @Override public InfuseEffect getRegularVersion() { return new Apophis(); }
    @Override public InfuseEffect getAugmentedVersion() { return new Apophis(true); }
    @Override public Message getName() { return new Message(augmented ? Message.MessageType.AUG_APOPHIS_NAME : Message.MessageType.APOPHIS_NAME); }
    @Override public Message getLore() { return new Message(augmented ? Message.MessageType.AUG_APOPHIS_LORE : Message.MessageType.APOPHIS_LORE); }

    private void spawnSparkEffect(final Player caster) {
        UUID casterId = caster.getUniqueId(); long[] elapsed = {0L};
        SchedulerUtil.runAtFixedRate(caster, plugin, task -> {
            elapsed[0] += 20L;
            if (elapsed[0] >= 100L) { Location finish = caster.getLocation().clone(); task.cancel(); startDarkRedDustEffect(finish, casterId); return; }
            Location center = caster.getLocation().clone(); World world = center.getWorld(); world.playSound(center, Sound.ENTITY_PLAYER_HURT_ON_FIRE, 1, 1);
            for (int angle = 0; angle < 360; angle += 20) { double rad = Math.toRadians(angle); world.spawnParticle(Particle.LAVA, center.clone().add(5 * Math.cos(rad), 0.1, 5 * Math.sin(rad)), 10, 0.05, 0.05, 0.05, 0.01); }
            SchedulerUtil.forEachNearbyPlayer(plugin, center, 5.0, target -> !target.getUniqueId().equals(casterId), target -> { if (!plugin.getRegionBlocker().canBeTargetedBySpark(target) || plugin.getRegionBlocker().isEffectBlocked(target, Apophis.this)) return; target.damage(8.0); });
        }, null, 20L, 20L);
    }

    private void startDarkRedDustEffect(final Location startLoc, UUID casterId) {
        final World world = startLoc.getWorld(); if (world == null) return;
        final Location center = startLoc.clone(); final double explosionRadius = plugin.getMainConfig().apophisSparkExplosionRadius();
        SchedulerUtil.forEachNearbyPlayer(plugin, center, explosionRadius, target -> !target.getUniqueId().equals(casterId), target -> target.setVelocity(new Vector(0, 2, 0)));
        SchedulerUtil.runRegion(center, plugin, ignored -> world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 1, 1));
        int[] tick = {0};
        SchedulerUtil.runRegionAtFixedRate(center, plugin, task -> {
            double particleHeightOffset = tick[0] * 3.0; if (tick[0] >= 60 || particleHeightOffset > 30.0) { task.cancel(); return; }
            double circleRadius = explosionRadius + tick[0] * 0.1;
            for (int angle = 0; angle < 360; angle += 5) { double rad = Math.toRadians(angle); Location particleLoc = center.clone().add(circleRadius * Math.cos(rad), particleHeightOffset, circleRadius * Math.sin(rad)); world.spawnParticle(Particle.DUST_PILLAR, particleLoc, 3, 0, 0, 0, 0, Material.REDSTONE_BLOCK.createBlockData()); }
            tick[0]++;
        }, 1L, 1L);
    }

    public void initDisguise(Player owner) {
        UUID uuid = owner.getUniqueId(); File disguiseFile = new File(plugin.getDataFolder(), "data/ApophisPlayers/" + uuid + ".yml"); disguiseFile.getParentFile().mkdirs(); if (disguiseFile.exists()) return;
        try (FileWriter writer = new FileWriter(disguiseFile)) {
            Optional<ProfileProperty> textures = owner.getPlayerProfile().getProperties().stream().filter(property -> "textures".equals(property.getName())).findFirst();
            writer.write(mm.serialize(owner.displayName())); writer.write("\n");
            if (textures.isEmpty()) writer.write("null\nnull");
            else { writer.write(textures.get().getValue()); writer.write("\n"); writer.write(String.valueOf(textures.get().getSignature())); }
        } catch (IOException err) { Infuse.LOGGER.error("Failed to write to {}. Make sure it can be created and edited by the user running the server.", disguiseFile.getPath()); }
    }

    public void disguise(Player owner) {
        initDisguise(owner); PlayerProfile profile = owner.getPlayerProfile(); profile.setProperty(APOPHIS_SKIN); owner.setPlayerProfile(profile); owner.displayName(APOPHIS_NAME); owner.playerListName(APOPHIS_NAME);
    }

    public void removeDisguise(Player owner) {
        UUID uuid = owner.getUniqueId(); File disguiseFile = new File(plugin.getDataFolder(), "data/ApophisPlayers/" + uuid + ".yml");
        try (Scanner scanner = new Scanner(disguiseFile)) {
            PlayerProfile profile = owner.getPlayerProfile(); String value = "", signature = "";
            if (scanner.hasNextLine()) { String read = scanner.nextLine(); owner.displayName(mm.deserialize(read)); owner.playerListName(mm.deserialize(read)); }
            if (scanner.hasNextLine()) value = scanner.nextLine();
            if (scanner.hasNextLine()) { signature = scanner.nextLine(); if (signature.equals("null")) signature = null; }
            profile.setProperty(new ProfileProperty("textures", value, signature)); owner.setPlayerProfile(profile);
        } catch (FileNotFoundException err) { return; }
        if (disguiseFile.exists() && !disguiseFile.delete()) Infuse.LOGGER.debug("Could not delete disguise file {}", disguiseFile);
    }

    private void showAndUpdateHealthAboveEntity(Entity entity) {
        if (!(entity instanceof LivingEntity target)) return;
        SchedulerUtil.run(target, plugin, () -> {
            if (!target.isValid() || target.isDead()) return;
            Location location = target.getLocation().add(0, 2.5, 0); TextDisplay display = location.getWorld().spawn(location, TextDisplay.class);
            display.setGravity(false); display.setCustomNameVisible(true); updateHealthDisplay(display, target); target.addPassenger(display);
            long[] elapsed = {0L};
            SchedulerUtil.runAtFixedRate(target, plugin, task -> { elapsed[0] += 10L; if (elapsed[0] >= 200L || target.isDead() || !target.isValid() || !display.isValid()) { task.cancel(); if (display.isValid()) display.remove(); return; } updateHealthDisplay(display, target); }, null, 1L, 10L);
        });
    }

    private void updateHealthDisplay(TextDisplay entity, LivingEntity player) {
        if (player.hasPotionEffect(PotionEffectType.ABSORPTION)) entity.customName(Message.toComponent(String.format("<yellow><b>%.1f ❤", player.getHealth()) + player.getAbsorptionAmount()));
        else entity.customName(Message.toComponent(String.format("<red><b>%.1f ❤", player.getHealth())));
    }

    @EventHandler public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer(); if (!player.isInLava() || !plugin.getDataManager().hasEffect(player, this) || plugin.getRegionBlocker().isEffectBlocked(player, this) || event.getFrom().distanceSquared(event.getTo()) < 0.01) return;
        player.setVelocity(player.getLocation().getDirection().normalize().multiply(plugin.getMainConfig().apophisLavaWalkSpeed()));
    }
    @EventHandler public void onEntityShootBow(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player) || !plugin.getDataManager().hasEffect(player, this) || plugin.getRegionBlocker().isEffectBlocked(player, this)) return;
        if (event.getForce() >= 1 && event.getProjectile() instanceof Projectile projectile) projectile.setFireTicks(100);
    }
    @EventHandler public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || event.getCause() != DamageCause.FALL || !plugin.getDataManager().hasEffect(player, this) || plugin.getRegionBlocker().isEffectBlocked(player, this)) return;
        Material blockType = player.getLocation().getBlock().getType(); if (blockType == Material.LAVA || blockType == Material.LAVA_CAULDRON) event.setCancelled(true);
    }
    @EventHandler public void apophisCombustTarget(TenHitsGivenEvent event) {
        Player attacker = event.getPlayer(); if (!plugin.getDataManager().hasEffect(attacker, this) || plugin.getRegionBlocker().isEffectBlocked(attacker, this) || plugin.getRegionBlocker().isEffectBlocked(event.getLastTarget(), this)) return; event.getLastTarget().setFireTicks(100);
    }
    @EventHandler public void apophisShowTargetHealth(TenHitsGivenEvent event) {
        Player attacker = event.getPlayer(); if (!plugin.getDataManager().hasEffect(attacker, this) || plugin.getRegionBlocker().isEffectBlocked(attacker, this)) return; showAndUpdateHealthAboveEntity(event.getLastTarget());
    }
    @EventHandler public void onPlayerEat(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer(); if (!plugin.getDataManager().hasEffect(player, this) || plugin.getRegionBlocker().isEffectBlocked(player, this)) return;
        if (event.getItem().getType() == Material.ENCHANTED_GOLDEN_APPLE) player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 2400, 4)); else player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 600, 0));
    }
    @EventHandler public void enchantHeldItem(PlayerItemHeldEvent event) {
        Player player = event.getPlayer(); if (!plugin.getDataManager().hasEffect(player, this) || plugin.getRegionBlocker().isEffectBlocked(player, this)) return; ItemStack item = player.getInventory().getItem(event.getNewSlot()); if (ItemUtil.isSword(item)) ItemUtil.applySpecialEnchantment(item, LOOTING_KEY, Enchantment.LOOTING, plugin.getMainConfig().apophisLootingLevel());
    }
    @EventHandler public void removeLootingWhenStored(InventoryCloseEvent event) {
        if (event.getView().getType() == InventoryType.PLAYER) return; for (ItemStack item : event.getView().getTopInventory().getContents()) if (item != null && item.getType() != Material.AIR) ItemUtil.removeSpecialEnchant(item, LOOTING_KEY, Enchantment.LOOTING);
    }
    @EventHandler public void removeLootingWhenDropped(PlayerDropItemEvent event) { ItemUtil.removeSpecialEnchant(event.getItemDrop().getItemStack(), LOOTING_KEY, Enchantment.LOOTING); }
    @EventHandler public void tenHitEvent(TenHitsTakenEvent event) {
        if (!plugin.getDataManager().hasEffect(event.getPlayer(), this) || plugin.getRegionBlocker().isEffectBlocked(event.getPlayer(), this) || plugin.getRegionBlocker().isEffectBlocked(event.getLastAttacker(), this)) return; new FoodAndExpLock(plugin, event.getLastAttacker(), plugin.getMainConfig().apophisLockDurationSeconds());
    }
    @EventHandler public void apophisExpMultiplier(PlayerPickupExperienceEvent event) {
        Player player = event.getPlayer(); if (!plugin.getDataManager().hasEffect(player, this) || plugin.getRegionBlocker().isEffectBlocked(player, this)) return; ExperienceOrb orb = event.getExperienceOrb(); double multiplier = CooldownManager.isEffectActive(player.getUniqueId(), getPlainKey()) ? plugin.getMainConfig().apophisXpMultiplierSpark() : plugin.getMainConfig().apophisXpMultiplierStandard(); orb.setExperience((int) Math.round(orb.getExperience() * multiplier));
    }

    @SuppressWarnings({"UnstableApiUsage", "unchecked"})
    @EventHandler public void apophisEnchantBonus(PrepareItemEnchantEvent event) {
        ItemStack item = event.getItem(); if (!item.hasData(DataComponentTypes.ENCHANTABLE) || !item.getEnchantments().isEmpty()) return;
        Player player = event.getEnchanter(); if (!plugin.getDataManager().hasEffect(player, this) || plugin.getRegionBlocker().isEffectBlocked(player, this)) return;
        EnchantmentOffer[] offers = event.getOffers(); Random random = new Random(player.getEnchantmentSeed());
        for (int k = 0; k < 3; k++) {
            Enchantable enchantable = item.getData(DataComponentTypes.ENCHANTABLE); if (enchantable == null) { offers[k] = null; continue; }
            int i = random.nextInt(1, 9) + 7 + random.nextInt(0, 16); int cost = k == 0 ? Math.max(i / 3, 1) : k == 1 ? i * 2 / 3 + 1 : Math.max(i, 30); if (cost < k + 1) { offers[k] = null; continue; }
            try {
                EnchantmentMenu menu = (EnchantmentMenu) ((CraftEnchantmentView) event.getView()).getHandle();
                Method method = menu.getClass().getDeclaredMethod("getEnchantmentList", RegistryAccess.class, net.minecraft.world.item.ItemStack.class, int.class, int.class); method.setAccessible(true);
                List<?> list = (List<?>) method.invoke(menu, ((CraftWorld) player.getWorld()).getHandle().registryAccess(), CraftItemStack.asNMSCopy(item), k, cost);
                if (!list.isEmpty()) {
                    EnchantmentInstance instance = (EnchantmentInstance) list.get(random.nextInt(list.size())); Holder<net.minecraft.world.item.enchantment.Enchantment> enchantment; int level; Class<EnchantmentInstance> clazz = EnchantmentInstance.class;
                    if (!clazz.isRecord()) { enchantment = (Holder<net.minecraft.world.item.enchantment.Enchantment>) clazz.getField("enchantment").get(instance); level = (int) clazz.getField("level").get(instance); }
                    else { RecordComponent[] components = clazz.getRecordComponents(); enchantment = (Holder<net.minecraft.world.item.enchantment.Enchantment>) components[0].getAccessor().invoke(instance); level = (int) components[1].getAccessor().invoke(instance); }
                    offers[k] = new EnchantmentOffer(CraftEnchantment.minecraftHolderToBukkit(enchantment), level, cost);
                }
                method.setAccessible(false);
            } catch (NoSuchMethodException e) { Infuse.LOGGER.error("Could not find the \"getEnchantmentList\" method in the EnchantmentMenu class"); }
            catch (Exception e) { Infuse.LOGGER.error("Error while calculating enchantments:", e); }
        }
    }

    @EventHandler public void stealExp(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player damaged) || !(event.getDamageSource().getCausingEntity() instanceof Player attacker) || !plugin.getDataManager().hasEffect(attacker, this)) return;
        if (plugin.getRegionBlocker().isEffectBlocked(attacker, this) || plugin.getRegionBlocker().isEffectBlocked(damaged, this)) return;
        int exp = damaged.getTotalExperience(), expPerHit = plugin.getMainConfig().apophisExpPerHit(); damaged.setTotalExperience(Math.max(exp - expPerHit, 0)); int toGain = (int) (Math.min(expPerHit, exp) * plugin.getMainConfig().apophisExpPercent()); attacker.setTotalExperience(attacker.getTotalExperience() + toGain); new PlayerExpChangeEvent(attacker, toGain).callEvent();
    }
    @EventHandler public void apophisPreserveConsumables(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer(); if (!plugin.getDataManager().hasEffect(player, this) || plugin.getRegionBlocker().isEffectBlocked(player, this)) return; ItemStack consumedItem = event.getItem(); if (consumedItem.getType() == Material.POTION) return; double chance = CooldownManager.isEffectActive(player.getUniqueId(), getPlainKey()) ? 0.75 : 0.5; if (Math.random() > chance) return; consumedItem.add(1); event.setItem(consumedItem); player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1, 1); player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, player.getLocation(), 3, 1.5, 0.5, 0.5, 0.01);
    }
    @EventHandler public void expShare(PlayerExpChangeEvent event) {
        Player player = event.getPlayer(); if (!CooldownManager.isEffectActive(player.getUniqueId(), getPlainKey()) || plugin.getRegionBlocker().isEffectBlocked(player, this)) return;
        var trustedIds = plugin.getTrustManager().getTrusted(player.getUniqueId()); int toGain = (int) (event.getAmount() * plugin.getMainConfig().apophisPercentExpToShare()); if (toGain == 0 || trustedIds.isEmpty()) return;
        SchedulerUtil.forEachOnlinePlayer(plugin, trustedPlayer -> { if (!trustedIds.contains(trustedPlayer.getUniqueId()) || plugin.getRegionBlocker().isEffectBlocked(trustedPlayer, this)) return; trustedPlayer.setTotalExperience(trustedPlayer.getTotalExperience() + toGain); });
    }
    @EventHandler public void onPlayerHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) return; UUID attackerUUID = attacker.getUniqueId();
        if (event.getEntity() instanceof Player target && CooldownManager.isEffectActive(attackerUUID, "apophis")) target.showTitle(Title.title(Component.text("\uE090"), Component.empty(), Title.Times.times(Duration.ZERO, Duration.ofSeconds(3), Duration.ZERO)));
    }
}
