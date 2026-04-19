package net.glimzo.clashbox.arena;

import me.pikashrey.glimzocore.utilities.chat.CC;
import net.glimzo.clashbox.core.ClashBoxPlugin;
import net.glimzo.clashbox.ui.ParticleUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class OreCubeManager implements Listener {

    private final ClashBoxPlugin plugin;
    private final Map<String, OreCube> cubes = new LinkedHashMap<>();

    private BukkitTask refillTask = null;
    private long refillIntervalSeconds = 15L;

    private final Set<Material> allowedCubeMaterials = EnumSet.noneOf(Material.class);

    public OreCubeManager(ClashBoxPlugin plugin) {
        this.plugin = plugin;
        for (OreType type : OreType.values()) {
            allowedCubeMaterials.add(type.getOreMaterial());
            allowedCubeMaterials.add(type.getBlockMaterial());
        }
    }

    public void startTask() {
        File cfgFile = new File(plugin.getDataFolder(), "ore-cubes.yml");
        if (cfgFile.exists()) {
            YamlConfiguration cfg = YamlConfiguration.loadConfiguration(cfgFile);
            refillIntervalSeconds = cfg.getLong("refill-interval-seconds", 15L);
        }

        long intervalTicks = refillIntervalSeconds * 20L;
        refillTask = plugin.getServer().getScheduler().runTaskTimer(
                plugin,
                this::tickRefill,
                intervalTicks,
                intervalTicks
        );

        plugin.getLogger().info("[ClashBox] OreCubeManager started - refill every "
                + refillIntervalSeconds + "s, " + cubes.size() + " cube(s) loaded.");
    }

    public void stopTask() {
        if (refillTask != null) {
            refillTask.cancel();
            refillTask = null;
        }
    }

    private void tickRefill() {
        if (cubes.isEmpty()) return;
        for (OreCube cube : cubes.values()) {
            if (!cube.isReadyToRefill()) continue;
            cube.fill();
            cube.scheduleRefill(refillIntervalSeconds);
            notifyNearbyPlayers(cube);
        }
    }

    private void notifyNearbyPlayers(OreCube cube) {
        Location center = cube.getCenter();
        World world = center.getWorld();
        if (world == null) return;

        ParticleUtil.happyVillager(center, 20);
        ParticleUtil.spellInstant(center, 10);

        for (Player p : world.getPlayers()) {
            if (p.getLocation().distanceSquared(center) <= OreCube.NOTIFY_RADIUS_SQ) {
                p.playSound(center, Sound.NOTE_PLING, 0.8f, 1.6f);
                p.playSound(center, Sound.ORB_PICKUP, 0.5f, 1.2f);
                p.sendMessage(CC.translate(
                        "&8[&b✦&8] &7The &" + oreColor(cube.getOreType()) +
                        cube.getOreType().getDisplayName() +
                        " &7cube has &arefilled&7!"));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Location loc = block.getLocation();
        Material type = block.getType();
        Player player = event.getPlayer();

        if (!plugin.getPlayerStateManager().isAliveInArena(player)) return;

        OreCube cube = getCubeAt(loc);
        if (cube == null) return;

        if (!cube.getOreType().matches(type)) {
            event.setCancelled(true);
            return;
        }

        event.setExpToDrop(0);
    }

    public void registerCube(String id, OreType oreType, Location corner1, Location corner2) {
        int minX = Math.min(corner1.getBlockX(), corner2.getBlockX());
        int minY = Math.min(corner1.getBlockY(), corner2.getBlockY());
        int minZ = Math.min(corner1.getBlockZ(), corner2.getBlockZ());
        Location minCorner = new Location(corner1.getWorld(), minX, minY, minZ);

        OreCube cube = new OreCube(id, oreType, minCorner);
        cubes.put(id.toLowerCase(), cube);
        cube.fill();
        cube.scheduleRefill(refillIntervalSeconds);

        plugin.getLogger().info("[ClashBox] Registered ore cube '" + id +
                "' (" + oreType.name() + ") at " +
                minX + "," + minY + "," + minZ);
    }

    public boolean removeCube(String id) {
        return cubes.remove(id.toLowerCase()) != null;
    }

    public boolean forceRefill(String id) {
        OreCube cube = cubes.get(id.toLowerCase());
        if (cube == null) return false;
        cube.fill();
        cube.scheduleRefill(refillIntervalSeconds);
        notifyNearbyPlayers(cube);
        return true;
    }

    public void saveCubes() {
        File file = new File(plugin.getDataFolder(), "ore-cubes.yml");
        YamlConfiguration cfg = new YamlConfiguration();

        cfg.set("refill-interval-seconds", refillIntervalSeconds);

        for (OreCube cube : cubes.values()) {
            String path = "cubes." + cube.getId();
            Location mc = cube.getMinCorner();
            cfg.set(path + ".ore-type", cube.getOreType().name());
            cfg.set(path + ".world",    mc.getWorld().getName());
            cfg.set(path + ".min-x",    mc.getBlockX());
            cfg.set(path + ".min-y",    mc.getBlockY());
            cfg.set(path + ".min-z",    mc.getBlockZ());
        }

        try {
            cfg.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("[ClashBox] Failed to save ore-cubes.yml: " + e.getMessage());
        }
    }

    public int loadCubes() {
        File file = new File(plugin.getDataFolder(), "ore-cubes.yml");
        if (!file.exists()) {
            createDefaultConfig(file);
            return 0;
        }

        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        refillIntervalSeconds = cfg.getLong("refill-interval-seconds", 15L);
        cubes.clear();

        ConfigurationSection section = cfg.getConfigurationSection("cubes");
        if (section == null) return 0;

        int count = 0;
        for (String id : section.getKeys(false)) {
            String path      = "cubes." + id;
            String typeName  = cfg.getString(path + ".ore-type");
            String worldName = cfg.getString(path + ".world");
            int    minX      = cfg.getInt(path + ".min-x");
            int    minY      = cfg.getInt(path + ".min-y");
            int    minZ      = cfg.getInt(path + ".min-z");

            OreType oreType = OreType.fromString(typeName);
            if (oreType == null) {
                plugin.getLogger().warning("[ClashBox] Unknown OreType '" + typeName +
                        "' for cube '" + id + "' - skipping.");
                continue;
            }

            World world = plugin.getServer().getWorld(worldName);
            if (world == null) {
                plugin.getLogger().warning("[ClashBox] World '" + worldName +
                        "' not found for cube '" + id + "' - skipping.");
                continue;
            }

            Location minCorner = new Location(world, minX, minY, minZ);
            OreCube cube = new OreCube(id, oreType, minCorner);
            cubes.put(id.toLowerCase(), cube);
            cube.fill();
            cube.scheduleRefill(refillIntervalSeconds);
            count++;
        }

        plugin.getLogger().info("[ClashBox] Loaded " + count + " ore cube(s) from ore-cubes.yml.");
        return count;
    }

    public OreCube getCubeAt(Location loc) {
        for (OreCube cube : cubes.values()) {
            if (cube.contains(loc)) return cube;
        }
        return null;
    }

    public Collection<OreCube> getCubes()      { return cubes.values(); }
    public OreCube getCube(String id)           { return cubes.get(id.toLowerCase()); }
    public long getRefillIntervalSeconds()      { return refillIntervalSeconds; }
    public void setRefillIntervalSeconds(long s){ this.refillIntervalSeconds = s; }

    private String oreColor(OreType type) {
        return switch (type) {
            case COAL     -> "8";
            case IRON     -> "7";
            case GOLD     -> "6";
            case REDSTONE -> "c";
            case LAPIS    -> "9";
            case DIAMOND  -> "b";
            case EMERALD  -> "a";
            case DARK_OAK -> "8";
        };
    }

    private void createDefaultConfig(File file) {
        file.getParentFile().mkdirs();
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("refill-interval-seconds", 15);
        try {
            cfg.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("[ClashBox] Could not create ore-cubes.yml: " + e.getMessage());
        }
    }
}
