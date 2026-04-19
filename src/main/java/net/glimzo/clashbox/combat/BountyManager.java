package net.glimzo.clashbox.combat;

import net.glimzo.clashbox.core.ClashBoxPlugin;
import net.glimzo.clashbox.player.ClashBoxProfile;
import org.bukkit.entity.Player;

import java.util.*;
import me.pikashrey.glimzocore.utilities.chat.CC;

public class BountyManager {

    private final ClashBoxPlugin plugin;
    private final Map<UUID, ActiveBounty> activeBounties = new HashMap<>();

    public BountyManager(ClashBoxPlugin plugin) {
        this.plugin = plugin;
    }

    public void placeBounty(Player target, int streak) {
        if (activeBounties.containsKey(target.getUniqueId())) {
            updateBounty(target, streak);
            return;
        }

        long walletValue = plugin.getShardEconomy().getBalance(target.getUniqueId());
        long baseValue   = plugin.getCBConfig().getBaseKillReward();
        double multiplier = plugin.getCBConfig().getBountyBaseMultiplier();

        long value = Math.max(
                plugin.getCBConfig().getBountyMinimumValue(),
                (long)((baseValue * streak * multiplier) + (walletValue * 0.15))
        );

        long expiresAt = System.currentTimeMillis() +
                (plugin.getCBConfig().getBountyExpirySeconds() * 1000L);

        ActiveBounty bounty = new ActiveBounty(target.getUniqueId(), value, streak, expiresAt);
        activeBounties.put(target.getUniqueId(), bounty);

        ClashBoxProfile profile = plugin.getProfileManager().getProfile(target);
        if (profile != null) profile.incrementBountiesPlacedOnSelf();

        broadcastBountyPlaced(target, value, streak);
        scheduleBountyExpiry(target.getUniqueId(), expiresAt);
    }

    private void updateBounty(Player target, int streak) {
        ActiveBounty existing = activeBounties.get(target.getUniqueId());
        if (existing == null) return;
        long newValue = (long)(existing.getValue() * 1.3);
        existing.setValue(newValue);
        existing.setStreak(streak);

        for (Player p : plugin.getServer().getOnlinePlayers()) {
            p.sendMessage(CC.translate("&8[&6★&8] &e&l" + target.getName() +
                    "'s bounty escalated to &6&l◆ " + newValue +
                    " &e(&c" + streak + " streak&e)!"));
        }
    }

    public long claimBounty(UUID targetUuid, Player claimer) {
        ActiveBounty bounty = activeBounties.remove(targetUuid);
        if (bounty == null || bounty.isExpired()) return 0L;

        long value = bounty.getValue();
        plugin.getShardEconomy().addShards(claimer.getUniqueId(), value, "Bounty claim");

        ClashBoxProfile claimerProfile = plugin.getProfileManager().getProfile(claimer);
        if (claimerProfile != null) claimerProfile.incrementBountiesClaimed();

        broadcastBountyClaimed(claimer, targetUuid, value, bounty.getStreak());
        return value;
    }

    private void scheduleBountyExpiry(UUID targetUuid, long expiresAt) {
        long delayMs   = expiresAt - System.currentTimeMillis();
        long delayTicks = Math.max(20L, delayMs / 50L);

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            ActiveBounty b = activeBounties.get(targetUuid);
            if (b == null) return;
            if (!b.isExpired()) return;
            activeBounties.remove(targetUuid);

            Player target = plugin.getServer().getPlayer(targetUuid);
            String name = target != null ? target.getName() : targetUuid.toString().substring(0, 8);
            for (Player p : plugin.getServer().getOnlinePlayers()) {
                p.sendMessage(CC.translate("&8[&7★&8] &7Bounty on &f" + name + " &7expired unclaimed."));
            }
        }, delayTicks);
    }

    public boolean hasBounty(UUID uuid) {
        ActiveBounty b = activeBounties.get(uuid);
        return b != null && !b.isExpired();
    }

    public long getBountyValue(UUID uuid) {
        ActiveBounty b = activeBounties.get(uuid);
        return (b != null && !b.isExpired()) ? b.getValue() : 0L;
    }

    public void removeBounty(UUID uuid) {
        activeBounties.remove(uuid);
    }

    private void broadcastBountyPlaced(Player target, long value, int streak) {
        String msg = "&8[&6★ BOUNTY&8] &e&l" + target.getName() +
                " &r&7is on a &c&l" + streak + "-kill streak&7! " +
                "&6&l$" + value + " &7reward for ending it!";
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            p.sendMessage(CC.translate(msg));
            if (!p.equals(target)) {
                p.playSound(p.getLocation(), org.bukkit.Sound.NOTE_BASS, 0.7f, 0.8f);
            }
        }
    }

    private void broadcastBountyClaimed(Player claimer, UUID targetUuid, long value, int streak) {
        Player target = plugin.getServer().getPlayer(targetUuid);
        String targetName = target != null ? target.getName() :
                targetUuid.toString().substring(0, 8);

        String msg = "&8[&a★ BOUNTY CLAIMED&8] &a&l" + claimer.getName() +
                " &r&7ended &c&l" + targetName + "'s &r&7" + streak +
                "-kill streak and claimed &a&l$" + value + "&7!";
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            p.sendMessage(CC.translate(msg));
            p.playSound(p.getLocation(), org.bukkit.Sound.FIREWORK_BLAST, 0.6f, 1.2f);
        }
    }

    public static class ActiveBounty {
        private final UUID targetUuid;
        private long value;
        private int streak;
        private final long expiresAt;

        public ActiveBounty(UUID uuid, long value, int streak, long expiresAt) {
            this.targetUuid = uuid;
            this.value      = value;
            this.streak     = streak;
            this.expiresAt  = expiresAt;
        }

        public boolean isExpired()      { return System.currentTimeMillis() >= expiresAt; }
        public UUID getTargetUuid()     { return targetUuid; }
        public long getValue()          { return value; }
        public void setValue(long v)    { this.value = v; }
        public int  getStreak()         { return streak; }
        public void setStreak(int s)    { this.streak = s; }
        public long getExpiresAt()      { return expiresAt; }
    }
}
