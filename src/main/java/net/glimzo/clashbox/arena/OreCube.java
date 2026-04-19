package net.glimzo.clashbox.arena;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class OreCube {

    public static final int SIZE = 9;
    public static final int TOTAL_BLOCKS = SIZE * SIZE * SIZE; // 729
    public static final double ORE_RATIO = 0.85;
    public static final int ORE_COUNT = (int) Math.floor(TOTAL_BLOCKS * ORE_RATIO);
    public static final int BLOCK_COUNT = TOTAL_BLOCKS - ORE_COUNT;

    public static final double NOTIFY_RADIUS_SQ = 20.0 * 20.0;

    private final String   id;
    private final OreType  oreType;
    private final Location minCorner;
    private long nextRefillAt;

    public OreCube(String id, OreType oreType, Location minCorner) {
        this.id           = id;
        this.oreType      = oreType;
        this.minCorner    = minCorner.clone();
        this.nextRefillAt = 0L;
    }

    public void fill() {
        World world = minCorner.getWorld();
        if (world == null) return;

        List<Material> materials = new ArrayList<>(TOTAL_BLOCKS);
        for (int i = 0; i < ORE_COUNT;   i++) materials.add(oreType.getOreMaterial());
        for (int i = 0; i < BLOCK_COUNT; i++) materials.add(oreType.getBlockMaterial());
        Collections.shuffle(materials, ThreadLocalRandom.current());

        int index = 0;
        for (int x = 0; x < SIZE; x++) {
            for (int y = 0; y < SIZE; y++) {
                for (int z = 0; z < SIZE; z++) {
                    world.getBlockAt(
                            minCorner.getBlockX() + x,
                            minCorner.getBlockY() + y,
                            minCorner.getBlockZ() + z
                    ).setType(materials.get(index++));
                }
            }
        }
    }

    public boolean contains(Location loc) {
        if (!loc.getWorld().equals(minCorner.getWorld())) return false;
        int bx = loc.getBlockX(), by = loc.getBlockY(), bz = loc.getBlockZ();
        int mx = minCorner.getBlockX(), my = minCorner.getBlockY(), mz = minCorner.getBlockZ();
        return bx >= mx && bx < mx + SIZE
                && by >= my && by < my + SIZE
                && bz >= mz && bz < mz + SIZE;
    }

    public Location getCenter() {
        return new Location(
                minCorner.getWorld(),
                minCorner.getX() + SIZE / 2.0,
                minCorner.getY() + SIZE / 2.0,
                minCorner.getZ() + SIZE / 2.0
        );
    }

    public boolean isReadyToRefill() {
        return System.currentTimeMillis() >= nextRefillAt;
    }

    public void scheduleRefill(long intervalSeconds) {
        this.nextRefillAt = System.currentTimeMillis() + (intervalSeconds * 1000L);
    }

    public String   getId()           { return id; }
    public OreType  getOreType()      { return oreType; }
    public Location getMinCorner()    { return minCorner.clone(); }
    public long     getNextRefillAt() { return nextRefillAt; }
}
