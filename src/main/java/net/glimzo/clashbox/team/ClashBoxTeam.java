package net.glimzo.clashbox.team;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class ClashBoxTeam {

    private final UUID teamId;
    private UUID leader;
    private final Set<UUID> members = new HashSet<>();
    private long vaultBalance;

    public ClashBoxTeam(UUID teamId, UUID leader) {
        this.teamId = teamId;
        this.leader = leader;
        this.members.add(leader);
    }

    public void addMember(UUID uuid) { members.add(uuid); }

    public void removeMember(UUID uuid) {
        members.remove(uuid);
        if (uuid.equals(leader) && !members.isEmpty()) {
            leader = members.iterator().next();
        }
    }

    public UUID      getTeamId()           { return teamId; }
    public UUID      getLeader()           { return leader; }
    public Set<UUID> getMembers()          { return members; }
    public long      getVaultBalance()     { return vaultBalance; }
    public void      setVaultBalance(long v) { this.vaultBalance = v; }
}
