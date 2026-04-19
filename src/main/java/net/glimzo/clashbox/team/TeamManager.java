package net.glimzo.clashbox.team;

import net.glimzo.clashbox.core.ClashBoxPlugin;
import net.glimzo.clashbox.economy.EconomyHook;
import net.glimzo.clashbox.player.ClashBoxProfile;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;

import java.util.*;
import me.pikashrey.glimzocore.utilities.chat.CC;

public class TeamManager implements Listener {

    private final ClashBoxPlugin plugin;
    private final EconomyHook economyHook;

    private final Map<UUID, ClashBoxTeam> teams        = new HashMap<>();
    private final Map<UUID, UUID>         playerTeamMap = new HashMap<>();
    private final Map<UUID, PendingInvite> pendingInvites = new HashMap<>();

    public TeamManager(ClashBoxPlugin plugin, EconomyHook economyHook) {
        this.plugin      = plugin;
        this.economyHook = economyHook;
    }

    public String createTeam(Player leader) {
        if (getTeam(leader) != null) return "&cYou are already in a team!";

        UUID teamId = UUID.randomUUID();
        ClashBoxTeam team = new ClashBoxTeam(teamId, leader.getUniqueId());
        teams.put(teamId, team);
        playerTeamMap.put(leader.getUniqueId(), teamId);

        ClashBoxProfile profile = plugin.getProfileManager().getProfile(leader);
        if (profile != null) profile.setTeamId(teamId);

        return null;
    }

    public String invite(Player inviter, Player target) {
        ClashBoxTeam team = getTeam(inviter);
        if (team == null) return "&cYou are not in a team. Create one first.";
        if (!team.getLeader().equals(inviter.getUniqueId()))
            return "&cOnly the team leader can invite players.";
        if (team.getMembers().size() >= plugin.getCBConfig().getTeamMaxSize())
            return "&cTeam is full (" + plugin.getCBConfig().getTeamMaxSize() + " max).";
        if (getTeam(target) != null) return "&c" + target.getName() + " is already in a team.";

        int expirySeconds = plugin.getCBConfig().getTeamInviteExpiry();
        long expiresAt = System.currentTimeMillis() + (expirySeconds * 1000L);
        pendingInvites.put(target.getUniqueId(), new PendingInvite(inviter.getUniqueId(), expiresAt));

        target.sendMessage(CC.translate("&b&l[Team Invite] &r&7" + inviter.getName() +
                " invited you to join their team! " +
                "&eType &a/team accept &eto join. &7(Expires in " + expirySeconds + "s)"));
        inviter.sendMessage(CC.translate("&7Invite sent to &f" + target.getName() + "&7."));
        return null;
    }

    public String acceptInvite(Player player) {
        PendingInvite invite = pendingInvites.remove(player.getUniqueId());
        if (invite == null)     return "&cYou have no pending team invite.";
        if (invite.isExpired()) return "&cYour team invite has expired.";

        UUID teamId = playerTeamMap.get(invite.getInviterUuid());
        if (teamId == null) return "&cThe team no longer exists.";
        ClashBoxTeam team = teams.get(teamId);
        if (team == null) return "&cThe team no longer exists.";

        team.addMember(player.getUniqueId());
        playerTeamMap.put(player.getUniqueId(), teamId);
        ClashBoxProfile profile = plugin.getProfileManager().getProfile(player);
        if (profile != null) profile.setTeamId(teamId);

        broadcastToTeam(team, "&a&l[Team] &r&f" + player.getName() + " &7joined the team!");
        return null;
    }

    public String leaveTeam(Player player) {
        ClashBoxTeam team = getTeam(player);
        if (team == null) return "&cYou are not in a team.";

        boolean wasLeader = team.getLeader().equals(player.getUniqueId());

        team.removeMember(player.getUniqueId());
        playerTeamMap.remove(player.getUniqueId());
        ClashBoxProfile profile = plugin.getProfileManager().getProfile(player);
        if (profile != null) profile.setTeamId(null);

        if (team.getMembers().isEmpty()) {
            disbandTeam(team);
            return null;
        }

        if (wasLeader) {
            broadcastToTeam(team, "&e&l[Team] &r&f" + player.getName() +
                    " &7(leader) left. New leader: &f" +
                    resolveName(team.getLeader()));
        } else {
            broadcastToTeam(team, "&c&l[Team] &r&f" + player.getName() + " &7left the team.");
        }

        return null;
    }

    public String depositToVault(Player player, long amount) {
        ClashBoxTeam team = getTeam(player);
        if (team == null) return "&cYou are not in a team.";
        if (plugin.getShardEconomy().removeShards(player.getUniqueId(), amount, "Team vault deposit") == 0)
            return "&cInsufficient wallet balance.";

        team.setVaultBalance(team.getVaultBalance() + amount);
        broadcastToTeam(team, CC.translate("&a[Team Vault] &f" + player.getName() +
                " &7deposited &a◆ " + amount + "&7. Vault: &a◆ " + team.getVaultBalance()));
        return null;
    }

    public String withdrawFromVault(Player player, long amount) {
        ClashBoxTeam team = getTeam(player);
        if (team == null) return "&cYou are not in a team.";
        if (!team.getLeader().equals(player.getUniqueId()))
            return "&cOnly the team leader can withdraw from the vault.";
        if (team.getVaultBalance() < amount) return "&cInsufficient vault balance.";

        team.setVaultBalance(team.getVaultBalance() - amount);
        plugin.getShardEconomy().addShards(player.getUniqueId(), amount, "Team vault withdrawal");
        broadcastToTeam(team, CC.translate("&e[Team Vault] &f" + player.getName() +
                " &7withdrew &e◆ " + amount + "&7. Vault: &a◆ " + team.getVaultBalance()));
        return null;
    }

    public ClashBoxTeam getTeam(Player player) {
        UUID teamId = playerTeamMap.get(player.getUniqueId());
        return teamId == null ? null : teams.get(teamId);
    }

    public ClashBoxTeam getTeamById(UUID teamId) {
        return teams.get(teamId);
    }

    public boolean areTeammates(Player a, Player b) {
        UUID ta = playerTeamMap.get(a.getUniqueId());
        UUID tb = playerTeamMap.get(b.getUniqueId());
        return ta != null && ta.equals(tb);
    }

    private void disbandTeam(ClashBoxTeam team) {
        long vault     = team.getVaultBalance();
        int  members   = team.getMembers().size();
        long perPlayer = members > 0 && vault > 0 ? vault / members : 0;

        for (UUID memberId : new HashSet<>(team.getMembers())) {
            playerTeamMap.remove(memberId);
            Player member = plugin.getServer().getPlayer(memberId);
            if (member != null) {
                ClashBoxProfile profile = plugin.getProfileManager().getProfile(member);
                if (profile != null) profile.setTeamId(null);
                if (perPlayer > 0) {
                    plugin.getShardEconomy().addShards(memberId, perPlayer, "Team disbanded - vault share");
                    member.sendMessage(CC.translate("&c[Team] Your team has been disbanded. " +
                            "&7You received your share of the vault: &a◆ " + perPlayer));
                } else {
                    member.sendMessage(CC.translate("&c[Team] Your team has been disbanded."));
                }
            }
        }
        teams.remove(team.getTeamId());
    }

    private void broadcastToTeam(ClashBoxTeam team, String message) {
        for (UUID memberId : team.getMembers()) {
            Player member = plugin.getServer().getPlayer(memberId);
            if (member != null && member.isOnline()) {
                member.sendMessage(message);
            }
        }
    }

    private String resolveName(UUID uuid) {
        Player online = plugin.getServer().getPlayer(uuid);
        if (online != null) return online.getName();
        String name = plugin.getServer().getOfflinePlayer(uuid).getName();
        return name != null ? name : uuid.toString().substring(0, 8);
    }

    public static class PendingInvite {
        private final UUID inviterUuid;
        private final long expiresAt;

        PendingInvite(UUID inviterUuid, long expiresAt) {
            this.inviterUuid = inviterUuid;
            this.expiresAt   = expiresAt;
        }

        public boolean isExpired()       { return System.currentTimeMillis() >= expiresAt; }
        public UUID    getInviterUuid()  { return inviterUuid; }
    }
}
