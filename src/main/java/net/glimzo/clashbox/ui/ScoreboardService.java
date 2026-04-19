package net.glimzo.clashbox.ui;

import me.pikashrey.glimzocore.GlimzoCore;
import me.pikashrey.glimzocore.api.player.GlobalPlayer;
import me.pikashrey.glimzocore.api.player.PlayerData;
import me.pikashrey.glimzocore.utilities.chat.CC;
import net.glimzo.clashbox.core.ClashBoxPlugin;
import net.glimzo.clashbox.player.ClashBoxProfile;
import net.glimzo.clashbox.player.PlayerStateManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scoreboard.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ScoreboardService implements Listener {

    private final ClashBoxPlugin plugin;
    private final Map<UUID, Scoreboard> arenaBoards = new HashMap<>();

    // unique colour-code strings used as stable line keys - avoids flicker on update
    private static final String[] LINE_KEYS = {
            "§0", "§1", "§2", "§3", "§4", "§5", "§6", "§7",
            "§8", "§9", "§a", "§b", "§c", "§d", "§e", "§f"
    };

    public ScoreboardService(ClashBoxPlugin plugin) {
        this.plugin = plugin;
    }

    public void startTask() {
        int ticks = plugin.getCBConfig().getScoreboardUpdateTicks();
        Bukkit.getScheduler().runTaskTimer(plugin, this::updateAll, ticks, ticks);
    }

    @EventHandler public void onJoin(PlayerJoinEvent event) {}

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        arenaBoards.remove(event.getPlayer().getUniqueId());
    }

    private void updateAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerStateManager sm = plugin.getPlayerStateManager();
            if (sm.isAliveInArena(player)) {
                updateArenaBoard(player);
            } else {
                restoreGlimzoBoard(player, false);
            }
        }
    }

    private void updateArenaBoard(Player player) {
        UUID uuid = player.getUniqueId();

        Scoreboard board = arenaBoards.computeIfAbsent(uuid, k -> {
            Scoreboard b = Bukkit.getScoreboardManager().getNewScoreboard();
            Objective obj = b.registerNewObjective("clashbox", "dummy");
            obj.setDisplayName(CC.translate("&b&lCLASH BOX"));
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);

            for (int i = 0; i < LINE_KEYS.length; i++) {
                Team team = b.registerNewTeam("line" + i);
                team.addEntry(LINE_KEYS[i]);
            }
            player.setScoreboard(b);
            return b;
        });

        Objective obj = board.getObjective("clashbox");
        if (obj == null) return;

        PlayerData gcData  = GlobalPlayer.get(player);
        ClashBoxProfile profile = plugin.getProfileManager().getProfile(player);
        PlayerStateManager sm   = plugin.getPlayerStateManager();

        long   wallet    = plugin.getShardEconomy().getBalance(uuid);
        long   bank      = profile != null ? profile.getSavingsBalance() : 0;
        int    streak    = plugin.getStreakTracker().getStreak(player);
        String zone      = sm.getLastZone(player).displayName();
        boolean hasBounty = plugin.getBountyManager().hasBounty(uuid);
        long    bountyVal = hasBounty ? plugin.getBountyManager().getBountyValue(uuid) : 0;

        String[] lines = new String[12];
        lines[11] = CC.translate("&fplay.glimzo.fun");
        lines[10] = CC.translate("&r ");
        lines[9]  = CC.translate("&f&l" + player.getName());
        lines[8]  = CC.translate("&8&m────────────────");
        lines[7]  = CC.translate("&7Zone: " + zone);
        lines[6]  = streak > 0
                ? CC.translate("&7Streak: &c&l" + streak + " !!!")
                : CC.translate("&7Streak: &f0");
        lines[5]  = CC.translate("&8────────────────");
        lines[4]  = CC.translate("&7Shards: &b◆ &f" + wallet);
        lines[3]  = CC.translate("&7Savings: &a◆ " + bank);
        lines[2]  = profile != null
                ? CC.translate("&7Kills:  &f" + profile.getSeasonKills())
                : CC.translate("&7Kills:  &f0");
        lines[1]  = gcData != null
                ? CC.translate("&7Level:  &a" + gcData.getLevel())
                : CC.translate("&7Level:  &a-");
        lines[0]  = hasBounty
                ? CC.translate("&6★ Bounty: &e◆ " + bountyVal)
                : CC.translate("&8&m────────────────&r");

        for (int i = 0; i < lines.length; i++) {
            Team team = board.getTeam("line" + i);
            if (team == null) continue;
            String text   = lines[i];
            // 1.8 team prefix max is 16 chars - split into prefix + suffix
            String prefix = text.length() > 16 ? text.substring(0, 16) : text;
            String suffix = text.length() > 16 ? text.substring(16) : "";
            if (suffix.length() > 16) suffix = suffix.substring(0, 16);
            team.setPrefix(prefix);
            team.setSuffix(suffix);
            obj.getScore(LINE_KEYS[i]).setScore(i);
        }

        if (!player.getScoreboard().equals(board)) {
            player.setScoreboard(board);
        }
    }

    public void restoreGlimzoBoard(Player player, boolean force) {
        UUID uuid = player.getUniqueId();
        if (!force && !arenaBoards.containsKey(uuid)) return;
        arenaBoards.remove(uuid);
        try {
            GlimzoCore.getInstance().getScoreboardManager().update(player);
        } catch (Exception ignored) {}
    }
}
