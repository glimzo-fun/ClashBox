package net.glimzo.clashbox.commands;

import me.pikashrey.glimzocore.utilities.chat.CC;
import net.glimzo.clashbox.core.ClashBoxPlugin;
import net.glimzo.clashbox.sets.ArmorSet;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

public class SetAdminCommand {

    private final ClashBoxPlugin plugin;

    public SetAdminCommand(ClashBoxPlugin plugin) {
        this.plugin = plugin;
    }

    public void handle(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(CC.translate("&cPlayers only.")); return;
        }
        if (args.length < 2) { sendHelp(player); return; }

        switch (args[1].toLowerCase()) {
            case "armorstand" -> handleArmorStand(player, args);
            case "npc"        -> handleNpc(player, args);
            case "glass"      -> handleGlass(player, args);
            case "reload"     -> handleReload(player);
            case "list"       -> handleList(player);
            default           -> sendHelp(player);
        }
    }

    private void handleArmorStand(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(CC.translate("&cUsage: /cb set armorstand <setId>"));
            return;
        }
        String setId = args[2].toLowerCase();
        ArmorSet set = plugin.getSetManager().getSet(setId);
        if (set == null) {
            player.sendMessage(CC.translate("&cUnknown set: &e" + setId +
                    "&c. Valid: " + getSetIds())); return;
        }
        plugin.getSetManager().saveLocation("armor-stands", setId, player.getLocation());
        player.sendMessage(CC.translate("&a[Sets] Armor stand position for &e" + set.getName() +
                " &aset to your location."));
        player.sendMessage(CC.translate("&7Place an armor stand at this position and it will be"));
        player.sendMessage(CC.translate("&7interactable for the &e" + set.getName() + "'s Set &7display."));
    }

    private void handleNpc(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(CC.translate("&cUsage: /cb set npc <setId>"));
            player.sendMessage(CC.translate("&7Then set up a ZNPCsPlus NPC with action:"));
            player.sendMessage(CC.translate("&e  cmd \"cb npcinteract set_<setId>\""));
            return;
        }
        String setId = args[2].toLowerCase();
        ArmorSet set = plugin.getSetManager().getSet(setId);
        if (set == null) {
            player.sendMessage(CC.translate("&cUnknown set: &e" + setId)); return;
        }
        plugin.getSetManager().saveLocation("npc-locations", setId, player.getLocation());
        player.sendMessage(CC.translate("&a[Sets] NPC position for &e" + set.getName() + " &asaved."));
        player.sendMessage(CC.translate("&7Create a ZNPCsPlus NPC here with action:"));
        player.sendMessage(CC.translate("&b  /znpc action add <id> cmd \"cb npcinteract set_" + setId + "\""));
    }

    private void handleGlass(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(CC.translate("&cUsage: /cb set glass <add|remove|list|save|load> [setId]"));
            return;
        }
        switch (args[2].toLowerCase()) {
            case "add" -> {
                if (args.length < 4) {
                    player.sendMessage(CC.translate("&cUsage: /cb set glass add <setId>"));
                    return;
                }
                String setId = args[3].toLowerCase();
                ArmorSet set = plugin.getSetManager().getSet(setId);
                if (set == null) {
                    player.sendMessage(CC.translate("&cUnknown set: &e" + setId)); return;
                }
                Location target = player.getTargetBlock((java.util.Set<org.bukkit.Material>) null, 5).getLocation();
                plugin.getSetMaterialDropListener().registerNode(target, set);
                player.sendMessage(CC.translate("&a[Glass Node] Registered at " +
                        target.getBlockX() + "," + target.getBlockY() + "," + target.getBlockZ() +
                        " for set &e" + set.getName()));
            }
            case "remove" -> {
                Location target = player.getTargetBlock((java.util.Set<org.bukkit.Material>) null, 5).getLocation();
                plugin.getSetMaterialDropListener().removeNodeAt(target);
                player.sendMessage(CC.translate("&c[Glass Node] Removed at " +
                        target.getBlockX() + "," + target.getBlockY() + "," + target.getBlockZ()));
            }
            case "list" -> {
                var nodes = plugin.getSetMaterialDropListener().getNodes();
                player.sendMessage(CC.translate("&b[Glass Nodes] " + nodes.size() + " registered:"));
                nodes.forEach(n -> player.sendMessage(CC.translate(
                        "  &7" + n.getSet().getName() + " &8@ &e" +
                        n.getLocation().getBlockX() + "," +
                        n.getLocation().getBlockY() + "," +
                        n.getLocation().getBlockZ())));
            }
            case "save" -> {
                plugin.getSetMaterialDropListener().saveNodes();
                player.sendMessage(CC.translate("&a[Glass Nodes] Saved " +
                        plugin.getSetMaterialDropListener().getNodes().size() + " nodes."));
            }
            case "load" -> {
                int loaded = plugin.getSetMaterialDropListener().loadNodes();
                player.sendMessage(CC.translate("&a[Glass Nodes] Loaded &e" + loaded + " &anodes."));
            }
            default -> player.sendMessage(CC.translate("&cUnknown sub: add, remove, list, save, load"));
        }
    }

    private void handleReload(Player player) {
        plugin.getSetManager().load();
        player.sendMessage(CC.translate("&a[Sets] Reloaded &e" +
                plugin.getSetManager().getSets().size() + " &asets from sets.yml."));
    }

    private void handleList(Player player) {
        player.sendMessage(CC.translate("&8━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
        player.sendMessage(CC.translate("&b&l  ARMOR SETS (" + plugin.getSetManager().getSets().size() + ")"));
        player.sendMessage(CC.translate("&8━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
        plugin.getSetManager().getSets().values().forEach(set ->
                player.sendMessage(CC.translate("  " + set.getTier().display() +
                        " &r" + set.getColor() + set.getName() +
                        " &8| &7id: &e" + set.getId() +
                        " &8| &7tier: &e" + set.getTierRequired())));
        player.sendMessage(CC.translate("&8━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
    }

    public List<String> tabComplete(String[] args) {
        if (args.length == 2) return filter(List.of("armorstand","npc","glass","reload","list"), args[1]);
        if (args.length == 3 && args[1].equalsIgnoreCase("glass"))
            return filter(List.of("add","remove","list","save","load"), args[2]);
        if (args.length == 4 && args[1].equalsIgnoreCase("glass") && args[2].equalsIgnoreCase("add"))
            return filter(new ArrayList<>(plugin.getSetManager().getSets().keySet()), args[3]);
        if (args.length == 3 && (args[1].equalsIgnoreCase("armorstand") || args[1].equalsIgnoreCase("npc")))
            return filter(new ArrayList<>(plugin.getSetManager().getSets().keySet()), args[2]);
        return Collections.emptyList();
    }

    private List<String> filter(List<String> opts, String prefix) {
        String p = prefix.toLowerCase();
        return opts.stream().filter(s -> s.startsWith(p)).collect(Collectors.toList());
    }

    private String getSetIds() {
        return String.join(", ", plugin.getSetManager().getSets().keySet());
    }

    private void sendHelp(Player player) {
        player.sendMessage(CC.translate("&b&lSet Admin Commands:"));
        player.sendMessage(CC.translate("&e/cb set armorstand <setId> &7- Save armor stand position"));
        player.sendMessage(CC.translate("&e/cb set npc <setId> &7- Save Offers NPC position"));
        player.sendMessage(CC.translate("&e/cb set glass add <setId> &7- Register glass node at looked block"));
        player.sendMessage(CC.translate("&e/cb set glass remove &7- Remove glass node at looked block"));
        player.sendMessage(CC.translate("&e/cb set glass list/save/load &7- Manage glass nodes"));
        player.sendMessage(CC.translate("&e/cb set reload &7- Reload sets.yml"));
        player.sendMessage(CC.translate("&e/cb set list &7- List all sets"));
    }
}
