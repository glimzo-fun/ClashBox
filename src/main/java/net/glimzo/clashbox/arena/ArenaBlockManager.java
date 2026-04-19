package net.glimzo.clashbox.arena;

import me.pikashrey.glimzocore.utilities.chat.CC;
import net.glimzo.clashbox.core.ClashBoxPlugin;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ArenaBlockManager implements Listener {

    private static final String ARENA_WORLD = "box";

    private final ClashBoxPlugin plugin;

    // player-placed blocks this session. resets on restart which is intentional -
    // blocks from last session should be treated as arena blocks again
    private final Set<Long> playerPlaced = ConcurrentHashMap.newKeySet();

    public ArenaBlockManager(ClashBoxPlugin plugin) {
        this.plugin = plugin;
    }

    public void loadAndSnapshot() {
        plugin.getLogger().info("[ClashBox] ArenaBlockManager ready - world 'box' is fully protected.");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Player player = event.getPlayer();

        if (!block.getWorld().getName().equals(ARENA_WORLD)) return;

        // let the ore system handle mineable blocks for arena players
        boolean inArena = plugin.getPlayerStateManager().isAliveInArena(player)
                || block.getWorld().getName().equals(ARENA_WORLD);
        if (inArena && isMineable(block.getType())) {
            return;
        }

        long key = pack(block.getX(), block.getY(), block.getZ());

        if (playerPlaced.contains(key)) {
            playerPlaced.remove(key);
            return;
        }

        event.setCancelled(true);
    }

    private boolean isMineable(org.bukkit.Material mat) {
        return switch (mat) {
            case COAL_ORE, IRON_ORE, GOLD_ORE, LAPIS_ORE,
                 REDSTONE_ORE, DIAMOND_ORE, EMERALD_ORE,
                 LOG, LOG_2 -> true;
            default -> {
                for (OreType type : OreType.values()) {
                    if (mat == type.getOreMaterial() || mat == type.getBlockMaterial()) yield true;
                }
                yield false;
            }
        };
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Block block = event.getBlock();
        if (!block.getWorld().getName().equals(ARENA_WORLD)) return;
        playerPlaced.add(pack(block.getX(), block.getY(), block.getZ()));
    }

    public void saveRegion(Location corner1, Location corner2) {
        plugin.getLogger().info("[ClashBox] ArenaBlockManager: saveRegion() called but no region is needed.");
    }

    public void retakeSnapshot(Runnable onComplete) {
        plugin.getLogger().info("[ClashBox] ArenaBlockManager: no snapshot to retake.");
        if (onComplete != null) onComplete.run();
    }

    public boolean isRegionLoaded()       { return true; }
    public int     getSnapshotSize()      { return 0; }
    public int     getPlayerPlacedCount() { return playerPlaced.size(); }

    public String getRegionSummary() {
        return "Entire world 'box' protected (" +
                playerPlaced.size() + " player-placed blocks this session)";
    }

    private static long pack(int x, int y, int z) {
        return ((long)(x + 512000) & 0x1FFFFFL) << 42
                | ((long)(y + 64)     & 0xFFFL)    << 30
                | ((long)(z + 512000) & 0x1FFFFFL) <<  9;
    }
}
