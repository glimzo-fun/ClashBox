package net.glimzo.clashbox.arena;

import net.glimzo.clashbox.core.ClashBoxPlugin;
import net.glimzo.clashbox.player.PlayerState;
import net.glimzo.clashbox.player.PlayerStateManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import net.glimzo.clashbox.ui.ParticleUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import me.pikashrey.glimzocore.utilities.chat.CC;

public class PitEntryManager implements Listener {

    private final ClashBoxPlugin plugin;
    private final PlayerStateManager stateManager;

    public PitEntryManager(ClashBoxPlugin plugin, PlayerStateManager stateManager) {
        this.plugin       = plugin;
        this.stateManager = stateManager;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();

        if (stateManager.getState(player) != PlayerState.IN_LOBBY) return;

        Location to = event.getTo();
        if (to == null) return;

        if (to.getY() > plugin.getCBConfig().getPitEntryY()) return;

        double cx = plugin.getCBConfig().getCenterX();
        double cz = plugin.getCBConfig().getCenterZ();
        double dx = to.getX() - cx;
        double dz = to.getZ() - cz;
        double pitRadius = plugin.getCBConfig().getPitRadius();

        if ((dx * dx + dz * dz) > (pitRadius * pitRadius)) return;

        triggerArenaEntry(player);
    }

    private void triggerArenaEntry(Player player) {
        if (stateManager.getState(player) == PlayerState.ENTERING_ARENA) return;

        stateManager.setState(player, PlayerState.ENTERING_ARENA);

        // 1.8 has no SLOW_FALLING - we cancel fall damage in SafeLandingHandler instead
        player.addPotionEffect(new PotionEffect(
                PotionEffectType.JUMP, 60, 1, false, false));
        player.addPotionEffect(new PotionEffect(
                PotionEffectType.SPEED, 40, 0, false, false));

        player.playSound(player.getLocation(), Sound.PORTAL_TRAVEL, 0.4f, 1.8f);

        int[] taskId = {0};
        taskId[0] = plugin.getServer().getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            if (!player.isOnline()) {
                plugin.getServer().getScheduler().cancelTask(taskId[0]);
                return;
            }
            if (stateManager.getState(player) != PlayerState.ENTERING_ARENA) {
                plugin.getServer().getScheduler().cancelTask(taskId[0]);
                return;
            }
            Location loc = player.getLocation();
            ParticleUtil.critMagic(loc, 8);
            ParticleUtil.fireworkSpark(loc, 5);
        }, 0L, 3L);

        plugin.getTitleService().sendTitle(player,
                "&b&lCLASH BOX", "&7You're entering the arena...", 5, 50, 10);

        var profile = plugin.getProfileManager().getProfile(player);
        if (profile != null) profile.resetSessionSellTracking();
    }

    public void onArenaLanded(Player player) {
        if (stateManager.getState(player) != PlayerState.ENTERING_ARENA) return;

        var zone = plugin.getZoneManager().detectZone(player.getLocation());
        stateManager.updateZone(player, zone);

        giveStarterKitIfEmpty(player);
    }

    private void giveStarterKitIfEmpty(Player player) {
        boolean hasItems = false;
        for (var item : player.getInventory().getContents()) {
            if (item != null && item.getType() != Material.AIR) {
                hasItems = true;
                break;
            }
        }
        if (hasItems) return;

        plugin.getUpgradeManager().giveStarterKit(player);
        player.sendMessage(CC.translate("&7You received a &fstarter kit&7. " +
                "&eUpgrade your gear at the shop in the Outer Zone!"));
    }
}
