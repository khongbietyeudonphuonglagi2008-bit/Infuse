package com.catadmirer.infuseSMP.managers;

import com.catadmirer.infuseSMP.EffectConstants;
import com.catadmirer.infuseSMP.Infuse;
import com.catadmirer.infuseSMP.effects.Ender;
import com.catadmirer.infuseSMP.effects.InfuseEffect;
import com.catadmirer.infuseSMP.util.SchedulerUtil;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Particle.DustOptions;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.Nullable;

public final class ParticleManager {
    private static final int MAX_LINE_POINTS = 4_096;
    private ParticleManager() {}

    public static void spawnEffectParticles(Player player, @Nullable InfuseEffect effect) {
        if (effect == null) return;
        if (effect.getId() == EffectConstants.Id.ENDER) {
            player.getWorld().spawnParticle(Particle.REVERSE_PORTAL, player.getLocation().add(0, 1, 0), 32, 0.3, 0.5, 0.3, 0);
            return;
        }
        player.getWorld().spawnParticle(Particle.ENTITY_EFFECT, player.getLocation().add(0, 1, 0), 2, 0.3, 0.5, 0.3, 0.1, Color.fromARGB(effect.getPotionColor().getRGB()));
    }

    public static void spawnCursedParticles(Player player) {
        if (Ender.cursedPlayers.contains(player.getUniqueId())) player.getWorld().spawnParticle(Particle.WITCH, player.getLocation().add(0, 1, 0), 10, 0.3, 0.5, 0.3, 0.01);
    }

    public static void spawnEffectCloud(Player player, Color color) {
        player.getWorld().spawnParticle(Particle.ENTITY_EFFECT, player.getLocation().add(0, 1, 0), 30, 0.5, 0.6, 0.5, 0, color);
    }

    public static void drawLine(Location start, Location end) { drawLine(start, end, 5, new DustOptions(Color.WHITE, 1)); }
    public static void drawLine(Location start, Location end, int count) { drawLine(start, end, count, new DustOptions(Color.WHITE, 1)); }
    public static void drawLine(Location start, Location end, DustOptions dustOptions) { drawLine(start, end, 5, dustOptions); }

    public static void drawLine(Location start, Location end, int count, DustOptions dustOptions) {
        World startWorld = start.getWorld();
        World endWorld = end.getWorld();
        if (startWorld == null || endWorld == null || !startWorld.getUID().equals(endWorld.getUID())) { Infuse.LOGGER.debug("Cannot draw lines between two worlds!"); return; }
        Location cursor = start.clone();
        var diff = end.clone().toVector().subtract(cursor.toVector());
        double length = diff.length();
        if (length < 1.0E-6) { startWorld.spawnParticle(Particle.DUST, cursor, Math.max(1, count), 0, 0, 0, 0, dustOptions); return; }
        int points = Math.max(1, Math.min(MAX_LINE_POINTS, (int) Math.ceil(length * 10.0)));
        var step = diff.multiply(1.0 / points);
        for (int i = 0; i <= points; i++) {
            startWorld.spawnParticle(Particle.DUST, cursor, Math.max(1, count), 0, 0, 0, 0, dustOptions);
            cursor.add(step);
        }
    }

    public static void dropEffect(Infuse plugin, boolean bottomToTop, @NotNull InfuseEffect effect, Location location) {
        final Location base = location.clone().add(0, bottomToTop ? 0 : 2, 0);
        final World world = base.getWorld();
        if (world == null) return;
        Color color = Color.fromARGB(effect.getPotionColor().getRGB());
        final Particle.DustOptions dust = new Particle.DustOptions(color, 0.7F);
        final int points = 16;
        final double radius = 0.6;
        final double[] y = {0.0};
        SchedulerUtil.runRegionAtFixedRate(base, plugin, task -> {
            if (y[0] > 2.0) { task.cancel(); return; }
            double ringY = bottomToTop ? y[0] : 2.0 - y[0];
            for (int i = 0; i < points; ++i) {
                double angle = Math.PI * 2 * i / points;
                world.spawnParticle(Particle.DUST, base.clone().add(Math.cos(angle) * radius, ringY, Math.sin(angle) * radius), 0, 0, 0, 0, 1, dust);
            }
            y[0] += 0.15;
        }, 1L, 1L);
        SchedulerUtil.runRegion(base, plugin, ignored -> world.playSound(base, Sound.ENTITY_TURTLE_EGG_BREAK, 1.3F, 1.2F));
    }
}
