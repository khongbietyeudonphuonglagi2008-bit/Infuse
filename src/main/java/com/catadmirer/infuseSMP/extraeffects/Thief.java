package com.catadmirer.infuseSMP.extraeffects;

import com.catadmirer.infuseSMP.EffectConstants;
import com.catadmirer.infuseSMP.effects.InfuseEffect;
import com.catadmirer.infuseSMP.Infuse;
import com.catadmirer.infuseSMP.Message;
import com.catadmirer.infuseSMP.Message.MessageType;
import com.catadmirer.infuseSMP.managers.CooldownManager;
import com.catadmirer.infuseSMP.util.SchedulerUtil;
import com.destroystokyo.paper.profile.PlayerProfile;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.profile.PlayerTextures;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class Thief extends InfuseEffect {
    private static final Map<UUID, DisguiseData> disguisedPlayers = new ConcurrentHashMap<>();
    public Thief() { this(false); }
    public Thief(boolean augmented) { super("thief", EffectConstants.Id.THIEF, augmented, EffectConstants.PotionColor.THIEF, EffectConstants.RitualColor.THIEF, EffectConstants.BackgroundColor.THIEF); }

    @Override public void equip(Player owner) {
        if (plugin.getRegionBlocker().isEffectBlocked(owner, this)) return;
        SchedulerUtil.forEachOnlinePlayer(plugin, viewer -> viewer.unlistPlayer(owner));
    }
    @Override public void unequip(Player owner) {
        if (disguisedPlayers.containsKey(owner.getUniqueId())) removeDisguise(owner);
        SchedulerUtil.forEachOnlinePlayer(plugin, viewer -> viewer.listPlayer(owner));
    }
    @Override public void activateSpark(Player owner, String slot) {
        if (!plugin.getRegionBlocker().canUseSpark(owner)) return;
        UUID playerUUID = owner.getUniqueId();
        if (CooldownManager.getCooldowns(playerUUID).stream().anyMatch(k -> k.startsWith(plainKey + "_") && k.endsWith("_" + slot))) return;
        owner.playSound(owner.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1, 1);
        long cooldown = plugin.getMainConfig().cooldown(this), duration = plugin.getMainConfig().duration(this);
        CooldownManager.setTimes(playerUUID, plainKey + "_" + slot, duration, cooldown);
    }
    @Override public InfuseEffect getRegularVersion() { return new Thief(); }
    @Override public InfuseEffect getAugmentedVersion() { return new Thief(true); }
    @Override public Message getName() { return new Message(augmented ? MessageType.AUG_THIEF_NAME : MessageType.THIEF_NAME); }
    @Override public Message getLore() { return new Message(augmented ? MessageType.AUG_THIEF_LORE : MessageType.THIEF_LORE); }

    private void activateEffect(Player player, @NotNull InfuseEffect effect, Entity victim, String slot) {
        Message msg = new Message(MessageType.THIEF_STEAL); msg.applyPlaceholder("victim", victim.getName()); msg.applyPlaceholder("effect_name", effect.getName()); player.sendMessage(msg.toComponent());
        effect.activateSpark(player, slot);
        UUID playerUUID = player.getUniqueId();
        CooldownManager.clearSpecificCooldown(playerUUID, effect.getPlainKey() + "_" + slot);
        CooldownManager.clearSpecificDuration(playerUUID, effect.getPlainKey() + "_" + slot);
        long cooldown = plugin.getMainConfig().cooldown(effect), duration = plugin.getMainConfig().duration(effect);
        CooldownManager.setTimes(playerUUID, plainKey + "_" + effect.key() + "_" + slot, duration, cooldown * 2);
    }

    @Override public char getIcon(Player user, String slot) {
        UUID uuid = user.getUniqueId();
        Optional<String> cooldownKey = CooldownManager.getCooldowns(uuid).stream().filter(k -> k.startsWith(plainKey + "_") && k.endsWith("_" + slot)).findFirst();
        Optional<String> durationKey = CooldownManager.getDurations(uuid).stream().filter(k -> k.startsWith(plainKey + "_") && k.endsWith("_" + slot)).findFirst();
        String activeKey = null; boolean active = false; long magnitude = -1;
        if (durationKey.isPresent() && CooldownManager.isEffectActive(uuid, durationKey.get())) { active = true; magnitude = CooldownManager.getEffectTimeLeft(uuid, durationKey.get()) / 1000; activeKey = durationKey.get(); }
        else if (cooldownKey.isPresent() && CooldownManager.isOnCooldown(uuid, cooldownKey.get())) { magnitude = CooldownManager.getCooldownTimeLeft(uuid, cooldownKey.get()) / 1000; activeKey = cooldownKey.get(); }
        if (activeKey == null || activeKey.split("_").length == 2) return super.getIcon(user, slot);
        int lastUnderscore = activeKey.lastIndexOf("_"); if (lastUnderscore > 6) activeKey = activeKey.substring(6, lastUnderscore);
        InfuseEffect stolen = InfuseEffect.getEffect(Key.key("infuse", activeKey));
        if (stolen == null) { Infuse.LOGGER.error("{} stole an invalid effect '{}'!", user.getName(), activeKey); return super.getIcon(user, slot); }
        if (active) return stolen.icon(true, (float) magnitude / plugin.getMainConfig().duration(stolen));
        return icon(false, (float) magnitude / plugin.getMainConfig().cooldown(stolen) * 2);
    }

    private void disguise(Player thiefUser, Player player) {
        disguisedPlayers.put(thiefUser.getUniqueId(), new DisguiseData(thiefUser.customName(), thiefUser.displayName(), thiefUser.isCustomNameVisible(), thiefUser.getPlayerProfile().getTextures()));
        thiefUser.customName(player.customName()); thiefUser.displayName(player.displayName()); thiefUser.setCustomNameVisible(player.isCustomNameVisible());
        PlayerProfile profile = thiefUser.getPlayerProfile(); profile.setTextures(player.getPlayerProfile().getTextures()); thiefUser.setPlayerProfile(profile);
        long disguiseEndTime = System.currentTimeMillis() + 3_600_000L;
        BossBar bossBar = Bukkit.createBossBar("Disguise", BarColor.PINK, BarStyle.SOLID); bossBar.setProgress(1); bossBar.addPlayer(thiefUser);
        SchedulerUtil.runAtFixedRate(thiefUser, plugin, task -> {
            long timeLeft = disguiseEndTime - System.currentTimeMillis();
            if (timeLeft <= 0) { removeDisguise(thiefUser); bossBar.removePlayer(thiefUser); task.cancel(); return; }
            bossBar.setProgress(Math.max(0.0, Math.min(1.0, timeLeft / 3_600_000.0)));
        }, () -> {}, 1L, 20L);
    }

    private void removeDisguise(Player player) {
        DisguiseData originalData = disguisedPlayers.remove(player.getUniqueId());
        if (originalData == null) return;
        player.customName(originalData.customName); player.displayName(originalData.displayName); player.setCustomNameVisible(originalData.customNameVisible);
        PlayerProfile profile = player.getPlayerProfile(); profile.setTextures(originalData.skin); player.setPlayerProfile(profile);
    }
    private record DisguiseData(Component customName, Component displayName, boolean customNameVisible, PlayerTextures skin) {}

    @EventHandler public void hideThievesOnJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (plugin.getDataManager().hasEffect(player, this)) SchedulerUtil.forEachOnlinePlayer(plugin, viewer -> viewer.unlistPlayer(player));
        SchedulerUtil.runGlobal(plugin, ignored -> {
            for (Player otherPlayer : Bukkit.getOnlinePlayers()) {
                if (!plugin.getDataManager().hasEffect(otherPlayer, this)) continue;
                player.getScheduler().run(plugin, task -> player.unlistPlayer(otherPlayer), null);
            }
        });
    }
    @EventHandler public void loseDisguise(PlayerDeathEvent event) { removeDisguise(event.getEntity()); }
    @EventHandler public void removeDisguiseOnQuit(PlayerQuitEvent event) { removeDisguise(event.getPlayer()); }
    @EventHandler public void getDisguise(PlayerDeathEvent event) {
        Player deadPlayer = event.getPlayer(), killer = deadPlayer.getKiller();
        if (killer == null || !plugin.getDataManager().hasEffect(killer, this) || plugin.getRegionBlocker().isEffectBlocked(killer, this) || plugin.getRegionBlocker().isEffectBlocked(deadPlayer, this)) return;
        disguise(killer, deadPlayer);
    }

    @EventHandler public void onPlayerHit(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim) || !(event.getDamager() instanceof Player attacker) || !plugin.getDataManager().hasEffect(attacker, this) || !plugin.getRegionBlocker().canBeTargetedBySpark(victim)) return;
        UUID playerUUID = attacker.getUniqueId(); Set<String> durations = CooldownManager.getDurations(playerUUID);
        Optional<String> thisDuration = durations.stream().filter(k -> k.startsWith(plainKey + "_")).filter(k -> k.lastIndexOf("_") == 5).findFirst();
        if (thisDuration.isEmpty()) return;
        String durationKey = thisDuration.get();
        if (!CooldownManager.isEffectActive(playerUUID, durationKey) || plugin.getRegionBlocker().isEffectBlocked(attacker, this)) return;
        InfuseEffect leftEffect = plugin.getDataManager().getEffect(victim.getUniqueId(), "1"), rightEffect = plugin.getDataManager().getEffect(victim.getUniqueId(), "2");
        if (leftEffect != null && rightEffect != null) { if (Math.random() > 0.5) activateEffect(attacker, leftEffect, victim, "1"); else activateEffect(attacker, rightEffect, victim, "2"); }
        else if (leftEffect != null) activateEffect(attacker, leftEffect, victim, "1");
        else if (rightEffect != null) activateEffect(attacker, rightEffect, victim, "2");
        else return;
        CooldownManager.setDuration(playerUUID, durationKey, 0);
    }
}
