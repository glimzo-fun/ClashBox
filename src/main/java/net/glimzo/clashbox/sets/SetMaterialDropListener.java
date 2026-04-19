package net.glimzo.clashbox.sets;

import me.pikashrey.glimzocore.utilities.chat.CC;
import net.glimzo.clashbox.core.ClashBoxPlugin;
import net.glimzo.clashbox.zone.ZoneType;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.configuration.file.YamlConfiguration;

public class SetMaterialDropListener implements Listener {

    private static final String GLASS_NODES_FILE  = "glass-nodes.yml";
    private static final int    REGEN_MIN_SECONDS = 20;
    private static final int    REGEN_MAX_SECONDS = 60;

    private final ClashBoxPlugin plugin;
    private final List<GlassNode> nodes = new ArrayList<>();

    public SetMaterialDropListener(ClashBoxPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block  block  = event.getBlock();

        if (!plugin.getPlayerStateManager().isAliveInArena(player)) return;

        GlassNode node = getNodeAt(block.getLocation());
        if (node == null) return;

        ZoneType playerZone = plugin.getZoneManager().detectZone(block.getLocation());
        ArmorSet set        = node.getSet();

        boolean allowed = false;
        for (String zone : set.getGlassDropZones()) {
            try {
                if (ZoneType.valueOf(zone) == playerZone) { allowed = true; break; }
            } catch (IllegalArgumentException ignored) {}
        }

        if (!allowed) {
            event.setCancelled(true);
            player.sendMessage(CC.translate("&cThis glass can only be mined in: &e" +
                    String.join(", ", set.getGlassDropZones())));
            return;
        }

        event.setExpToDrop(0);

        node.setCurrentMaterial(null);
        block.setType(Material.STONE);

        int delay = ThreadLocalRandom.current().nextInt(REGEN_MIN_SECONDS, REGEN_MAX_SECONDS + 1);
        node.setRespawnAt(System.currentTimeMillis() + (delay * 1000L));

        Location dropLoc = block.getLocation().add(0.5, 0.5, 0.5);
        ItemStack drop   = plugin.getSetManager().buildGlassItem(set);
        Item dropped     = player.getWorld().dropItem(dropLoc, drop);
        dropped.setPickupDelay(0);

        player.sendMessage(CC.translate("&7Mined &e1x " + set.getColor() + set.getName() +
                " Glass&7. Trade at the &bOffers NPC&7."));
    }

    public void startRegenTask() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (GlassNode node : nodes) {
                if (!node.isReadyToSpawn()) continue;
                Material glass = node.getSet().getGlassMaterial();
                if (glass == Material.AIR) continue;
                node.setCurrentMaterial(glass);
                Block block = node.getLocation().getBlock();
                block.setType(glass);
                block.setData((byte) node.getSet().getGlassDamage());
            }
        }, 40L, 40L);
    }

    public void registerNode(Location loc, ArmorSet set) {
        Material glass = set.getGlassMaterial();
        GlassNode node = new GlassNode(loc, set, glass);
        nodes.add(node);
        loc.getBlock().setType(glass);
        loc.getBlock().setData((byte) set.getGlassDamage());
    }

    public void removeNodeAt(Location loc) {
        nodes.removeIf(n -> locEquals(n.getLocation(), loc));
    }

    public List<GlassNode> getNodes() { return nodes; }

    public void saveNodes() {
        File file = new File(plugin.getDataFolder(), GLASS_NODES_FILE);
        YamlConfiguration cfg = new YamlConfiguration();
        for (int i = 0; i < nodes.size(); i++) {
            GlassNode n = nodes.get(i);
            String path = "nodes." + i;
            cfg.set(path + ".set",   n.getSet().getId());
            cfg.set(path + ".world", n.getLocation().getWorld().getName());
            cfg.set(path + ".x",     n.getLocation().getBlockX());
            cfg.set(path + ".y",     n.getLocation().getBlockY());
            cfg.set(path + ".z",     n.getLocation().getBlockZ());
        }
        try { cfg.save(file); }
        catch (IOException e) {
            plugin.getLogger().severe("[ClashBox] Failed to save glass-nodes.yml: " + e.getMessage());
        }
    }

    public int loadNodes() {
        nodes.clear();
        File file = new File(plugin.getDataFolder(), GLASS_NODES_FILE);
        if (!file.exists()) return 0;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        if (!cfg.contains("nodes")) return 0;
        int count = 0;
        for (String key : cfg.getConfigurationSection("nodes").getKeys(false)) {
            String path  = "nodes." + key;
            String setId = cfg.getString(path + ".set");
            String world = cfg.getString(path + ".world");
            int x = cfg.getInt(path + ".x");
            int y = cfg.getInt(path + ".y");
            int z = cfg.getInt(path + ".z");
            ArmorSet set   = plugin.getSetManager().getSet(setId);
            org.bukkit.World w = plugin.getServer().getWorld(world);
            if (set == null || w == null) continue;
            Location loc = new Location(w, x, y, z);
            registerNode(loc, set);
            count++;
        }
        return count;
    }

    private GlassNode getNodeAt(Location loc) {
        for (GlassNode n : nodes) {
            if (locEquals(n.getLocation(), loc)) return n;
        }
        return null;
    }

    private boolean locEquals(Location a, Location b) {
        return a.getBlockX() == b.getBlockX()
            && a.getBlockY() == b.getBlockY()
            && a.getBlockZ() == b.getBlockZ()
            && a.getWorld().equals(b.getWorld());
    }

    public static class GlassNode {
        private final Location location;
        private final ArmorSet set;
        private Material currentMaterial;
        private long     respawnAt;

        public GlassNode(Location location, ArmorSet set, Material initial) {
            this.location        = location;
            this.set             = set;
            this.currentMaterial = initial;
            this.respawnAt       = 0L;
        }

        public boolean isReadyToSpawn() {
            return currentMaterial == null && System.currentTimeMillis() >= respawnAt;
        }

        public Location getLocation()              { return location; }
        public ArmorSet getSet()                   { return set; }
        public Material getCurrentMaterial()       { return currentMaterial; }
        public void     setCurrentMaterial(Material m) { this.currentMaterial = m; }
        public long     getRespawnAt()             { return respawnAt; }
        public void     setRespawnAt(long ts)      { this.respawnAt = ts; }
    }
}
