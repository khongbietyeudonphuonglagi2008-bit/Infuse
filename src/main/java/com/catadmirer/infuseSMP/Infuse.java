package com.catadmirer.infuseSMP;

import com.catadmirer.infuseSMP.commands.*;
import com.catadmirer.infuseSMP.effects.*;
import com.catadmirer.infuseSMP.expansions.ExpansionHelper;
import com.catadmirer.infuseSMP.expansions.InfusePlaceholders;
import com.catadmirer.infuseSMP.extraeffects.*;
import com.catadmirer.infuseSMP.listeners.*;
import com.catadmirer.infuseSMP.managers.*;
import com.catadmirer.infuseSMP.util.regions.BasicRegionBlocker;
import com.catadmirer.infuseSMP.util.regions.DualRegionBlocker;
import com.catadmirer.infuseSMP.util.regions.RegionBlocker;
import com.catadmirer.infuseSMP.util.trust.BetterTeamsTrustManager;
import com.catadmirer.infuseSMP.util.trust.MultiTrustManager;
import com.catadmirer.infuseSMP.util.trust.TrustManager;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public class Infuse extends JavaPlugin {
    public static final Logger LOGGER = LoggerFactory.getLogger("Infuse");
    public static final NamespacedKey JOIN_EFFECT_KEY = new NamespacedKey("infuse", "has_join_effects");
    public static final Function<String, String> PACK_URL = version ->
            "https://files.turbojax.org/packs/infuse/%s/InfusePack-v%s.zip".formatted(version, version);

    private static Infuse instance;

    private volatile String latestVersion;
    private final CompletableFuture<String> latestVersionFuture = new CompletableFuture<>();
    private final CompletableFuture<ResourcePackInfo> resourcePackFuture = new CompletableFuture<>();

    private MainConfig mainConfig;
    private DataManager dataManager;
    private EffectManager effectManager;
    private GlobalLoop loop;
    private RecipeManager recipeManager;
    private HitTracker hitTracker;
    private RitualManager ritualManager;
    private TrustManager trustManager;
    private RegionBlocker regionBlocker;

    /** Keep construction side-effect free; Paper does not guarantee API availability in constructors. */
    public Infuse() {
        instance = this;
    }

    @NonNull
    public static Infuse getInstance() {
        return Objects.requireNonNull(instance, "Infuse has not been constructed yet");
    }

    @Override
    public void onLoad() {
        mainConfig = new MainConfig(this);
        mainConfig.load();
        mainConfig.applyUpdates();

        dataManager = new DataManager(this);
        dataManager.load();
        dataManager.applyUpdates();

        effectManager = new EffectManager(this);
        loop = new GlobalLoop(this);
        recipeManager = new RecipeManager();
        hitTracker = new HitTracker(this);
        ritualManager = new RitualManager();

        if (ExpansionHelper.canUseBetterTeams() && mainConfig.enableBetterTeams()) {
            trustManager = new MultiTrustManager(new BetterTeamsTrustManager(), dataManager);
        } else {
            trustManager = dataManager;
        }

        if (ExpansionHelper.canUseWorldGuard()) {
            regionBlocker = new DualRegionBlocker();
            LOGGER.info("WorldGuard found! Enabling region-based effect management.");
        } else {
            regionBlocker = new BasicRegionBlocker();
            LOGGER.info("WorldGuard is not installed! Using blacklisted-world configs.");
        }

        regionBlocker.init();
    }

    @Override
    public void onEnable() {
        new MessageTranslator().loadAll();

        // Effects must exist before recipes, player loops, placeholders, or persisted effect lookups use them.
        registerEffects();
        registerCommands();
        registerEvents();
        getRecipeManager().reload();
        getGlobalLoop().start();

        if (ExpansionHelper.canUsePlaceholderAPI()) {
            new InfusePlaceholders(this).register();
            LOGGER.info("Placeholders Enabled!");
        } else {
            LOGGER.warn("PlaceholderAPI is not installed, so custom placeholders won't work.");
        }

        startResourcePackDiscovery();
        startUpdateCheck();
        LOGGER.info("Infuse Plugin has been enabled!");
    }

    @Override
    public void onDisable() {
        LOGGER.info("Infuse Plugin is disabling...");

        if (loop != null) loop.stop();
        if (ritualManager != null) ritualManager.stopRitual();
        if (dataManager != null) dataManager.flush();

        // Cancel non-entity tasks explicitly. Entity tasks are either tracked by their manager or
        // retired automatically by Paper/Folia when the plugin/entity retires.
        Bukkit.getGlobalRegionScheduler().cancelTasks(this);
        Bukkit.getAsyncScheduler().cancelTasks(this);

        LOGGER.info("Infuse Plugin has been disabled!");
    }

    /** Registers commands through Paper's lifecycle command registrar. */
    private void registerCommands() {
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, e -> {
            e.registrar().register(SparkCommand.build(this, true));
            e.registrar().register(SparkCommand.build(this, false));
            e.registrar().register(TrustCommand.build(getTrustManager(), true));
            e.registrar().register(TrustCommand.build(getTrustManager(), false));
            e.registrar().register(SwapCommand.build(this));
            e.registrar().register(InfuseCommand.build(this));
            e.registrar().register(DrainCommand.build(this, true));
            e.registrar().register(DrainCommand.build(this, false));
            e.registrar().register(DrawCommand.build());
        });
    }

    private void registerEvents() {
        Bukkit.getPluginManager().registerEvents(getGlobalLoop(), this);
        Bukkit.getPluginManager().registerEvents(getHitTracker(), this);
        Bukkit.getPluginManager().registerEvents(new PlayerSwapHandItemsListener(), this);
        Bukkit.getPluginManager().registerEvents(new CrafterCraftListener(), this);
        Bukkit.getPluginManager().registerEvents(new EntityDeathListener(), this);
        Bukkit.getPluginManager().registerEvents(new EntityDropItemListener(), this);
        Bukkit.getPluginManager().registerEvents(new EntityPickupItemListener(), this);
        Bukkit.getPluginManager().registerEvents(new EffectCraftManager(), this);
        Bukkit.getPluginManager().registerEvents(new InventoryClickListener(), this);
        Bukkit.getPluginManager().registerEvents(new ItemDespawnListener(), this);
        Bukkit.getPluginManager().registerEvents(new PlayerDeathListener(), this);
        Bukkit.getPluginManager().registerEvents(new PlayerItemConsumeListener(), this);
        Bukkit.getPluginManager().registerEvents(new PlayerJoinListener(), this);
        Bukkit.getPluginManager().registerEvents(new PlayerQuitListener(), this);
    }

    private void registerEffects() {
        InfuseEffect.register(new Emerald());
        InfuseEffect.register(new Ender());
        InfuseEffect.register(new Feather());
        InfuseEffect.register(new Fire());
        InfuseEffect.register(new Frost());
        InfuseEffect.register(new Haste());
        InfuseEffect.register(new Heart());
        InfuseEffect.register(new Invis());
        InfuseEffect.register(new Ocean());
        InfuseEffect.register(new Regen());
        InfuseEffect.register(new Speed());
        InfuseEffect.register(new Strength());
        InfuseEffect.register(new Thunder());

        if (getMainConfig().enableApophis()) InfuseEffect.register(new Apophis());
        if (getMainConfig().enableThief()) InfuseEffect.register(new Thief());
    }

    private void startResourcePackDiscovery() {
        Bukkit.getAsyncScheduler().runNow(this, ignored -> {
            try {
                resourcePackFuture.complete(packFromVersion("2.3"));
            } catch (RuntimeException err) {
                resourcePackFuture.completeExceptionally(err);
                LOGGER.error("Could not discover the Infuse resource-pack hash. Players will not be forced to download it this session.", err);
            }
        });
    }

    /** Sends the pack once its hash has been discovered, without blocking login or a region thread. */
    public void sendResourcePackWhenReady(Player player) {
        resourcePackFuture.whenComplete((pack, error) -> {
            if (error != null || pack == null || !isEnabled()) return;

            player.getScheduler().run(this, ignored -> {
                if (!player.isOnline()) return;
                ResourcePackRequest request = ResourcePackRequest.resourcePackRequest()
                        .packs(pack)
                        .required(true)
                        .build();
                player.sendResourcePacks(request);
            }, null);
        });
    }

    private static ResourcePackInfo packFromVersion(String version) {
        String url = PACK_URL.apply(version);
        URI uri = URI.create(url);

        Random rand = new Random(url.hashCode());
        UUID id = new UUID(rand.nextLong(), rand.nextLong());

        String sha1;
        try (InputStream remote = uri.toURL().openStream()) {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] buf = new byte[8192];
            int bytesRead;
            while ((bytesRead = remote.read(buf)) != -1) digest.update(buf, 0, bytesRead);
            sha1 = HexFormat.of().formatHex(digest.digest());
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new IllegalStateException("Could not load/hash resource pack " + uri, e);
        }

        return ResourcePackInfo.resourcePackInfo(id, uri, sha1);
    }

    private void startUpdateCheck() {
        Bukkit.getAsyncScheduler().runNow(this, ignored -> {
            String fetched = fetchLatestVersion();
            latestVersion = fetched == null ? getVersion() : fetched;
            latestVersionFuture.complete(latestVersion);
        });

        latestVersionFuture.thenAccept(latest -> {
            if (!isEnabled() || latest == null || getVersion().equals(latest)) return;
            Bukkit.getGlobalRegionScheduler().run(this, ignored -> {
                Message msg = updateMessage(latest);
                Bukkit.getConsoleSender().sendMessage(msg.toComponent());
            });
        });
    }

    /** Non-blocking update notification for a joining player. */
    public void notifyUpdateIfAvailable(Player player) {
        if (!player.hasPermission("infuse.update_notify")) return;

        latestVersionFuture.thenAccept(latest -> {
            if (!isEnabled() || latest == null || getVersion().equals(latest)) return;
            player.getScheduler().run(this, ignored -> {
                if (player.isOnline()) player.sendMessage(updateMessage(latest).toComponent());
            }, null);
        });
    }

    private Message updateMessage(String latest) {
        Message msg = new Message(Message.MessageType.UPDATE_AVAILABLE);
        msg.applyPlaceholder("current_version", getVersion());
        msg.applyPlaceholder("latest_version", latest);
        return msg;
    }

    private String fetchLatestVersion() {
        HttpRequest request = HttpRequest.newBuilder()
                .GET()
                .timeout(Duration.ofSeconds(10))
                .header("User-Agent", "Infuse/" + getVersion())
                .uri(URI.create("https://api.modrinth.com/v2/project/infusesmp/version"))
                .build();

        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
            HttpResponse<String> response = client.send(request, BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                LOGGER.warn("Received error code {} from api.modrinth.com", response.statusCode());
                return null;
            }

            JsonArray versions = new Gson().fromJson(response.body(), JsonArray.class);
            if (versions == null || versions.isEmpty()) {
                LOGGER.warn("No versions published to Modrinth; using the current version.");
                return getVersion();
            }

            JsonObject latest = versions.get(0).getAsJsonObject();
            return latest.get("version_number").getAsString();
        } catch (JsonSyntaxException err) {
            LOGGER.error("Could not parse the JSON returned by Modrinth.", err);
        } catch (InterruptedException err) {
            Thread.currentThread().interrupt();
            LOGGER.warn("Version request was interrupted.", err);
        } catch (IOException err) {
            LOGGER.warn("Could not check Modrinth for updates.", err);
        }

        return null;
    }

    public String getVersion() {
        return getPluginMeta().getVersion();
    }

    /** Returns the cached result only; this method never performs network I/O. */
    public String getLatestVersion() {
        return latestVersion == null ? getVersion() : latestVersion;
    }

    public MainConfig getMainConfig() {
        return Objects.requireNonNull(mainConfig, "MainConfig has not been initialized yet");
    }

    public RecipeManager getRecipeManager() {
        return Objects.requireNonNull(recipeManager, "RecipeManager has not been initialized yet");
    }

    @NonNull
    public DataManager getDataManager() {
        return Objects.requireNonNull(dataManager, "DataManager has not been initialized yet");
    }

    public EffectManager getEffectManager() {
        return Objects.requireNonNull(effectManager, "EffectManager has not been initialized yet");
    }

    public HitTracker getHitTracker() {
        return Objects.requireNonNull(hitTracker, "HitTracker has not been initialized yet");
    }

    public RitualManager getRitualManager() {
        return Objects.requireNonNull(ritualManager, "RitualManager has not been initialized yet");
    }

    public TrustManager getTrustManager() {
        return Objects.requireNonNull(trustManager, "TrustManager has not been initialized yet");
    }

    public RegionBlocker getRegionBlocker() {
        return Objects.requireNonNull(regionBlocker, "RegionBlocker has not been initialized yet");
    }

    private GlobalLoop getGlobalLoop() {
        return Objects.requireNonNull(loop, "GlobalLoop has not been initialized yet");
    }
}
