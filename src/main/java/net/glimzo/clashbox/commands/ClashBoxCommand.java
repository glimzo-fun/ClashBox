package net.glimzo.clashbox.commands;

import me.pikashrey.glimzocore.utilities.chat.CC;
import net.glimzo.clashbox.core.ClashBoxPlugin;
import net.glimzo.clashbox.progression.UpgradeType;
import net.glimzo.clashbox.sets.ArmorSet;
import net.glimzo.clashbox.sets.SetUpgradeMenu;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

public class ClashBoxCommand implements CommandExecutor, TabCompleter {

    private final ClashBoxPlugin plugin;

    private static final List<String> TOP_LEVEL = List.of(
            "reload", "ore", "cube", "arena", "set",
            "npcinteract", "portal", "give", "giveshards",
            "saveall", "sellarea", "upgrade"
    );

    public ClashBoxCommand(ClashBoxPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) { sendHelp(sender); return true; }

        String sub = args[0].toLowerCase();

        if (sub.equals("npcinteract")) {
            handleNpcInteract(sender, args);
            return true;
        }

        if (!sender.hasPermission("clashbox.admin")) {
            sender.sendMessage(CC.translate("&cNo permission.")); return true;
        }

        switch (sub) {
            case "reload" -> {
                plugin.reloadConfig();
                plugin.getZoneConfig().reload();
                plugin.getSetManager().load();
                sender.sendMessage(CC.translate("&a[ClashBox] Config, zones and sets reloaded."));
            }
            case "ore"   -> plugin.getOreAdminCommand().onCommand(sender, command, label, args);
            case "cube"  -> plugin.getOreCubeAdminCommand().handle(sender, args);
            case "arena" -> plugin.getArenaAdminCommand().handle(sender, args);
            case "set"   -> plugin.getSetAdminCommand().handle(sender, args);

            case "portal" -> {
                plugin.getPortalManager().startTask();
                sender.sendMessage(CC.translate("&a[ClashBox] Portals relocated."));
            }

            case "give", "giveshards" -> {
                if (args.length < 3) {
                    sender.sendMessage(CC.translate("&cUsage: /cb give <player> <amount>")); return true;
                }
                Player target = plugin.getServer().getPlayer(args[1]);
                if (target == null) {
                    sender.sendMessage(CC.translate("&cPlayer not found.")); return true;
                }
                try {
                    long amount = Long.parseLong(args[2]);
                    plugin.getShardEconomy().addShards(target.getUniqueId(), amount, "Admin give");
                    sender.sendMessage(CC.translate("&aGave &b◆ " + amount + " Shards &ato &f" + target.getName()));
                    target.sendMessage(CC.translate("&8[&bClashBox&8] &b◆ " + amount + " Shards &7added by admin."));
                } catch (NumberFormatException e) {
                    sender.sendMessage(CC.translate("&cInvalid amount."));
                }
            }

            case "saveall" -> {
                plugin.getProfileManager().saveAll();
                sender.sendMessage(CC.translate("&a[ClashBox] All profiles saved."));
            }

            case "sellarea" -> {
                if (!(sender instanceof Player adminPlayer)) {
                    sender.sendMessage("Must be a player."); return true;
                }
                if (args.length < 2 || !args[1].equalsIgnoreCase("set")) {
                    sender.sendMessage(CC.translate("&cUsage: /cb sellarea set")); return true;
                }
                plugin.getSellArea().setCenter(adminPlayer.getLocation());
                sender.sendMessage(CC.translate("&a[ClashBox] Sell area set at your location."));
            }

            case "upgrade" -> {
                if (args.length < 3) {
                    sender.sendMessage(CC.translate("&cUsage: /cb upgrade <player> <type>")); return true;
                }
                Player target = plugin.getServer().getPlayer(args[1]);
                if (target == null) {
                    sender.sendMessage(CC.translate("&cPlayer not found.")); return true;
                }
                try {
                    UpgradeType type = UpgradeType.valueOf(args[2].toUpperCase());
                    var result = plugin.getUpgradeManager().purchaseUpgrade(target, type);
                    sender.sendMessage(CC.translate(result.getMessage()));
                } catch (IllegalArgumentException e) {
                    sender.sendMessage(CC.translate("&cInvalid upgrade type. Options: " + Arrays.toString(UpgradeType.values())));
                }
            }

            default -> sendHelp(sender);
        }
        return true;
    }

    private void handleNpcInteract(CommandSender sender, String[] args) {
        if (args.length < 2) return;
        String category = args[1];

        if (category.startsWith("set_")) {
            if (!(sender instanceof Player player)) return;
            String setId = category.substring(4);
            ArmorSet set = plugin.getSetManager().getSet(setId);
            if (set == null) {
                sender.sendMessage(CC.translate("&cUnknown set: &e" + setId)); return;
            }
            if (!player.getWorld().getName().equals(ClashBoxPlugin.ARENA_WORLD)) {
                player.sendMessage(CC.translate("&cSet upgrades are available in the arena Core zone.")); return;
            }
            new SetUpgradeMenu(plugin, player, set).open();
            return;
        }

        plugin.getEnchantNpcCommand().handle(sender, args);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command,
                                      String alias, String[] args) {
        if (args.length == 1) {
            if (!sender.hasPermission("clashbox.admin")) return Collections.emptyList();
            List<String> opts = TOP_LEVEL.stream()
                    .filter(s -> !s.equals("npcinteract"))
                    .collect(Collectors.toList());
            return filterStart(opts, args[0]);
        }

        if (!sender.hasPermission("clashbox.admin")) return Collections.emptyList();

        if (args.length >= 2) {
            return switch (args[0].toLowerCase()) {
                case "ore"   -> plugin.getOreAdminCommand().onTabComplete(sender, command, alias, args);
                case "cube"  -> plugin.getOreCubeAdminCommand().tabComplete(args);
                case "arena" -> plugin.getArenaAdminCommand().tabComplete(args);
                case "set"   -> plugin.getSetAdminCommand().tabComplete(args);
                default      -> Collections.emptyList();
            };
        }
        return Collections.emptyList();
    }

    private void sendHelp(CommandSender s) {
        s.sendMessage(CC.translate("&b&lClashBox Admin Commands:"));
        s.sendMessage(CC.translate("&e/cb reload &7- Reload config + zones + sets"));
        s.sendMessage(CC.translate("&e/cb ore add|remove|list|save|load &7- Ore nodes"));
        s.sendMessage(CC.translate("&e/cb cube pos1|setpos|set|remove|list|fill|interval|save|load &7- Ore cubes"));
        s.sendMessage(CC.translate("&e/cb arena pos1|setregion|resnap|info &7- Arena block region"));
        s.sendMessage(CC.translate("&e/cb set armorstand|npc|glass|reload|list &7- Armor sets"));
        s.sendMessage(CC.translate("&e/cb portal &7- Force portal relocation"));
        s.sendMessage(CC.translate("&e/cb give <player> <amount> &7- Give shards"));
        s.sendMessage(CC.translate("&e/cb saveall &7- Force save all profiles"));
        s.sendMessage(CC.translate("&e/cb sellarea set &7- Set sell area position"));
        s.sendMessage(CC.translate("&e/cb upgrade <player> <type> &7- Admin-force upgrade"));
    }

    private List<String> filterStart(List<String> opts, String prefix) {
        String p = prefix.toLowerCase();
        return opts.stream().filter(s -> s.startsWith(p)).collect(Collectors.toList());
    }
}
