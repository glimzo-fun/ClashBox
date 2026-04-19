package net.glimzo.clashbox.ui;

import me.pikashrey.glimzocore.utilities.chat.CC;
import net.glimzo.clashbox.core.ClashBoxPlugin;
import net.glimzo.clashbox.player.PlayerStateManager;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ActionBarService {

    private final ClashBoxPlugin plugin;
    private final Map<UUID, TempMessage> temporary = new HashMap<>();

    public ActionBarService(ClashBoxPlugin plugin) {
        this.plugin = plugin;
    }

    public void startTask() {
        int interval = plugin.getCBConfig().getActionBarUpdateTicks();
        plugin.getServer().getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                sendUpdate(player);
            }
        }, interval, interval);
    }

    private void sendUpdate(Player player) {
        UUID uuid = player.getUniqueId();
        long now  = System.currentTimeMillis();

        TempMessage temp = temporary.get(uuid);
        if (temp != null) {
            if (now < temp.expiresAt) { sendActionBar(player, temp.message); return; }
            else                      { temporary.remove(uuid); }
        }

        PlayerStateManager sm = plugin.getPlayerStateManager();
        if (sm.isAliveInArena(player)) {
            long wallet = plugin.getShardEconomy().getBalance(uuid);
            int  streak = plugin.getStreakTracker().getStreak(player);
            String zone = sm.getLastZone(player).displayName();
            String bar  = zone + "  &7|  &eWallet: &f$" + wallet;
            if (streak > 0) bar += "  &7|  &cX" + streak + " STREAK";
            sendActionBar(player, CC.translate(bar));
        }
    }

    public void sendPersistent(Player player, String message, int durationTicks) {
        long expiresAt = System.currentTimeMillis() + (durationTicks * 50L);
        temporary.put(player.getUniqueId(), new TempMessage(CC.translate(message), expiresAt));
        sendActionBar(player, CC.translate(message));
    }

    private static String nmsPath(String cls) {
        String ver = org.bukkit.Bukkit.getServer().getClass().getPackage().getName().split("\\.")[3];
        return "net.minecraft.server." + ver + "." + cls;
    }

    private void sendActionBar(Player player, String message) {
        try {
            Object packet = Class.forName(nmsPath("PacketPlayOutChat"))
                    .getConstructor(Class.forName(nmsPath("IChatBaseComponent")), byte.class)
                    .newInstance(
                            Class.forName(nmsPath("ChatSerializer"))
                                    .getMethod("a", String.class)
                                    .invoke(null, "{\"text\":\"" + message.replace("\"", "\\\"") + "\"}"),
                            (byte) 2
                    );
            Object handle = player.getClass().getMethod("getHandle").invoke(player);
            Object conn   = handle.getClass().getField("playerConnection").get(handle);
            conn.getClass().getMethod("sendPacket", Class.forName(nmsPath("Packet"))).invoke(conn, packet);
        } catch (Exception ignored) {}
    }

    private static class TempMessage {
        final String message;
        final long   expiresAt;
        TempMessage(String m, long e) { message = m; expiresAt = e; }
    }
}
