package net.glimzo.clashbox.combat;

import net.glimzo.clashbox.core.ClashBoxPlugin;
import net.glimzo.clashbox.player.ClashBoxProfile;
import net.glimzo.clashbox.player.PlayerStateManager;
import net.glimzo.clashbox.zone.ZoneType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

import java.util.List;
import java.util.UUID;
import me.pikashrey.glimzocore.utilities.chat.CC;

public class KillManager implements Listener {

    private final ClashBoxPlugin plugin;
    private final StreakTracker streakTracker;
    private final BountyManager bountyManager;
    private final AssistTracker assistTracker;

    public KillManager(ClashBoxPlugin plugin,
                       StreakTracker streakTracker,
                       BountyManager bountyManager,
                       AssistTracker assistTracker) {
        this.plugin        = plugin;
        this.streakTracker = streakTracker;
        this.bountyManager = bountyManager;
        this.assistTracker = assistTracker;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();

        PlayerStateManager stateManager = plugin.getPlayerStateManager();

        if (!stateManager.isAliveInArena(victim)) return;

        ZoneType zone = stateManager.getLastZone(victim);

        if (killer != null && stateManager.isAliveInArena(killer)) {
            if (plugin.getTeamManager().areTeammates(killer, victim)) {
                processEnvironmentalDeath(victim, zone);
            } else {
                processKill(killer, victim, zone);
            }
        } else {
            processEnvironmentalDeath(victim, zone);
        }
    }

    private void processKill(Player killer, Player victim, ZoneType zone) {
        long reward = calculateKillReward(killer, victim, zone);

        long bountyReward = 0L;
        if (bountyManager.hasBounty(victim.getUniqueId())) {
            // claimBounty already credits the bounty internally
            bountyReward = bountyManager.claimBounty(victim.getUniqueId(), killer);
        }

        plugin.getShardEconomy().addShards(killer.getUniqueId(), reward,
                "Kill in " + zone.name());

        long totalReward = reward + bountyReward;

        net.glimzo.clashbox.tier.NotorietySource source = switch (zone) {
            case CORE  -> net.glimzo.clashbox.tier.NotorietySource.KILL_CORE;
            case MID   -> net.glimzo.clashbox.tier.NotorietySource.KILL_MID;
            default    -> net.glimzo.clashbox.tier.NotorietySource.KILL_OUTER;
        };
        long notoriety = bountyReward > 0
                ? net.glimzo.clashbox.tier.NotorietySource.KILL_BOUNTY.getAmount()
                : source.getAmount();
        plugin.getTierManager().addNotoriety(killer, notoriety, source);

        int newStreak = streakTracker.incrementStreak(killer);
        streakTracker.resetStreak(victim);

        int bountyThreshold = plugin.getCBConfig().getStreakBountyThreshold();
        if (newStreak >= bountyThreshold) {
            bountyManager.placeBounty(killer, newStreak);
        }

        processAssists(victim.getUniqueId(), killer.getUniqueId(), reward, zone);

        ClashBoxProfile killerProfile = plugin.getProfileManager().getProfile(killer);
        ClashBoxProfile victimProfile = plugin.getProfileManager().getProfile(victim);
        if (killerProfile != null) killerProfile.incrementLifetimeKills();
        if (victimProfile != null) victimProfile.incrementLifetimeDeaths();

        sendKillFeedback(killer, victim, totalReward, newStreak, zone, bountyReward > 0);
        assistTracker.clearPlayer(victim.getUniqueId());
    }

    private void processEnvironmentalDeath(Player victim, ZoneType zone) {
        streakTracker.resetStreak(victim);
        bountyManager.removeBounty(victim.getUniqueId());
        ClashBoxProfile p = plugin.getProfileManager().getProfile(victim);
        if (p != null) p.incrementLifetimeDeaths();
        assistTracker.clearPlayer(victim.getUniqueId());
    }

    public long calculateKillReward(Player killer, Player victim, ZoneType zone) {
        long base = plugin.getCBConfig().getBaseKillReward();

        double zoneMultiplier = switch (zone) {
            case OUTER -> 1.0;
            case MID   -> plugin.getCBConfig().getZoneKillMultiplier("mid");
            case CORE  -> plugin.getCBConfig().getZoneKillMultiplier("core");
        };

        int streak = streakTracker.getStreak(killer);
        double streakMultiplier = 1.0 +
                (streak * plugin.getCBConfig().getStreakCoinMultiplierPerKill());

        return (long)(base * zoneMultiplier * streakMultiplier);
    }

    private void processAssists(UUID victimUuid, UUID killerUuid, long killReward, ZoneType zone) {
        List<UUID> assisters = assistTracker.getAssisters(victimUuid, killerUuid);
        if (assisters.isEmpty()) return;

        double assistPercent = plugin.getCBConfig().getAssistRewardPercent();
        long   assistReward  = (long)(killReward * assistPercent);

        for (UUID assisterUuid : assisters) {
            Player assister = plugin.getServer().getPlayer(assisterUuid);
            if (assister == null || !assister.isOnline()) continue;

            plugin.getShardEconomy().addShards(assisterUuid, assistReward,
                    "Assist on kill in " + zone.name());

            assister.sendMessage(CC.translate(
                    "&8[&bAssist&8] &b+◆ " + assistReward +
                            " &7assist reward (" + zone.displayName() + "&7)"));
        }
    }

    private void sendKillFeedback(Player killer, Player victim, long totalReward,
                                  int newStreak, ZoneType zone, boolean wasBounty) {

        StringBuilder msg = new StringBuilder();
        msg.append("&a&l+◆ ").append(totalReward).append(" &r");
        msg.append("&7[Kill - ").append(zone.displayName()).append("&7]");
        if (newStreak > 1) msg.append(" &e&l").append(newStreak).append(" STREAK");
        if (wasBounty)     msg.append(" &6&l[BOUNTY]");

        killer.sendMessage(CC.translate(msg.toString()));

        if (totalReward >= 1000 || newStreak >= 5) {
            plugin.getTitleService().sendTitle(
                    killer,
                    "&a&l+◆ " + totalReward,
                    wasBounty ? "&6★ Bounty Claimed!" : "&7" + zone.displayName(),
                    3, 25, 8
            );
        }

        killer.playSound(killer.getLocation(), org.bukkit.Sound.ORB_PICKUP, 0.8f, 1.3f);
    }
}
