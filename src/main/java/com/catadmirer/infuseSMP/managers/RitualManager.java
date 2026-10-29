package com.catadmirer.infuseSMP.managers;

import com.catadmirer.infuseSMP.Infuse;
import com.catadmirer.infuseSMP.Message;
import com.catadmirer.infuseSMP.effects.Ender;
import com.catadmirer.infuseSMP.effects.InfuseEffect;
import com.catadmirer.infuseSMP.util.SchedulerUtil;
import com.google.gson.JsonObject;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.jspecify.annotations.Nullable;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

public class RitualManager {
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final Infuse plugin;
    private volatile boolean active;
    @Nullable private volatile BossBar bossBar;
    @Nullable private volatile EnderCrystal enderCrystal;
    @Nullable private volatile InfuseEffect effect;
    @Nullable private volatile Location location;
    @Nullable private volatile ImmortalBrewer immortalBrewer;
    @Nullable private volatile ScheduledTask task;

    public RitualManager() { plugin = Infuse.getInstance(); }
    public boolean isActive() { return active; }
    public @Nullable BossBar getBossBar() { return bossBar; }
    public @Nullable InfuseEffect getEffect() { return effect; }
    public @Nullable Location getLocation() { return location == null ? null : location.clone(); }
    public boolean hasBeam() { return active && enderCrystal != null; }
    public boolean isBrewerImmortal() { return active && immortalBrewer != null; }

    public synchronized void startRitual(HumanEntity player, InfuseEffect effect, Location requestedLocation) {
        if (active) { player.sendMessage(new Message(Message.MessageType.ERROR_RITUAL_ACTIVE).toComponent()); return; }
        Location ritualLocation = requestedLocation.clone().toBlockLocation();
        active = true; this.effect = effect; this.location = ritualLocation;
        Component itemName = effect.getName().toComponent();
        Component bossName = Component.text("🧪 ").append(itemName.decorate(TextDecoration.BOLD)).append(Component.text(" 🧪").decoration(TextDecoration.BOLD, false)).color(itemName.color());
        BossBar createdBossBar = BossBar.bossBar(bossName, 1, effect.getRitualColor(), BossBar.Overlay.PROGRESS);
        bossBar = createdBossBar;
        SchedulerUtil.forEachOnlinePlayer(plugin, p -> p.showBossBar(createdBossBar));

        if (plugin.getMainConfig().ritualBeacon()) {
            Location startLoc = ritualLocation.clone().add(0.5, 0, 0.5);
            startLoc.setY(Math.max(ritualLocation.getWorld().getMinHeight(), -64));
            Location targetLoc = ritualLocation.clone().add(0.5, 0, 0.5);
            targetLoc.setY(ritualLocation.getWorld().getMaxHeight() - 1);
            SchedulerUtil.runRegion(startLoc, plugin, ignored -> {
                if (!active) return;
                EnderCrystal crystal = ritualLocation.getWorld().spawn(startLoc, EnderCrystal.class);
                crystal.setShowingBottom(false); crystal.setInvulnerable(true); crystal.setInvisible(true); crystal.setBeamTarget(targetLoc); crystal.setPersistent(false); enderCrystal = crystal;
            });
        }

        String x = String.valueOf(ritualLocation.getBlockX()), y = String.valueOf(ritualLocation.getBlockY()), z = String.valueOf(ritualLocation.getBlockZ());
        String worldName = switch (ritualLocation.getWorld().getEnvironment()) { case NORMAL -> "<green><b>Overworld"; case NETHER -> "<dark_red><b>Nether"; case THE_END -> "<dark_purple><b>End"; default -> "<gray>" + ritualLocation.getWorld().getName(); };
        Message minecraftMessage = new Message(Message.MessageType.EFFECT_BROADCAST);
        minecraftMessage.applyPlaceholder("player", player.getName()); minecraftMessage.applyPlaceholder("item", itemName); minecraftMessage.applyPlaceholder("x", x); minecraftMessage.applyPlaceholder("y", y); minecraftMessage.applyPlaceholder("z", z); minecraftMessage.applyPlaceholder("dimension", worldName);
        SchedulerUtil.forEachOnlinePlayer(plugin, p -> p.sendMessage(minecraftMessage.toComponent()));
        Message discordMessage = new Message(Message.MessageType.DISCORD_BROADCAST);
        discordMessage.applyPlaceholder("player", player.getName()); discordMessage.applyPlaceholder("item", PlainTextComponentSerializer.plainText().serialize(itemName)); discordMessage.applyPlaceholder("x", x); discordMessage.applyPlaceholder("y", y); discordMessage.applyPlaceholder("z", z); discordMessage.applyPlaceholder("dimension", MiniMessage.miniMessage().stripTags(worldName));
        if (plugin.getMainConfig().enableDiscordBroadcasts()) { String webhookUrl = plugin.getMainConfig().discordWebhookUrl(); if (webhookUrl != null && !webhookUrl.isBlank()) sendToDiscord(webhookUrl, discordMessage.toString()); }
        if (plugin.getMainConfig().useImmortalBrewers()) { ImmortalBrewer brewer = new ImmortalBrewer(ritualLocation); immortalBrewer = brewer; SchedulerUtil.runGlobal(plugin, ignored -> Bukkit.getPluginManager().registerEvents(brewer, plugin)); }

        int durationSeconds = effect.equals(new Ender(true)) ? plugin.getMainConfig().ritualDurationEnder() : plugin.getMainConfig().ritualDuration();
        long totalTicks = Math.max(1L, durationSeconds * 20L); long[] elapsed = {0L};
        task = SchedulerUtil.runRegionAtFixedRate(ritualLocation, plugin, scheduled -> {
            if (!active) { scheduled.cancel(); return; }
            if (!isBrewerImmortal() && ritualLocation.getBlock().getType() != Material.BREWING_STAND) {
                Message interrupted = new Message(Message.MessageType.RITUAL_INTERRUPTED);
                SchedulerUtil.forEachOnlinePlayer(plugin, p -> p.sendMessage(interrupted.toComponent()));
                stopRitual(); scheduled.cancel(); return;
            }
            elapsed[0]++; BossBar bar = bossBar; if (bar != null) bar.progress(Math.max(0f, 1f - (float) elapsed[0] / totalTicks));
            if (elapsed[0] >= totalTicks) { completeRitual(); scheduled.cancel(); }
        }, 1L, 1L);
    }

    public synchronized void completeRitual() {
        if (!active || effect == null || location == null) return;
        InfuseEffect completedEffect = effect; Location dropLocation = location.clone().add(0, 1, 0);
        Message msg = new Message(Message.MessageType.EFFECT_FINISHED); msg.applyPlaceholder("item", completedEffect.getName());
        SchedulerUtil.forEachOnlinePlayer(plugin, p -> p.sendMessage(msg.toComponent()));
        dropLocation.getWorld().dropItem(dropLocation, completedEffect.createItem()); stopRitual();
    }

    public synchronized void stopRitual() {
        if (!active) return;
        BossBar oldBossBar = bossBar; EnderCrystal oldCrystal = enderCrystal; ImmortalBrewer oldBrewer = immortalBrewer; ScheduledTask oldTask = task;
        active = false; bossBar = null; effect = null; enderCrystal = null; immortalBrewer = null; location = null; task = null;
        if (oldTask != null) oldTask.cancel();
        if (oldBossBar != null && plugin.isEnabled()) SchedulerUtil.forEachOnlinePlayer(plugin, p -> p.hideBossBar(oldBossBar));
        if (oldCrystal != null && plugin.isEnabled()) SchedulerUtil.run(oldCrystal, plugin, () -> { if (oldCrystal.isValid()) oldCrystal.remove(); });
        if (oldBrewer != null) HandlerList.unregisterAll(oldBrewer);
    }

    private void sendToDiscord(String webhookUrl, String message) {
        JsonObject payload = new JsonObject(); payload.addProperty("content", message);
        HttpRequest request;
        try { request = HttpRequest.newBuilder(URI.create(webhookUrl)).timeout(Duration.ofSeconds(8)).header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(payload.toString())).build(); }
        catch (IllegalArgumentException err) { Infuse.LOGGER.error("Invalid Discord webhook URL configured.", err); return; }
        HTTP.sendAsync(request, HttpResponse.BodyHandlers.discarding()).whenComplete((response, error) -> {
            if (error != null) { Infuse.LOGGER.error("Could not send webhook message to Discord.", error); return; }
            int status = response.statusCode(); if (status < 200 || status >= 300) Infuse.LOGGER.warn("Discord webhook returned HTTP {}", status);
        });
    }

    public static class ImmortalBrewer implements Listener {
        private final Location brewerLocation;
        public ImmortalBrewer(Location brewerLocation) { this.brewerLocation = brewerLocation.clone().toBlockLocation(); }
        @EventHandler public void onBrewingStandBreak(BlockBreakEvent event) { if (event.getBlock().getLocation().equals(brewerLocation)) event.setCancelled(true); }
        @EventHandler public void onBrewingStandExplode(EntityExplodeEvent event) { List<Block> blocks = event.blockList(); blocks.removeIf(block -> block.getLocation().equals(brewerLocation)); }
        @EventHandler(priority = EventPriority.LOW) public void onBrewingStandInteract(PlayerInteractEvent event) { Block clicked = event.getClickedBlock(); if (clicked == null || !clicked.getLocation().equals(brewerLocation)) return; event.setUseInteractedBlock(Event.Result.DENY); }
    }
}
