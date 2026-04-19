package net.glimzo.clashbox.portal;

import net.glimzo.clashbox.core.ClashBoxPlugin;
import net.glimzo.clashbox.player.PlayerState;
import net.glimzo.clashbox.player.PlayerStateManager;
import net.glimzo.clashbox.ui.ParticleUtil;
import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import me.pikashrey.glimzocore.utilities.chat.CC;

public class PortalManager implements Listener {

    private final ClashBoxPlugin plugin;
    private final PlayerStateManager stateManager;

    private final List<ActivePortal> activePortals = new ArrayList<>();
    private List<Location> spawnPool = new ArrayList<>();

    private int visualTaskId = -1;
    private int relocTaskId  = -1;

    private static final double TRIGGER_RADIUS_SQ = 1.5 * 1.5;

    private final java.util.Set<java.util.UUID> teleporting =
            java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());

    public PortalManager(ClashBoxPlugin plugin, PlayerStateManager stateManager) {
        this.plugin       = plugin;
        this.stateManager = stateManager;
    }

    public void startTask() {
        spawnPool = new ArrayList<>(plugin.getCBConfig().getPortalSpawnLocations());

        if (spawnPool.isEmpty()) {
            plugin.getLogger().warning("[ClashBox] No portal spawn locations configured!");
            return;
        }

        relocatePortals(false);

        int intervalSeconds = plugin.getCBConfig().getPortalRelocationInterval();
        int intervalTicks   = intervalSeconds * 20;
        relocTaskId = plugin.getServer().getScheduler()
                .scheduleSyncRepeatingTask(plugin, () -> relocatePortals(true),
                        intervalTicks, intervalTicks);

        visualTaskId = plugin.getServer().getScheduler()
                .scheduleSyncRepeatingTask(plugin, this::renderPortals, 5L, 5L);
    }

    public void cleanup() {
        if (visualTaskId != -1) {
            plugin.getServer().getScheduler().cancelTask(visualTaskId);
        }
        if (relocTaskId != -1) {
            plugin.getServer().getScheduler().cancelTask(relocTaskId);
        }
        activePortals.clear();
    }

    private void relocatePortals(boolean announce) {
        activePortals.clear();

        int count = plugin.getCBConfig().getPortalActiveCount();
        List<Location> pool = new ArrayList<>(spawnPool);
        Collections.shuffle(pool, new Random());

        int placed = 0;
        for (Location loc : pool) {
            if (placed >= count) break;
            activePortals.add(new ActivePortal(loc));
            placed++;
        }

        if (announce && plugin.getCBConfig().isPortalAnnounceEnabled()) {
            announcePortals();
        }
    }

    private void announcePortals() {
        if (activePortals.isEmpty()) return;

        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (!stateManager.isInArena(player) &&
                    stateManager.getState(player) != PlayerState.IN_LOBBY) continue;

            player.sendMessage(CC.translate("&8━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
            player.sendMessage(CC.translate("&b&l  ✦ LOBBY PORTALS RELOCATED"));

            for (int i = 0; i < activePortals.size(); i++) {
                Location loc = activePortals.get(i).getLocation();
                String zone  = getZoneLabel(loc);
                player.sendMessage(String.format(
                        "&7  Portal &e#%d &7-> &fX: %d, Y: %d, Z: %d &8[%s&8]",
                        i + 1,
                        loc.getBlockX(),
                        loc.getBlockY(),
                        loc.getBlockZ(),
                        zone
                ));
            }

            player.sendMessage(CC.translate("&7  Step into a portal to return to the lobby."));
            player.sendMessage(CC.translate("&8━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
            player.playSound(player.getLocation(), Sound.NOTE_PLING, 0.7f, 1.4f);
        }
    }

    private void renderPortals() {
        if (activePortals.isEmpty()) return;

        long tick = System.currentTimeMillis() / 50;

        for (ActivePortal portal : activePortals) {
            Location center = portal.getLocation().clone().add(0.5, 0.5, 0.5);

            ParticleUtil.spinningRing(center, 0.7, -0.5, tick, 20);
            ParticleUtil.spinningRing(center, 0.7, 0.8, tick + 10, 20);

            for (double dy = -0.5; dy <= 1.3; dy += 0.3) {
                Location col = center.clone().add(0, dy, 0);
                center.getWorld().playEffect(col, Effect.ENDER_SIGNAL, 0);
            }

            if (tick % 4 == 0) {
                ParticleUtil.spellInstant(center.clone().add(0, 0.5, 0), 5);
            }
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!stateManager.isInArena(player)) return;

        Location to = event.getTo();
        if (to == null) return;

        for (ActivePortal portal : activePortals) {
            Location portalCenter = portal.getLocation().clone().add(0.5, 0.5, 0.5);

            if (!to.getWorld().equals(portalCenter.getWorld())) continue;

            double dx = to.getX() - portalCenter.getX();
            double dy = to.getY() - portalCenter.getY();
            double dz = to.getZ() - portalCenter.getZ();
            double distSq = dx * dx + dy * dy + dz * dz;

            if (distSq <= TRIGGER_RADIUS_SQ) {
                teleportToLobby(player);
                break;
            }
        }
    }

    private void teleportToLobby(Player player) {
        if (!teleporting.add(player.getUniqueId())) return;
        Location lobbySpawn = plugin.getCBConfig().getLobbySpawn();
        if (lobbySpawn == null || lobbySpawn.getWorld() == null) {
            player.sendMessage(CC.translate("&cLobby location is not configured. Contact an admin."));
            teleporting.remove(player.getUniqueId());
            return;
        }

        stateManager.setState(player, PlayerState.IN_LOBBY);

        plugin.getSellManager().sellAllOres(player);

        player.getInventory().setHelmet(null);
        player.getInventory().setChestplate(null);
        player.getInventory().setLeggings(null);
        player.getInventory().setBoots(null);
        player.getInventory().clear();

        player.teleport(lobbySpawn);
        teleporting.remove(player.getUniqueId());

        plugin.getScoreboardService().restoreGlimzoBoard(player, true);

        player.sendMessage(CC.translate("&a&l✦ Returned to lobby via portal."));
        plugin.getTitleService().sendTitle(player,
                "&a&lLOBBY", "&7Your progress is saved.", 5, 30, 10);
        player.playSound(lobbySpawn, Sound.ENDERMAN_TELEPORT, 0.8f, 1.2f);

        ParticleUtil.endSignal(player.getLocation(), 10);
        ParticleUtil.smoke(player.getLocation(), 8);
    }

    private String getZoneLabel(Location loc) {
        var zoneType = plugin.getZoneManager().detectZone(loc);
        return zoneType.displayName();
    }

    public List<ActivePortal> getActivePortals() {
        return Collections.unmodifiableList(activePortals);
    }

    public static class ActivePortal {
        private final Location location;

        public ActivePortal(Location location) {
            this.location = location.clone();
        }

        public Location getLocation() { return location; }
    }
}
