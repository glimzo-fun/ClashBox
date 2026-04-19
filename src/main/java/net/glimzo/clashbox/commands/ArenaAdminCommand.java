package net.glimzo.clashbox.commands;

import me.pikashrey.glimzocore.utilities.chat.CC;
import net.glimzo.clashbox.core.ClashBoxPlugin;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

public class ArenaAdminCommand {

    private final ClashBoxPlugin plugin;
    private final Map<UUID, Location> pos1Map = new HashMap<>();

    public ArenaAdminCommand(ClashBoxPlugin plugin) {
        this.plugin = plugin;
    }

    public void handle(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(CC.translate("&cMust be a player."));
            return;
        }

        if (args.length < 2) {
            sendHelp(player);
            return;
        }

        switch (args[1].toLowerCase()) {
            case "pos1"      -> handlePos1(player);
            case "setregion" -> handleSetRegion(player, args);
            case "resnap"    -> handleResnap(player);
            case "info"      -> handleInfo(player);
            default          -> sendHelp(player);
        }
    }

    private void handlePos1(Player player) {
        Location loc = player.getLocation().getBlock().getLocation();
        pos1Map.put(player.getUniqueId(), loc);
        player.sendMessage(CC.translate(
                "&a[Arena] &7Pos1 set to &e" +
                loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ() +
                "&7. Now walk to the opposite corner and run &e/cb arena setregion&7."));
    }

    private void handleSetRegion(Player player, String[] args) {
        Location corner1, corner2;

        if (args.length >= 8) {
            try {
                int x1 = Integer.parseInt(args[2]);
                int y1 = Integer.parseInt(args[3]);
                int z1 = Integer.parseInt(args[4]);
                int x2 = Integer.parseInt(args[5]);
                int y2 = Integer.parseInt(args[6]);
                int z2 = Integer.parseInt(args[7]);
                corner1 = new Location(player.getWorld(), x1, y1, z1);
                corner2 = new Location(player.getWorld(), x2, y2, z2);
            } catch (NumberFormatException e) {
                player.sendMessage(CC.translate("&cCoordinates must be integers."));
                return;
            }
        } else {
            corner1 = pos1Map.get(player.getUniqueId());
            if (corner1 == null) {
                player.sendMessage(CC.translate(
                        "&cNo pos1 set. Run &e/cb arena pos1 &cfirst, " +
                        "then walk to the other corner and run this command."));
                return;
            }
            corner2 = player.getLocation().getBlock().getLocation();
            pos1Map.remove(player.getUniqueId());
        }

        if (!corner1.getWorld().getName().equals("box") ||
            !corner2.getWorld().getName().equals("box")) {
            player.sendMessage(CC.translate("&cBoth corners must be in world &ebox&c."));
            return;
        }

        plugin.getArenaBlockManager().saveRegion(corner1, corner2);

        player.sendMessage(CC.translate("&a[Arena] &7Region saved. Taking snapshot async..."));
        player.sendMessage(CC.translate(
                "&7From &e" + corner1.getBlockX() + "," + corner1.getBlockY() + "," + corner1.getBlockZ() +
                " &7to &e" + corner2.getBlockX() + "," + corner2.getBlockY() + "," + corner2.getBlockZ()));

        plugin.getArenaBlockManager().retakeSnapshot(() ->
                player.sendMessage(CC.translate("&a[Arena] Snapshot complete! " +
                        plugin.getArenaBlockManager().getSnapshotSize() + " blocks indexed.")));
    }

    private void handleResnap(Player player) {
        if (!plugin.getArenaBlockManager().isRegionLoaded()) {
            player.sendMessage(CC.translate("&cNo region defined. Run &e/cb arena setregion &cfirst."));
            return;
        }
        player.sendMessage(CC.translate("&a[Arena] &7Re-snapshotting blocks async..."));
        plugin.getArenaBlockManager().retakeSnapshot(() ->
                player.sendMessage(CC.translate("&a[Arena] Re-snapshot complete! " +
                        plugin.getArenaBlockManager().getSnapshotSize() + " blocks indexed.")));
    }

    private void handleInfo(Player player) {
        player.sendMessage(CC.translate("&8━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
        player.sendMessage(CC.translate("&b&l  ARENA BLOCK MANAGER"));
        player.sendMessage(CC.translate("&8━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
        player.sendMessage(CC.translate("&7World:    &ebox"));
        player.sendMessage(CC.translate("&7Region:   &e" +
                plugin.getArenaBlockManager().getRegionSummary()));
        player.sendMessage(CC.translate("&7Snapshot: &e" +
                plugin.getArenaBlockManager().getSnapshotSize() + " &7blocks"));
        player.sendMessage(CC.translate("&7Player-placed (session): &e" +
                plugin.getArenaBlockManager().getPlayerPlacedCount() + " &7blocks"));
        player.sendMessage(CC.translate("&7Regen delay: &e3 seconds"));
        player.sendMessage(CC.translate("&8━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
    }

    public List<String> tabComplete(String[] args) {
        if (args.length == 2) {
            return filter(List.of("pos1", "setregion", "resnap", "info"), args[1]);
        }
        return Collections.emptyList();
    }

    private List<String> filter(List<String> opts, String prefix) {
        String p = prefix.toLowerCase();
        return opts.stream().filter(s -> s.startsWith(p)).collect(Collectors.toList());
    }

    private void sendHelp(Player player) {
        player.sendMessage(CC.translate("&b&lArena Commands:"));
        player.sendMessage(CC.translate("&e/cb arena pos1 &7- Mark corner 1"));
        player.sendMessage(CC.translate("&e/cb arena setregion &7- Set region from pos1 to your pos"));
        player.sendMessage(CC.translate("&e/cb arena setregion <x1 y1 z1 x2 y2 z2> &7- Explicit coords"));
        player.sendMessage(CC.translate("&e/cb arena resnap &7- Re-snapshot after map edits"));
        player.sendMessage(CC.translate("&e/cb arena info &7- Show region + snapshot stats"));
    }
}
