package com.catadmirer.infuseSMP.managers;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lock-free timestamp based cooldown/duration registry.
 *
 * <p>Expired entries are pruned on reads, so long-running servers do not accumulate stale ability
 * keys and prefix lookups cannot accidentally stop at an expired key while another matching key is
 * still active.</p>
 */
public final class CooldownManager {
    private static final Map<UUID, Map<String, Long>> cooldowns = new ConcurrentHashMap<>();
    private static final Map<UUID, Map<String, Long>> durations = new ConcurrentHashMap<>();

    private CooldownManager() {}

    public static void setTimes(UUID playerUUID, String key, long durationSeconds, long cooldownSeconds) {
        setCooldown(playerUUID, key, Math.max(0L, cooldownSeconds) + Math.max(0L, durationSeconds));
        setDuration(playerUUID, key, durationSeconds);
    }

    public static void setDuration(UUID playerUUID, String key, long seconds) {
        putDeadline(durations, playerUUID, key, seconds);
    }

    public static boolean isEffectActive(UUID playerUUID, String keyPrefix) {
        return hasActiveMatching(durations, playerUUID, keyPrefix);
    }

    public static long getEffectTimeLeft(UUID playerUUID, String key) {
        return timeLeft(durations, playerUUID, key);
    }

    public static Set<String> getDurations(UUID playerUUID) {
        return activeKeys(durations, playerUUID);
    }

    public static void clearSpecificDuration(UUID playerUUID, String key) {
        removeKey(durations, playerUUID, key);
    }

    public static void cleanupExpiredDurations() {
        cleanupAll(durations);
    }

    public static boolean isOnCooldown(UUID playerUUID, String keyPrefix) {
        return hasActiveMatching(cooldowns, playerUUID, keyPrefix);
    }

    public static void setCooldown(UUID playerUUID, String key, long seconds) {
        putDeadline(cooldowns, playerUUID, key, seconds);
    }

    public static long getCooldownTimeLeft(UUID playerUUID, String key) {
        return timeLeft(cooldowns, playerUUID, key);
    }

    public static Set<String> getCooldowns(UUID playerUUID) {
        return activeKeys(cooldowns, playerUUID);
    }

    public static void clearSpecificCooldown(UUID playerUUID, String key) {
        removeKey(cooldowns, playerUUID, key);
    }

    public static void cleanupAllExpiredCooldowns() {
        cleanupAll(cooldowns);
    }

    /** Clears cooldown state while preserving currently active effect durations. */
    public static void removeAllCooldowns(UUID playerUUID) {
        cooldowns.remove(playerUUID);
    }

    /** Clears both cooldown and active-duration state, intended for plugin shutdown/tests. */
    public static void clearAllState(UUID playerUUID) {
        cooldowns.remove(playerUUID);
        durations.remove(playerUUID);
    }

    private static void putDeadline(Map<UUID, Map<String, Long>> store, UUID playerUUID, String key, long seconds) {
        long safeSeconds = Math.max(0L, seconds);
        long now = System.currentTimeMillis();
        long millis;
        try {
            millis = Math.multiplyExact(safeSeconds, 1_000L);
        } catch (ArithmeticException ignored) {
            millis = Long.MAX_VALUE - now;
        }
        long deadline = millis >= Long.MAX_VALUE - now ? Long.MAX_VALUE : now + millis;
        store.computeIfAbsent(playerUUID, ignored -> new ConcurrentHashMap<>()).put(key, deadline);
    }

    private static boolean hasActiveMatching(Map<UUID, Map<String, Long>> store, UUID playerUUID, String prefix) {
        Map<String, Long> entries = store.get(playerUUID);
        if (entries == null) return false;

        long now = System.currentTimeMillis();
        boolean active = false;
        for (Map.Entry<String, Long> entry : entries.entrySet()) {
            if (entry.getValue() <= now) {
                entries.remove(entry.getKey(), entry.getValue());
                continue;
            }
            String name = entry.getKey();
            if (name.equals(prefix) || name.startsWith(prefix + "_")) active = true;
        }
        removeOuterIfEmpty(store, playerUUID, entries);
        return active;
    }

    private static long timeLeft(Map<UUID, Map<String, Long>> store, UUID playerUUID, String key) {
        Map<String, Long> entries = store.get(playerUUID);
        if (entries == null) return 0L;
        Long deadline = entries.get(key);
        if (deadline == null) return 0L;

        long left = deadline - System.currentTimeMillis();
        if (left > 0L) return left;
        entries.remove(key, deadline);
        removeOuterIfEmpty(store, playerUUID, entries);
        return 0L;
    }

    private static Set<String> activeKeys(Map<UUID, Map<String, Long>> store, UUID playerUUID) {
        Map<String, Long> entries = store.get(playerUUID);
        if (entries == null) return Set.of();
        pruneExpired(entries, System.currentTimeMillis());
        removeOuterIfEmpty(store, playerUUID, entries);
        return Set.copyOf(entries.keySet());
    }

    private static void removeKey(Map<UUID, Map<String, Long>> store, UUID playerUUID, String key) {
        Map<String, Long> entries = store.get(playerUUID);
        if (entries == null) return;
        entries.remove(key);
        removeOuterIfEmpty(store, playerUUID, entries);
    }

    private static void cleanupAll(Map<UUID, Map<String, Long>> store) {
        long now = System.currentTimeMillis();
        store.forEach((uuid, entries) -> {
            pruneExpired(entries, now);
            removeOuterIfEmpty(store, uuid, entries);
        });
    }

    private static void pruneExpired(Map<String, Long> entries, long now) {
        entries.entrySet().removeIf(entry -> entry.getValue() <= now);
    }

    private static void removeOuterIfEmpty(Map<UUID, Map<String, Long>> store, UUID playerUUID, Map<String, Long> entries) {
        if (entries.isEmpty()) store.remove(playerUUID, entries);
    }
}
