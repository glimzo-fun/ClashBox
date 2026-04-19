package net.glimzo.clashbox.zone;

import net.glimzo.clashbox.core.ClashBoxConfig;
import net.glimzo.clashbox.core.ClashBoxPlugin;
import net.glimzo.clashbox.player.PlayerStateManager;
import net.glimzo.clashbox.ui.ParticleUtil;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import me.pikashrey.glimzocore.utilities.chat.CC;

public class ZoneManager implements Listener {

    private final ClashBoxPlugin     plugin;
    private final PlayerStateManager stateManager;
    private final ClashBoxConfig     config;

    private static final double MOVE_CHECK_THRESHOLD = 0.01;

    public ZoneManager(ClashBoxPlugin plugin, PlayerStateManager stateManager) {
        this.plugin       = plugin;
        this.stateManager = stateManager;
        this.config       = plugin.getCBConfig();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();

        if (!stateManager.isAliveInArena(player)) return;

        Location from = event.getFrom();
        Location to   = event.getTo();
        if (to == null) return;

        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        double dy = to.getY() - from.getY();
        if ((dx * dx + dz * dz + dy * dy) < MOVE_CHECK_THRESHOLD) return;

        stateManager.updateLastMoveCheck(player);

        ZoneType current  = detectZone(to);
        ZoneType previous = stateManager.getLastZone(player);

        if (current != previous) {
            ZoneTransitionEvent transitionEvent = new ZoneTransitionEvent(player, previous, current);
            plugin.getServer().getPluginManager().callEvent(transitionEvent);

            if (!transitionEvent.isCancelled()) {
                stateManager.updateZone(player, current);
                handleTransition(player, previous, current);
            }
        }
    }

    public ZoneType detectZone(Location loc) {
        double cx = config.getCenterX();
        double cz = config.getCenterZ();

        double dx = loc.getX() - cx;
        double dz = loc.getZ() - cz;
        double distSq = dx * dx + dz * dz;
        int y = loc.getBlockY();

        int coreRadius = config.getZoneRadius("core");
        int midRadius  = config.getZoneRadius("mid");
        int coreYMin   = config.getZoneYMin("core");
        int coreYMax   = config.getZoneYMax("core");
        int midYMin    = config.getZoneYMin("mid");
        int midYMax    = config.getZoneYMax("mid");

        if (distSq <= (double) coreRadius * coreRadius && y >= coreYMin && y <= coreYMax)
            return ZoneType.CORE;
        if (distSq <= (double) midRadius * midRadius && y >= midYMin && y <= midYMax)
            return ZoneType.MID;
        return ZoneType.OUTER;
    }

    private void handleTransition(Player player, ZoneType from, ZoneType to) {
        ZoneConfig zc = plugin.getZoneConfig();
        switch (to) {
            case MID -> {
                plugin.getTitleService().sendTitle(player,
                        zc.getEntryTitle(ZoneType.MID),
                        zc.getEntrySubtitle(ZoneType.MID),
                        5, 30, 10);
                player.playSound(player.getLocation(), Sound.PORTAL_TRIGGER, 0.6f, 1.2f);
            }
            case CORE -> {
                plugin.getTitleService().sendTitle(player,
                        zc.getEntryTitle(ZoneType.CORE),
                        zc.getEntrySubtitle(ZoneType.CORE),
                        5, 40, 10);
                player.playSound(player.getLocation(), Sound.WITHER_DEATH, 0.5f, 1.5f);
                plugin.getServer().getScheduler().runTask(plugin, () ->
                        player.getWorld().playEffect(
                                player.getLocation(), org.bukkit.Effect.MOBSPAWNER_FLAMES, 0));
            }
            case OUTER -> {
                if (from == ZoneType.CORE) {
                    plugin.getTitleService().sendTitle(player,
                            "&a&l✦ EXTRACTED!", "&7You made it out of Core!", 5, 40, 10);
                    player.playSound(player.getLocation(), Sound.LEVEL_UP, 0.8f, 1.2f);
                    plugin.getServer().getScheduler().runTask(plugin, () ->
                            ParticleUtil.happyVillager(player.getLocation(), 15));
                } else if (from == ZoneType.MID) {
                    plugin.getTitleService().sendTitle(player,
                            zc.getEntryTitle(ZoneType.OUTER),
                            zc.getEntrySubtitle(ZoneType.OUTER),
                            5, 20, 10);
                    player.playSound(player.getLocation(), Sound.NOTE_PLING, 0.5f, 1.0f);
                }
            }
        }
    }

    public boolean isPvPZone(ZoneType zone) {
        return plugin.getZoneConfig().isPvpEnabled(zone);
    }
}
