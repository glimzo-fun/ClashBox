package net.glimzo.clashbox.arena;

import net.glimzo.clashbox.core.ClashBoxPlugin;
import net.glimzo.clashbox.ui.ParticleUtil;
import net.glimzo.clashbox.zone.ZoneType;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

import java.util.*;
import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;
import net.glimzo.clashbox.player.ClashBoxProfile;

public class OreRegenerationManager implements Listener {

    private final ClashBoxPlugin plugin;
    private final List<OreNode> nodes = new ArrayList<>();
    private int taskId = -1;

    private static final List<Material> OUTER_ORES = Arrays.asList(
            Material.COAL_ORE, Material.IRON_ORE
    );
    private static final List<Material> MID_ORES = Arrays.asList(
            Material.COAL_ORE, Material.IRON_ORE, Material.GOLD_ORE,
            Material.LAPIS_ORE, Material.REDSTONE_ORE
    );
    private static final List<Material> CORE_ORES = Arrays.asList(
            Material.GOLD_ORE, Material.LAPIS_ORE, Material.REDSTONE_ORE,
            Material.DIAMOND_ORE, Material.EMERALD_ORE
    );

    private static final double[] OUTER_WEIGHTS = {0.70, 0.30};
    private static final double[] MID_WEIGHTS   = {0.35, 0.30, 0.15, 0.12, 0.08};
    private static final double[] CORE_WEIGHTS  = {0.20, 0.20, 0.20, 0.25, 0.15};

    public OreRegenerationManager(ClashBoxPlugin plugin) {
        this.plugin = plugin;
    }

    public void registerNode(Location loc, ZoneType zone) {
        Material initial = pickOre(zone);
        OreNode node = new OreNode(loc, zone, initial);
        nodes.add(node);
        loc.getBlock().setType(initial);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block   = event.getBlock();

        OreNode node = getNodeAt(block.getLocation());
        if (node == null) return;
        if (node.isMined()) return;

        ZoneType zone = plugin.getZoneManager().detectZone(block.getLocation());

        Material oreType = node.getCurrentOre();
        if (oreType != null) {
            ParticleUtil.blockBreak(block.getLocation(), oreType);
        }

        node.setCurrentOre(null);
        block.setType(Material.STONE);

        int minDelay = plugin.getCBConfig().getOreRegenMin(zone.configKey());
        int maxDelay = plugin.getCBConfig().getOreRegenMax(zone.configKey());
        int delay = ThreadLocalRandom.current().nextInt(minDelay, maxDelay + 1);
        node.setRespawnAt(System.currentTimeMillis() + (delay * 1000L));
    }

    public void startTask() {
        int interval = plugin.getCBConfig().getOreRegenCheckInterval();
        taskId = plugin.getServer().getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            for (OreNode node : nodes) {
                if (!node.isReadyToSpawn()) continue;
                Material newOre = pickOre(node.getZone());
                node.setCurrentOre(newOre);
                node.getLocation().getBlock().setType(newOre);
                ParticleUtil.happyVillager(node.getLocation().clone().add(0.5, 0.8, 0.5), 6);
            }
        }, interval, interval);
    }

    public void stopTask() {
        if (taskId != -1) {
            plugin.getServer().getScheduler().cancelTask(taskId);
            taskId = -1;
        }
    }

    private long calculateSellValue(Material ore, ZoneType zone, Player player) {
        Map<Material, Long> basePrices = plugin.getCBConfig().getOreBasePrices();
        long base = basePrices.getOrDefault(ore, 10L);
        double zoneMultiplier = plugin.getCBConfig().getOreZoneMultiplier(zone.configKey());
        long rawValue = (long)(base * zoneMultiplier);
        return applySoftcap(rawValue, player);
    }

    private long applySoftcap(long value, Player player) {
        ClashBoxProfile profile = plugin.getProfileManager().getProfile(player);
        if (profile == null) return value;

        long cap = plugin.getCBConfig().getSellHourlySoftcap();
        long now = System.currentTimeMillis();

        if ((now - profile.getSessionSellWindowStart()) > 3_600_000L) {
            profile.resetSessionSellTracking();
        }

        if (profile.getSessionSoldValue() >= cap) {
            double reduction = plugin.getCBConfig().getSellSoftcapReduction();
            return (long)(value * reduction);
        }

        return value;
    }

    private OreNode getNodeAt(Location loc) {
        for (OreNode node : nodes) {
            Location nodeLoc = node.getLocation();
            if (nodeLoc.getBlockX() == loc.getBlockX()
                    && nodeLoc.getBlockY() == loc.getBlockY()
                    && nodeLoc.getBlockZ() == loc.getBlockZ()
                    && nodeLoc.getWorld().equals(loc.getWorld())) {
                return node;
            }
        }
        return null;
    }

    private Material pickOre(ZoneType zone) {
        List<Material> pool;
        double[] weights;
        switch (zone) {
            case CORE  -> { pool = CORE_ORES;  weights = CORE_WEIGHTS;  }
            case MID   -> { pool = MID_ORES;   weights = MID_WEIGHTS;   }
            default    -> { pool = OUTER_ORES; weights = OUTER_WEIGHTS; }
        }
        double roll = ThreadLocalRandom.current().nextDouble();
        double cumulative = 0;
        for (int i = 0; i < pool.size(); i++) {
            cumulative += weights[i];
            if (roll <= cumulative) return pool.get(i);
        }
        return pool.get(pool.size() - 1);
    }

    public List<OreNode> getNodes() { return nodes; }
}
