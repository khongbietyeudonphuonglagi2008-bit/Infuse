package com.catadmirer.infuseSMP.managers;

import com.catadmirer.infuseSMP.Infuse;
import com.catadmirer.infuseSMP.effects.InfuseEffect;
import com.catadmirer.infuseSMP.util.trust.TrustManager;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kyori.adventure.key.Key;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NullMarked;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Thread-safe runtime player data cache with debounced persistence.
 *
 * <p>Folia/Canvas can execute player events concurrently on different region threads.  A shared
 * {@link YamlConfiguration} is not a suitable live data store in that environment, and synchronously
 * saving it on every click/hit/equip stalls the region thread.  Runtime reads therefore use immutable
 * player snapshots stored in concurrent maps; disk writes are coalesced and performed on the async
 * scheduler.  Plugin shutdown still performs one synchronous flush so no acknowledged change is lost.</p>
 */
@NullMarked
public class DataManager implements TrustManager {
    private static final long SAVE_DEBOUNCE_MILLIS = 750L;

    private final Infuse plugin;
    private final File dataFile;

    private final Map<UUID, PlayerData> players = new ConcurrentHashMap<>();
    private final Map<String, Integer> existingCounts = new ConcurrentHashMap<>();

    private final AtomicBoolean dirty = new AtomicBoolean(false);
    private final AtomicLong generation = new AtomicLong();
    private final AtomicLong persistedGeneration = new AtomicLong(-1L);
    private final AtomicReference<ScheduledTask> pendingSave = new AtomicReference<>();
    private final ReentrantLock fileWriteLock = new ReentrantLock();

    public DataManager(Infuse plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "data/playerdata.yml");
    }

    /** Reloads all persisted data into the thread-safe runtime cache. */
    public boolean load() {
        if (!createFile(false)) return false;

        YamlConfiguration loaded = new YamlConfiguration();
        try {
            loaded.load(dataFile);
        } catch (InvalidConfigurationException err) {
            Infuse.LOGGER.warn("{} contains an invalid YAML configuration. Verify the contents of the file.", dataFile.getName());
            return false;
        } catch (IOException err) {
            Infuse.LOGGER.error("Could not read {}.", dataFile.getName(), err);
            return false;
        }

        players.clear();
        existingCounts.clear();

        boolean migratedLegacyCounts = !loaded.contains("existing-effects") && loaded.contains("effects-crafted");
        ConfigurationSection existing = loaded.getConfigurationSection("existing-effects");
        if (existing == null) existing = loaded.getConfigurationSection("effects-crafted");
        if (existing != null) {
            for (String key : existing.getKeys(false)) {
                existingCounts.put(key, Math.max(0, existing.getInt(key, 0)));
            }
        }

        for (String rootKey : loaded.getKeys(false)) {
            if (rootKey.equals("existing-effects") || rootKey.equals("effects-crafted")) continue;

            UUID uuid;
            try {
                uuid = UUID.fromString(rootKey);
            } catch (IllegalArgumentException ignored) {
                continue;
            }

            String left = loaded.getString(rootKey + ".1");
            String right = loaded.getString(rootKey + ".2");
            String controls = loaded.getString(rootKey + ".controls", "offhand");

            Set<UUID> trusted = new HashSet<>();
            for (String trustedId : loaded.getStringList(rootKey + ".trust")) {
                try {
                    trusted.add(UUID.fromString(trustedId));
                } catch (IllegalArgumentException ignored) {
                    Infuse.LOGGER.warn("Ignoring invalid trusted-player UUID '{}' for {}", trustedId, uuid);
                }
            }

            players.put(uuid, new PlayerData(left, right, Set.copyOf(trusted), controls));
        }

        long loadedGeneration = generation.incrementAndGet();
        if (migratedLegacyCounts) {
            dirty.set(true);
            persistedGeneration.set(loadedGeneration - 1L);
        } else {
            dirty.set(false);
            persistedGeneration.set(loadedGeneration);
        }
        Infuse.LOGGER.info("Successfully loaded {} player records from {}", players.size(), dataFile.getName());
        return true;
    }

    public boolean save() {
        ScheduledTask scheduled = pendingSave.getAndSet(null);
        if (scheduled != null) scheduled.cancel();

        long snapshotGeneration = generation.get();
        boolean success = writeSnapshot(snapshotGeneration);
        if (success && generation.get() == snapshotGeneration) dirty.set(false);
        return success;
    }

    public boolean flush() {
        return save();
    }

    public boolean createFile(boolean replace) {
        if (dataFile.exists() && replace && !dataFile.delete()) {
            Infuse.LOGGER.warn("Could not replace {}", dataFile.getName());
            return false;
        } else if (dataFile.exists()) {
            return true;
        }

        try {
            File parent = dataFile.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                Infuse.LOGGER.error("Could not create data directory {}", parent);
                return false;
            }
            return dataFile.createNewFile() || dataFile.exists();
        } catch (IOException e) {
            Infuse.LOGGER.error("Could not create {}. Make sure the user has write permissions.", dataFile.getName(), e);
            return false;
        }
    }

    public int getExistingCount(InfuseEffect effect) {
        return existingCounts.getOrDefault(effect.key().asString(), 0);
    }

    public void setExistingCount(InfuseEffect effect, int crafted) {
        existingCounts.put(effect.key().asString(), Math.max(0, crafted));
        markDirty();
    }

    @Override
    public Set<UUID> getTrusted(UUID player) {
        return players.getOrDefault(player, PlayerData.EMPTY).trusted();
    }

    @Override
    public void setTrusted(UUID player, Set<UUID> trusted) {
        Set<UUID> immutable = Collections.unmodifiableSet(new HashSet<>(trusted));
        players.compute(player, (uuid, current) -> (current == null ? PlayerData.EMPTY : current).withTrusted(immutable));
        markDirty();
    }

    @Override
    public void addTrust(UUID player, UUID trusted) {
        players.compute(player, (uuid, current) -> {
            PlayerData data = current == null ? PlayerData.EMPTY : current;
            Set<UUID> updated = new HashSet<>(data.trusted());
            updated.add(trusted);
            return data.withTrusted(Set.copyOf(updated));
        });
        markDirty();
    }

    @Override
    public void removeTrust(UUID player, UUID trusted) {
        players.compute(player, (uuid, current) -> {
            PlayerData data = current == null ? PlayerData.EMPTY : current;
            Set<UUID> updated = new HashSet<>(data.trusted());
            updated.remove(trusted);
            return data.withTrusted(Set.copyOf(updated));
        });
        markDirty();
    }

    public void setEffect(UUID owner, String slot, @Nullable InfuseEffect effect) {
        String serialized = effect == null ? null : effect.key().asString();
        players.compute(owner, (uuid, current) -> (current == null ? PlayerData.EMPTY : current).withEffect(slot, serialized));
        markDirty();
    }

    @Nullable
    public InfuseEffect getEffect(UUID playerUUID, String slot) {
        PlayerData data = players.get(playerUUID);
        if (data == null) return null;

        String effectKey = switch (slot) {
            case "1" -> data.leftEffect();
            case "2" -> data.rightEffect();
            default -> null;
        };
        if (effectKey == null || effectKey.isBlank()) return null;

        Key key;
        try {
            key = effectKey.contains(":") ? Key.key(effectKey) : Key.key("infuse", effectKey);
        } catch (IllegalArgumentException err) {
            Infuse.LOGGER.warn("Invalid equipped effect key '{}' for {}", effectKey, playerUUID);
            return null;
        }

        InfuseEffect effect = InfuseEffect.getEffect(key);
        if (effect == null) Infuse.LOGGER.debug("No registered ability found for equipped effect {}", key);
        return effect;
    }

    public boolean hasEffect(OfflinePlayer player, InfuseEffect effect) {
        return hasEffect(player, effect, false);
    }

    public boolean hasEffect(OfflinePlayer player, InfuseEffect effect, boolean differentiateAugmented) {
        return hasEffect(player, effect, differentiateAugmented, "1") || hasEffect(player, effect, differentiateAugmented, "2");
    }

    public boolean hasEffect(OfflinePlayer player, InfuseEffect effect, String slot) {
        return hasEffect(player, effect, false, slot);
    }

    public boolean hasEffect(OfflinePlayer player, InfuseEffect effect, boolean differentiateAugmented, String slot) {
        InfuseEffect equippedEffect = getEffect(player.getUniqueId(), slot);
        if (equippedEffect == null) return false;
        return differentiateAugmented ? effect.equals(equippedEffect) : effect.getId() == equippedEffect.getId();
    }

    public void removeEffect(UUID playerUUID, String slot) {
        setEffect(playerUUID, slot, null);
    }

    public void setControlMode(UUID playerUUID, String defaultMode) {
        players.compute(playerUUID, (uuid, current) -> (current == null ? PlayerData.EMPTY : current).withControls(defaultMode));
        markDirty();
    }

    public String getControlMode(UUID playerUUID) {
        return players.getOrDefault(playerUUID, PlayerData.EMPTY).controls();
    }

    public void applyUpdates() {
        save();
    }

    private void markDirty() {
        dirty.set(true);
        generation.incrementAndGet();

        if (!plugin.isEnabled()) return;

        ScheduledTask scheduled = Bukkit.getAsyncScheduler().runDelayed(
                plugin,
                task -> {
                    pendingSave.compareAndSet(task, null);
                    if (!dirty.get()) return;

                    long snapshotGeneration = generation.get();
                    if (writeSnapshot(snapshotGeneration) && generation.get() == snapshotGeneration) {
                        dirty.set(false);
                    }
                },
                SAVE_DEBOUNCE_MILLIS,
                TimeUnit.MILLISECONDS
        );

        ScheduledTask previous = pendingSave.getAndSet(scheduled);
        if (previous != null) previous.cancel();
    }

    private boolean writeSnapshot(long snapshotGeneration) {
        fileWriteLock.lock();
        try {
            if (snapshotGeneration <= persistedGeneration.get()) return true;
            if (!createFile(false)) return false;

            Map<UUID, PlayerData> playerSnapshot = Map.copyOf(players);
            Map<String, Integer> countSnapshot = Map.copyOf(existingCounts);

            YamlConfiguration output = new YamlConfiguration();
            for (Map.Entry<String, Integer> entry : countSnapshot.entrySet()) {
                output.set("existing-effects." + entry.getKey(), entry.getValue());
            }

            for (Map.Entry<UUID, PlayerData> entry : playerSnapshot.entrySet()) {
                String root = entry.getKey().toString();
                PlayerData data = entry.getValue();

                if (data.leftEffect() != null) output.set(root + ".1", data.leftEffect());
                if (data.rightEffect() != null) output.set(root + ".2", data.rightEffect());
                if (!data.trusted().isEmpty()) {
                    output.set(root + ".trust", data.trusted().stream().map(UUID::toString).sorted().toList());
                }
                if (!"offhand".equals(data.controls())) output.set(root + ".controls", data.controls());
            }

            output.save(dataFile);
            persistedGeneration.accumulateAndGet(snapshotGeneration, Math::max);
            return true;
        } catch (IOException err) {
            Infuse.LOGGER.error("Could not save {}", dataFile.getName(), err);
            return false;
        } finally {
            fileWriteLock.unlock();
        }
    }

    private record PlayerData(@Nullable String leftEffect, @Nullable String rightEffect, Set<UUID> trusted, String controls) {
        private static final PlayerData EMPTY = new PlayerData(null, null, Set.of(), "offhand");

        private PlayerData withTrusted(Set<UUID> value) {
            return new PlayerData(leftEffect, rightEffect, value, controls);
        }

        private PlayerData withControls(String value) {
            return new PlayerData(leftEffect, rightEffect, trusted, value == null || value.isBlank() ? "offhand" : value);
        }

        private PlayerData withEffect(String slot, @Nullable String effect) {
            return switch (slot) {
                case "1" -> new PlayerData(effect, rightEffect, trusted, controls);
                case "2" -> new PlayerData(leftEffect, effect, trusted, controls);
                default -> throw new IllegalArgumentException("Effect slot must be '1' or '2'");
            };
        }
    }
}
