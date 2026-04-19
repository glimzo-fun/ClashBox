package net.glimzo.clashbox.combat;

import net.glimzo.clashbox.core.ClashBoxPlugin;
import net.glimzo.clashbox.player.ClashBoxProfile;
import net.glimzo.clashbox.player.PlayerState;
import net.glimzo.clashbox.player.PlayerStateManager;
import net.glimzo.clashbox.progression.UpgradeType;
import net.glimzo.clashbox.zone.ZoneType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.util.UUID;
import me.pikashrey.glimzocore.utilities.chat.CC;

public class DeathHandler implements Listener {

    private final ClashBoxPlugin plugin;
    private final KillManager killManager;
    private final PlayerStateManager stateManager;

    public DeathHandler(ClashBoxPlugin plugin,
                        KillManager killManager,
                        PlayerStateManager stateManager) {
        this.plugin       = plugin;
        this.killManager  = killManager;
        this.stateManager = stateManager;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        if (!stateManager.isInArena(victim)) return;

        ZoneType zone = stateManager.getLastZone(victim);

        long lostCoins = applyDeathPenalty(victim, zone);

        event.getDrops().clear();
        event.setDeathMessage(null);

        stateManager.setState(victim, PlayerState.DEAD);
        scheduleRespawnSequence(victim, zone, lostCoins);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (stateManager.getState(player) != PlayerState.DEAD) return;
        event.setRespawnLocation(plugin.getCBConfig().getLobbySpawn());
        stateManager.setState(player, PlayerState.IN_LOBBY);
        plugin.getScoreboardService().restoreGlimzoBoard(player, true);
    }

    private long applyDeathPenalty(Player victim, ZoneType zone) {
        UUID uuid = victim.getUniqueId();

        if (zone == ZoneType.OUTER) return 0L;

        long wallet = plugin.getShardEconomy().getBalance(uuid);
        if (wallet <= 0) {
            if (zone == ZoneType.CORE) {
                plugin.getLoanManager().applyDeathPenalty(uuid);
            }
            return 0L;
        }

        double baseLossPercent = plugin.getCBConfig().getZoneDeathLossPercent(
                zone.configKey()) / 100.0;

        if (zone == ZoneType.MID) {
            ClashBoxProfile profile = plugin.getProfileManager().getProfile(victim);
            if (profile != null) {
                int protLevel = profile.getUpgradeLevel(UpgradeType.CARRY_PROTECTION);
                baseLossPercent = Math.max(0, baseLossPercent - (protLevel * 0.10));
            }
        }

        long lostAmount = (long)(wallet * baseLossPercent);
        if (lostAmount > 0) {
            plugin.getShardEconomy().removeShards(uuid, lostAmount, "Died in " + zone.name());
        }

        if (zone == ZoneType.CORE) {
            plugin.getLoanManager().applyDeathPenalty(uuid);
        }

        return lostAmount;
    }

    private void scheduleRespawnSequence(Player victim, ZoneType zone, long lostCoins) {
        UUID uuid = victim.getUniqueId();
        String killerName = victim.getKiller() != null ?
                victim.getKiller().getName() : "the arena";

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            Player p = plugin.getServer().getPlayer(uuid);
            if (p == null) return;

            String subtitle = buildDeathSubtitle(killerName, zone, lostCoins);
            plugin.getTitleService().sendTitle(p, "&c&lYOU DIED", subtitle, 5, 60, 15);

            p.sendMessage(CC.translate("&8━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
            p.sendMessage(CC.translate("&c&l  ✦ DEATH SUMMARY"));
            p.sendMessage(CC.translate("&7  Killed by: &f" + killerName));
            p.sendMessage(CC.translate("&7  Zone: " + zone.displayName()));
            if (lostCoins > 0) {
                p.sendMessage(CC.translate("&7  Lost: &c&l-◆ " + lostCoins));
            } else {
                p.sendMessage(CC.translate("&7  Lost: &a◆ 0 (safe zone)"));
            }
            p.sendMessage(CC.translate("&7  Shards: &b◆ " +
                    plugin.getShardEconomy().getBalance(uuid)));
            p.sendMessage(CC.translate("&8━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
            p.playSound(p.getLocation(), org.bukkit.Sound.WITHER_HURT, 0.5f, 0.7f);

        }, 10L);

        int respawnDelay = plugin.getCBConfig().getRespawnDelayTicks();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            Player p = plugin.getServer().getPlayer(uuid);
            if (p == null) return;
            plugin.getActionBarService().sendPersistent(p,
                    "&a&l➤ Jump in to re-enter the arena!", 100);
            p.playSound(p.getLocation(), org.bukkit.Sound.NOTE_PLING, 0.6f, 1.0f);
        }, respawnDelay);
    }

    private String buildDeathSubtitle(String killerName, ZoneType zone, long lost) {
        if (lost > 0) return "&7Killed by &f" + killerName + " &7- &c-◆ " + lost;
        return "&7Killed by &f" + killerName;
    }
}
