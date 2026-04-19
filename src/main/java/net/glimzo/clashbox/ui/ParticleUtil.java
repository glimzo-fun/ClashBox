package net.glimzo.clashbox.ui;

import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.entity.Player;

// 1.8-compatible particle helper. All visual effects route through here.
// Minecraft 1.8 uses org.bukkit.Effect + World.playEffect() - the Particle
// enum and World.spawnParticle() were added in 1.9.
public final class ParticleUtil {

    private ParticleUtil() {}

    public static void crit(Location loc, int count) {
        for (int i = 0; i < count; i++) {
            loc.getWorld().playEffect(jitter(loc, 0.5), Effect.CRIT, 0);
        }
    }

    public static void critMagic(Location loc, int count) {
        for (int i = 0; i < count; i++) {
            loc.getWorld().playEffect(jitter(loc, 0.4), Effect.MAGIC_CRIT, 0);
        }
    }

    public static void happyVillager(Location loc, int count) {
        for (int i = 0; i < count; i++) {
            loc.getWorld().playEffect(jitter(loc, 0.6), Effect.HAPPY_VILLAGER, 0);
        }
    }

    public static void explosionLarge(Location loc) {
        loc.getWorld().playEffect(loc, Effect.EXPLOSION_LARGE, 0);
    }

    public static void explosionHuge(Location loc) {
        loc.getWorld().playEffect(loc, Effect.EXPLOSION_HUGE, 0);
    }

    public static void smoke(Location loc, int count) {
        for (int i = 0; i < count; i++) {
            loc.getWorld().playEffect(jitter(loc, 0.3), Effect.SMOKE, 4);
        }
    }

    public static void flame(Location loc, int count) {
        for (int i = 0; i < count; i++) {
            loc.getWorld().playEffect(jitter(loc, 0.4), Effect.MOBSPAWNER_FLAMES, 0);
        }
    }

    public static void portal(Location loc, int count) {
        for (int i = 0; i < count; i++) {
            loc.getWorld().playEffect(jitter(loc, 0.5), Effect.PORTAL, 0);
        }
    }

    public static void blockBreak(Location loc, org.bukkit.Material material) {
        loc.getWorld().playEffect(loc, Effect.STEP_SOUND, material);
    }

    public static void fireworkSpark(Location loc, int count) {
        critMagic(loc, count);
    }

    public static void endSignal(Location loc, int count) {
        for (int i = 0; i < count; i++) {
            loc.getWorld().playEffect(jitter(loc, 0.3), Effect.ENDER_SIGNAL, 0);
        }
    }

    public static void spellInstant(Location loc, int count) {
        for (int i = 0; i < count; i++) {
            loc.getWorld().playEffect(jitter(loc, 0.5), Effect.POTION_BREAK, 0);
        }
    }

    public static void critToPlayer(Player player, Location loc, int count) {
        try {
            for (int i = 0; i < count; i++) {
                player.spigot().playEffect(
                        jitter(loc, 0.4),
                        Effect.CRIT, 0, 0,
                        0f, 0f, 0f, 0.1f, 1, 32
                );
            }
        } catch (Exception ignored) {
            crit(loc, count);
        }
    }

    public static void portalToPlayer(Player player, Location loc, int count) {
        try {
            for (int i = 0; i < count; i++) {
                player.spigot().playEffect(
                        jitter(loc, 0.5),
                        Effect.PORTAL, 0, 0,
                        0f, 0f, 0f, 0.05f, 1, 32
                );
            }
        } catch (Exception ignored) {
            portal(loc, count);
        }
    }

    public static void spinningRing(Location center, double radius,
                                     double yOffset, long tickPhase, int count) {
        for (int i = 0; i < count; i++) {
            double angle = (2 * Math.PI / count) * i + (tickPhase * 0.08);
            double x = center.getX() + radius * Math.cos(angle);
            double z = center.getZ() + radius * Math.sin(angle);
            Location point = new Location(center.getWorld(),
                    x, center.getY() + yOffset, z);
            center.getWorld().playEffect(point, Effect.PORTAL, 0);
        }
    }

    private static Location jitter(Location base, double spread) {
        double x = base.getX() + (Math.random() - 0.5) * spread * 2;
        double y = base.getY() + (Math.random() - 0.5) * spread;
        double z = base.getZ() + (Math.random() - 0.5) * spread * 2;
        return new Location(base.getWorld(), x, y, z);
    }
}
