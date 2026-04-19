package net.glimzo.clashbox.commands;

import net.glimzo.clashbox.arena.OreNode;
import net.glimzo.clashbox.arena.OreRegenerationManager;
import net.glimzo.clashbox.core.ClashBoxPlugin;
import net.glimzo.clashbox.zone.ZoneType;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import me.pikashrey.glimzocore.utilities.chat.CC;

public class OreAdminCommand implements CommandExecutor, TabCompleter {

    private final ClashBoxPlugin plugin;
    private static final int REACH = 5;

    public OreAdminCommand(ClashBoxPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("clashbox.admin")) {
            sender.sendMessage(CC.translate("&cNo permission.")); return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(CC.translate("&cMust be a player.")); return true;
        }

        if (args.length < 2) { sendHelp(player); return true; }

        switch (args[1].toLowerCase()) {
            case "add"    -> handleAdd(player, args);
            case "remove" -> handleRemove(player);
            case "list"   -> handleList(player);
            case "clear"  -> handleClear(player, args);
            case "save"   -> handleSave(player);
            case "load"   -> handleLoad(player);
            default       -> sendHelp(player);
        }
        return true;
    }

    private void handleAdd(Player player, String[] args) {
        Block target = player.getTargetBlock((java.util.Set<org.bukkit.Material>) null, REACH);
        if (target == null || target.getType() == Material.AIR) {
            player.sendMessage("&cYou must be looking at a block (within " + REACH + " blocks).");
            return;
        }

        ZoneType zone;
        if (args.length >= 3) {
            try {
                zone = ZoneType.valueOf(args[2].toUpperCase());
            } catch (IllegalArgumentException e) {
                player.sendMessage(CC.translate("&cInvalid zone. Use: OUTER, MID, CORE"));
                return;
            }
        } else {
            zone = plugin.getZoneManager().detectZone(target.getLocation());
        }

        Location loc = target.getLocation();
        OreRegenerationManager oreManager = plugin.getOreRegenManager();

        for (OreNode node : oreManager.getNodes()) {
            Location nl = node.getLocation();
            if (nl.getBlockX() == loc.getBlockX()
                    && nl.getBlockY() == loc.getBlockY()
                    && nl.getBlockZ() == loc.getBlockZ()) {
                player.sendMessage("&eA node already exists at that block (" +
                        node.getZone().displayName() + "&e). Remove it first.");
                return;
            }
        }

        oreManager.registerNode(loc, zone);

        player.sendMessage("&a&l[Ore Node] &r&aRegistered at " +
                formatLoc(loc) + " &7in " + zone.displayName());
        player.sendMessage(CC.translate("&7The block has been converted to a zone-appropriate ore."));
        player.sendMessage("&7Total nodes: &e" + oreManager.getNodes().size());
    }

    private void handleRemove(Player player) {
        Block target = player.getTargetBlock((java.util.Set<org.bukkit.Material>) null, REACH);
        if (target == null || target.getType() == Material.AIR) {
            player.sendMessage(CC.translate("&cYou must be looking at a block."));
            return;
        }

        Location loc = target.getLocation();
        OreRegenerationManager oreManager = plugin.getOreRegenManager();
        List<OreNode> nodes = oreManager.getNodes();

        OreNode toRemove = null;
        for (OreNode node : nodes) {
            Location nl = node.getLocation();
            if (nl.getBlockX() == loc.getBlockX()
                    && nl.getBlockY() == loc.getBlockY()
                    && nl.getBlockZ() == loc.getBlockZ()) {
                toRemove = node;
                break;
            }
        }

        if (toRemove == null) {
            player.sendMessage(CC.translate("&cNo ore node registered at that block."));
            return;
        }

        nodes.remove(toRemove);
        target.setType(Material.STONE);
        player.sendMessage("&c&l[Ore Node] &r&cRemoved node at " + formatLoc(loc));
        player.sendMessage("&7Remaining nodes: &e" + nodes.size());
    }

    private void handleList(Player player) {
        List<OreNode> nodes = plugin.getOreRegenManager().getNodes();
        player.sendMessage(CC.translate("&8━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
        player.sendMessage("&b&l  ORE NODES (" + nodes.size() + " total)");
        player.sendMessage(CC.translate("&8━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));

        if (nodes.isEmpty()) {
            player.sendMessage(CC.translate("&7  None registered yet. Use &e/cb ore add &7while looking at a block."));
            player.sendMessage(CC.translate("&8━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
            return;
        }

        for (ZoneType zone : ZoneType.values()) {
            long count = nodes.stream().filter(n -> n.getZone() == zone).count();
            if (count == 0) continue;
            player.sendMessage(zone.displayName() + " &8(" + count + ")");
            nodes.stream()
                    .filter(n -> n.getZone() == zone)
                    .limit(10)
                    .forEach(n -> {
                        String status = n.isMined() ? "&c[mined]" : "&a[active]";
                        player.sendMessage("  &8- &7" + formatLoc(n.getLocation()) + " " + status);
                    });
            if (count > 10) {
                player.sendMessage("  &8...and &7" + (count - 10) + " &8more.");
            }
        }
        player.sendMessage(CC.translate("&8━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
    }

    private void handleClear(Player player, String[] args) {
        boolean confirmed = args.length >= 3 && args[2].equalsIgnoreCase("--confirm");
        if (!confirmed) {
            player.sendMessage("&c&lWARNING: &r&cThis removes ALL " +
                    plugin.getOreRegenManager().getNodes().size() + " registered ore nodes!");
            player.sendMessage(CC.translate("&7Run &e/cb ore clear --confirm &7to proceed."));
            return;
        }
        int count = plugin.getOreRegenManager().getNodes().size();
        plugin.getOreRegenManager().getNodes().clear();
        player.sendMessage("&c[Ore Nodes] Cleared &e" + count + " &cnodes.");
    }

    private void handleSave(Player player) {
        saveNodes();
        player.sendMessage("&a[Ore Nodes] Saved &e" +
                plugin.getOreRegenManager().getNodes().size() + " &anodes to &fnodes.yml&a.");
    }

    private void handleLoad(Player player) {
        int loaded = loadNodes();
        player.sendMessage("&a[Ore Nodes] Loaded &e" + loaded + " &anodes from &fnodes.yml&a.");
    }

    public void saveNodes() {
        File file = new File(plugin.getDataFolder(), "nodes.yml");
        org.bukkit.configuration.file.YamlConfiguration cfg =
                new org.bukkit.configuration.file.YamlConfiguration();

        List<OreNode> nodes = plugin.getOreRegenManager().getNodes();
        for (int i = 0; i < nodes.size(); i++) {
            OreNode node = nodes.get(i);
            String path  = "nodes." + i;
            cfg.set(path + ".world", node.getLocation().getWorld().getName());
            cfg.set(path + ".x",     node.getLocation().getBlockX());
            cfg.set(path + ".y",     node.getLocation().getBlockY());
            cfg.set(path + ".z",     node.getLocation().getBlockZ());
            cfg.set(path + ".zone",  node.getZone().name());
        }

        try {
            cfg.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("[ClashBox] Failed to save nodes.yml: " + e.getMessage());
        }
    }

    public int loadNodes() {
        File file = new File(plugin.getDataFolder(), "nodes.yml");
        if (!file.exists()) return 0;

        org.bukkit.configuration.file.YamlConfiguration cfg =
                org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file);

        plugin.getOreRegenManager().getNodes().clear();

        if (!cfg.contains("nodes")) return 0;

        int count = 0;
        for (String key : cfg.getConfigurationSection("nodes").getKeys(false)) {
            String path      = "nodes." + key;
            String worldName = cfg.getString(path + ".world");
            int x = cfg.getInt(path + ".x");
            int y = cfg.getInt(path + ".y");
            int z = cfg.getInt(path + ".z");
            String zoneName  = cfg.getString(path + ".zone", "OUTER");

            org.bukkit.World world = plugin.getServer().getWorld(worldName);
            if (world == null) continue;

            try {
                ZoneType zone = ZoneType.valueOf(zoneName);
                Location loc  = new Location(world, x, y, z);
                plugin.getOreRegenManager().registerNode(loc, zone);
                count++;
            } catch (IllegalArgumentException ignored) {}
        }
        return count;
    }

    private String formatLoc(Location loc) {
        return "&8(&7" + loc.getBlockX() + ", " + loc.getBlockY() +
                ", " + loc.getBlockZ() + "&8)";
    }

    private void sendHelp(Player player) {
        player.sendMessage(CC.translate("&b&lOre Node Commands:"));
        player.sendMessage(CC.translate("&e/cb ore add [OUTER|MID|CORE] &7- Register looked-at block"));
        player.sendMessage(CC.translate("&e/cb ore remove &7- Remove looked-at node"));
        player.sendMessage(CC.translate("&e/cb ore list &7- List all nodes"));
        player.sendMessage(CC.translate("&e/cb ore clear --confirm &7- Remove all nodes"));
        player.sendMessage(CC.translate("&e/cb ore save &7- Save nodes to nodes.yml"));
        player.sendMessage(CC.translate("&e/cb ore load &7- Reload nodes from nodes.yml"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command,
                                      String alias, String[] args) {
        if (args.length == 2) {
            return Arrays.asList("add", "remove", "list", "clear", "save", "load")
                    .stream()
                    .filter(s -> s.startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }
        if (args.length == 3 && args[1].equalsIgnoreCase("add")) {
            return Arrays.stream(ZoneType.values())
                    .map(ZoneType::name)
                    .filter(s -> s.startsWith(args[2].toUpperCase()))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}
