package net.glimzo.clashbox.commands;

import me.pikashrey.glimzocore.utilities.chat.CC;
import net.glimzo.clashbox.arena.OreCube;
import net.glimzo.clashbox.arena.OreCubeManager;
import net.glimzo.clashbox.arena.OreType;
import net.glimzo.clashbox.core.ClashBoxPlugin;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

public class OreCubeAdminCommand {

    private final ClashBoxPlugin plugin;

    private final Map<UUID, Location> pos1Map = new HashMap<>();
    private final Map<UUID, Location> pos2Map = new HashMap<>();
    private final Map<UUID, Map<String, Location[]>> pendingCubes = new HashMap<>();

    public OreCubeAdminCommand(ClashBoxPlugin plugin) {
        this.plugin = plugin;
    }

    public void handle(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(CC.translate("&cMust be a player."));
            return;
        }

        if (args.length < 2) { sendHelp(player); return; }

        String sub = args[1].toLowerCase();

        switch (sub) {
            case "pos1"     -> handlePos1(player, args);
            case "pos2"     -> handlePos2(player, args);
            case "remove"   -> handleRemove(player, args);
            case "list"     -> handleList(player);
            case "fill"     -> handleFill(player, args);
            case "interval" -> handleInterval(player, args);
            case "save"     -> handleSave(player);
            case "load"     -> handleLoad(player);
            default -> {
                if (args.length >= 3) {
                    handleRegister(player, args[1], args[2]);
                } else {
                    sendHelp(player);
                }
            }
        }
    }

    private void handlePos1(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(CC.translate("&cUsage: &e/cb cube pos1 <id>"));
            return;
        }
        String   id  = args[2].toLowerCase();
        Location loc = player.getLocation().getBlock().getLocation();

        pendingCubes
                .computeIfAbsent(player.getUniqueId(), k -> new HashMap<>())
                .compute(id, (k, v) -> {
                    Location[] arr = v != null ? v : new Location[2];
                    arr[0] = loc;
                    return arr;
                });

        player.sendMessage(CC.translate(
                "&a[Cube] &7Pos1 for &e'" + id + "' &7set to &e" +
                loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ() +
                "&7. Now go to the opposite corner and run &e/cb cube pos2 " + id + "&7."));
    }

    private void handlePos2(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(CC.translate("&cUsage: &e/cb cube pos2 <id>"));
            return;
        }
        String   id  = args[2].toLowerCase();
        Location loc = player.getLocation().getBlock().getLocation();

        Map<String, Location[]> playerPending = pendingCubes
                .computeIfAbsent(player.getUniqueId(), k -> new HashMap<>());

        Location[] arr = playerPending.get(id);
        if (arr == null || arr[0] == null) {
            player.sendMessage(CC.translate(
                    "&cNo pos1 set for &e'" + id + "'&c. Run &e/cb cube pos1 " + id + " &cfirst."));
            return;
        }

        arr[1] = loc;

        player.sendMessage(CC.translate(
                "&a[Cube] &7Pos2 for &e'" + id + "' &7set to &e" +
                loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ() +
                "&7. Now run &e/cb cube " + id + " <type> &7to register."));
        player.sendMessage(CC.translate("&7Types: " + getTypeList()));
    }

    private void handleRegister(Player player, String id, String typeName) {
        OreType oreType = OreType.fromString(typeName);
        if (oreType == null) {
            player.sendMessage(CC.translate("&cUnknown ore type: &e" + typeName));
            player.sendMessage(CC.translate("&7Valid types: " + getTypeList()));
            return;
        }

        Map<String, Location[]> playerPending = pendingCubes.get(player.getUniqueId());
        Location[] corners = playerPending != null ? playerPending.get(id.toLowerCase()) : null;

        if (corners == null || corners[0] == null || corners[1] == null) {
            player.sendMessage(CC.translate(
                    "&cMissing corners for &e'" + id + "'&c. " +
                    "Run &e/cb cube pos1 " + id + " &cand &e/cb cube pos2 " + id + " &cfirst."));
            return;
        }

        plugin.getOreCubeManager().registerCube(id.toLowerCase(), oreType, corners[0], corners[1]);
        playerPending.remove(id.toLowerCase());

        Location mn = corners[0];
        Location mx = corners[1];
        player.sendMessage(CC.translate(
                "&a&l[Ore Cube] &r&aRegistered &e'" + id + "' &7(" + oreType.getDisplayName() +
                "&7) from &e" + mn.getBlockX()+","+mn.getBlockY()+","+mn.getBlockZ() +
                " &7to &e" + mx.getBlockX()+","+mx.getBlockY()+","+mx.getBlockZ()));
        player.sendMessage(CC.translate(
                "&7Cube filled and will refill every &e" +
                plugin.getOreCubeManager().getRefillIntervalSeconds() + "s&7."));
    }

    private void handleRemove(Player player, String[] args) {
        if (args.length < 3) { player.sendMessage(CC.translate("&cUsage: &e/cb cube remove <id>")); return; }
        String id = args[2].toLowerCase();
        if (plugin.getOreCubeManager().removeCube(id)) {
            player.sendMessage(CC.translate("&c[Ore Cube] &r&cRemoved cube '&e" + id + "&c'. Blocks NOT cleared."));
        } else {
            player.sendMessage(CC.translate("&cNo cube with id '&e" + id + "&c'."));
        }
    }

    private void handleList(Player player) {
        Collection<OreCube> cubes = plugin.getOreCubeManager().getCubes();
        player.sendMessage(CC.translate("&8━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
        player.sendMessage(CC.translate("&b&l  ORE CUBES (" + cubes.size() + " registered)"));
        player.sendMessage(CC.translate("&7  Refill: &e" + plugin.getOreCubeManager().getRefillIntervalSeconds() + "s"));
        player.sendMessage(CC.translate("&8━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
        if (cubes.isEmpty()) {
            player.sendMessage(CC.translate("&7  None. Use: &e/cb cube pos1 <id> &7-> &e/cb cube pos2 <id> &7-> &e/cb cube <id> <type>"));
        } else {
            for (OreCube cube : cubes) {
                Location mc  = cube.getMinCorner();
                long secsLeft = Math.max(0, (cube.getNextRefillAt() - System.currentTimeMillis()) / 1000L);
                player.sendMessage(CC.translate(
                        "  &e" + cube.getId() + " &8| " + cube.getOreType().getDisplayName() +
                        " &8| &7" + mc.getBlockX()+","+mc.getBlockY()+","+mc.getBlockZ() +
                        " &8| &7refill &a" + secsLeft + "s"));
            }
        }
        player.sendMessage(CC.translate("&8━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
    }

    private void handleFill(Player player, String[] args) {
        if (args.length < 3) { player.sendMessage(CC.translate("&cUsage: &e/cb cube fill <id|all>")); return; }
        OreCubeManager mgr = plugin.getOreCubeManager();
        if (args[2].equalsIgnoreCase("all")) {
            int n = 0;
            for (OreCube cube : mgr.getCubes()) { cube.fill(); cube.scheduleRefill(mgr.getRefillIntervalSeconds()); n++; }
            player.sendMessage(CC.translate("&a[Ore Cube] Force-refilled &e" + n + "&a cube(s)."));
        } else {
            boolean ok = mgr.forceRefill(args[2].toLowerCase());
            player.sendMessage(ok
                    ? CC.translate("&a[Ore Cube] Refilled '&e" + args[2] + "&a'.")
                    : CC.translate("&cNo cube '&e" + args[2] + "&c'."));
        }
    }

    private void handleInterval(Player player, String[] args) {
        if (args.length < 3) { player.sendMessage(CC.translate("&cUsage: &e/cb cube interval <seconds>")); return; }
        try {
            long secs = Long.parseLong(args[2]);
            if (secs < 5) { player.sendMessage(CC.translate("&cMinimum 5 seconds.")); return; }
            plugin.getOreCubeManager().setRefillIntervalSeconds(secs);
            plugin.getOreCubeManager().stopTask();
            plugin.getOreCubeManager().startTask();
            player.sendMessage(CC.translate("&a[Ore Cube] Interval set to &e" + secs + "s&a. Run &e/cb cube save&a to persist."));
        } catch (NumberFormatException e) {
            player.sendMessage(CC.translate("&cMust be a whole number."));
        }
    }

    private void handleSave(Player player) {
        plugin.getOreCubeManager().saveCubes();
        player.sendMessage(CC.translate("&a[Ore Cube] Saved " + plugin.getOreCubeManager().getCubes().size() + " cube(s)."));
    }

    private void handleLoad(Player player) {
        plugin.getOreCubeManager().stopTask();
        int n = plugin.getOreCubeManager().loadCubes();
        plugin.getOreCubeManager().startTask();
        player.sendMessage(CC.translate("&a[Ore Cube] Loaded &e" + n + "&a cube(s)."));
    }

    public List<String> tabComplete(String[] args) {
        if (args.length == 2) {
            List<String> opts = new ArrayList<>(List.of("pos1","pos2","remove","list","fill","interval","save","load"));
            plugin.getOreCubeManager().getCubes().forEach(c -> opts.add(c.getId()));
            return filterStart(opts, args[1]);
        }
        if (args.length == 3) {
            String sub = args[1].toLowerCase();
            if (sub.equals("remove") || sub.equals("fill")) {
                List<String> ids = new ArrayList<>(plugin.getOreCubeManager().getCubes().stream().map(OreCube::getId).collect(Collectors.toList()));
                if (sub.equals("fill")) ids.add("all");
                return filterStart(ids, args[2]);
            }
            if (sub.equals("pos1") || sub.equals("pos2")) {
                return filterStart(plugin.getOreCubeManager().getCubes().stream().map(OreCube::getId).collect(Collectors.toList()), args[2]);
            }
            return filterStart(Arrays.stream(OreType.values()).map(t -> t.name().toLowerCase()).collect(Collectors.toList()), args[2]);
        }
        return Collections.emptyList();
    }

    private void sendHelp(Player player) {
        player.sendMessage(CC.translate("&b&lOre Cube Setup:"));
        player.sendMessage(CC.translate("&7Step 1: &e/cb cube pos1 <id> &7- stand at first corner"));
        player.sendMessage(CC.translate("&7Step 2: &e/cb cube pos2 <id> &7- stand at second corner"));
        player.sendMessage(CC.translate("&7Step 3: &e/cb cube <id> <type> &7- register it"));
        player.sendMessage(CC.translate("&b&lOther:"));
        player.sendMessage(CC.translate("&e/cb cube remove <id> &7- unregister"));
        player.sendMessage(CC.translate("&e/cb cube list &7- list all"));
        player.sendMessage(CC.translate("&e/cb cube fill <id|all> &7- force refill"));
        player.sendMessage(CC.translate("&e/cb cube interval <s> &7- change refill interval"));
        player.sendMessage(CC.translate("&e/cb cube save/load &7- persist / reload"));
        player.sendMessage(CC.translate("&7Types: " + getTypeList()));
    }

    private String getTypeList() {
        return Arrays.stream(OreType.values()).map(OreType::getDisplayName).collect(Collectors.joining("&7, "));
    }

    private List<String> filterStart(List<String> opts, String prefix) {
        String p = prefix.toLowerCase();
        return opts.stream().filter(s -> s.toLowerCase().startsWith(p)).collect(Collectors.toList());
    }
}
