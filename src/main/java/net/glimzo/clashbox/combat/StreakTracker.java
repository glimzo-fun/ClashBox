package net.glimzo.clashbox.combat;

import net.glimzo.clashbox.core.ClashBoxPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import me.pikashrey.glimzocore.utilities.chat.CC;

public class StreakTracker implements Listener {

    private final ClashBoxPlugin plugin;
    private final Map<UUID, Integer> streaks = new HashMap<>();

    public StreakTracker(ClashBoxPlugin plugin) {
        this.plugin = plugin;
    }

    public int getStreak(Player player) {
        return streaks.getOrDefault(player.getUniqueId(), 0);
    }

    public int getStreak(UUID uuid) {
        return streaks.getOrDefault(uuid, 0);
    }

    public int incrementStreak(Player player) {
        int newStreak = streaks.merge(player.getUniqueId(), 1, Integer::sum);
        checkMilestone(player, newStreak);
        return newStreak;
    }

    public int resetStreak(Player player) {
        int old = streaks.getOrDefault(player.getUniqueId(), 0);
        streaks.put(player.getUniqueId(), 0);
        return old;
    }

    public void resetStreak(UUID uuid) {
        streaks.put(uuid, 0);
    }

    public void removePlayer(UUID uuid) {
        streaks.remove(uuid);
    }

    private void checkMilestone(Player player, int streak) {
        List<Integer> milestones = plugin.getCBConfig().getStreakAnnounceMilestones();
        if (!milestones.contains(streak)) return;

        String name  = player.getName();
        String color = streakColor(streak);
        String msg   = buildMilestoneMessage(name, streak, color);

        for (Player online : plugin.getServer().getOnlinePlayers()) {
            online.sendMessage(msg);
        }

        plugin.getTitleService().sendTitle(
                player,
                color + "&l" + streak + " KILL STREAK!",
                "&7Keep going - you have a bounty on you!",
                5, 50, 15
        );

        player.playSound(player.getLocation(), org.bukkit.Sound.LEVEL_UP, 1.0f, 1.5f);
    }

    private String buildMilestoneMessage(String name, int streak, String color) {
        String suffix = streakSuffix(streak);
        return "&8[&c⚔&8] " + color + "&l" + name + " &r&7is on a " +
               color + "&l" + streak + "-kill streak" + suffix + "&7! &c&lBOUNTY ACTIVE.";
    }

    private String streakColor(int streak) {
        if (streak >= 20) return "&5";
        if (streak >= 12) return "&c";
        if (streak >= 8)  return "&6";
        if (streak >= 5)  return "&e";
        return "&a";
    }

    private String streakSuffix(int streak) {
        if (streak >= 20) return " &5&l[LEGENDARY]";
        if (streak >= 12) return " &c&l[UNSTOPPABLE]";
        if (streak >= 8)  return " &6&l[RAMPAGE]";
        if (streak >= 5)  return " &e&l[HOT]";
        return "";
    }
}
