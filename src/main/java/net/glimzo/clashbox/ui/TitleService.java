package net.glimzo.clashbox.ui;

import me.pikashrey.glimzocore.utilities.chat.CC;
import net.glimzo.clashbox.core.ClashBoxPlugin;
import org.bukkit.entity.Player;

public class TitleService {

    private final ClashBoxPlugin plugin;

    public TitleService(ClashBoxPlugin plugin) {
        this.plugin = plugin;
    }

    public void sendTitle(Player player, String title, String subtitle,
                          int fadeIn, int stay, int fadeOut) {
        try {
            String ver = org.bukkit.Bukkit.getServer().getClass()
                    .getPackage().getName().split("\\.")[3];
            String nms = "net.minecraft.server." + ver + ".";

            Class<?> chatSerializer = Class.forName(nms + "ChatSerializer");
            Class<?> iChat          = Class.forName(nms + "IChatBaseComponent");
            Class<?> packetClass    = Class.forName(nms + "PacketPlayOutTitle");
            Class<?> enumClass      = Class.forName(nms + "PacketPlayOutTitle$EnumTitleAction");

            Object titleText    = chatSerializer.getMethod("a", String.class)
                    .invoke(null, "{\"text\":\"" + CC.translate(title)   .replace("\"","\\\"") + "\"}");
            Object subtitleText = chatSerializer.getMethod("a", String.class)
                    .invoke(null, "{\"text\":\"" + CC.translate(subtitle).replace("\"","\\\"") + "\"}");

            Object enumTitle    = Enum.valueOf((Class<Enum>) enumClass, "TITLE");
            Object enumSubtitle = Enum.valueOf((Class<Enum>) enumClass, "SUBTITLE");
            Object enumTimes    = Enum.valueOf((Class<Enum>) enumClass, "TIMES");

            Object timesPacket = packetClass.getConstructor(int.class, int.class, int.class)
                    .newInstance(fadeIn, stay, fadeOut);
            Object titlePacket = packetClass.getConstructor(enumClass, iChat)
                    .newInstance(enumTitle, titleText);
            Object subPacket   = packetClass.getConstructor(enumClass, iChat)
                    .newInstance(enumSubtitle, subtitleText);

            Object handle = player.getClass().getMethod("getHandle").invoke(player);
            Object conn   = handle.getClass().getField("playerConnection").get(handle);
            Class<?> packet = Class.forName(nms + "Packet");
            java.lang.reflect.Method send = conn.getClass().getMethod("sendPacket", packet);

            send.invoke(conn, timesPacket);
            send.invoke(conn, titlePacket);
            send.invoke(conn, subPacket);
        } catch (Exception ignored) {}
    }

    public void sendTitle(Player player, String title, String subtitle) {
        sendTitle(player, title, subtitle, 10, 40, 10);
    }

    public void clearTitle(Player player) {
        try {
            String ver = org.bukkit.Bukkit.getServer().getClass()
                    .getPackage().getName().split("\\.")[3];
            String nms = "net.minecraft.server." + ver + ".";
            Class<?> packetClass = Class.forName(nms + "PacketPlayOutTitle");
            Class<?> enumClass   = Class.forName(nms + "PacketPlayOutTitle$EnumTitleAction");
            Object enumClear     = Enum.valueOf((Class<Enum>) enumClass, "CLEAR");
            Object clearPacket   = packetClass.getConstructor(enumClass,
                    Class.forName(nms + "IChatBaseComponent")).newInstance(enumClear, null);
            Object handle = player.getClass().getMethod("getHandle").invoke(player);
            Object conn   = handle.getClass().getField("playerConnection").get(handle);
            conn.getClass().getMethod("sendPacket",
                    Class.forName(nms + "Packet")).invoke(conn, clearPacket);
        } catch (Exception ignored) {}
    }
}
