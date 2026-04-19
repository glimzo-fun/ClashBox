package net.glimzo.clashbox.sell;

import net.glimzo.clashbox.core.ClashBoxPlugin;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class SellArea implements Listener {

    private final ClashBoxPlugin plugin;
    private Location center;
    private double radius = 3.0;

    private final Set<UUID> cooldown = new HashSet<>();

    public SellArea(ClashBoxPlugin plugin) {
        this.plugin = plugin;
        loadLocation();
    }

    private void loadLocation() {
        var cfg = plugin.getConfig();
        if (!cfg.contains("sell-area.x")) return;

        var world = plugin.getServer().getWorld(plugin.getCBConfig().getWorldName());
        if (world == null) return;

        center = new Location(world,
                cfg.getDouble("sell-area.x"),
                cfg.getDouble("sell-area.y"),
                cfg.getDouble("sell-area.z"));
        radius = cfg.getDouble("sell-area.radius", 3.0);
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (center == null) return;
        Player player = event.getPlayer();

        if (!plugin.getPlayerStateManager().isAliveInArena(player)) return;
        if (cooldown.contains(player.getUniqueId())) return;

        Location to = event.getTo();
        if (to == null) return;
        if (!to.getWorld().equals(center.getWorld())) return;
        if (to.distanceSquared(center) > radius * radius) return;

        cooldown.add(player.getUniqueId());
        plugin.getServer().getScheduler().runTaskLater(plugin,
                () -> cooldown.remove(player.getUniqueId()), 40L);

        plugin.getSellManager().sellAllOres(player);
    }

    public void setCenter(Location loc) {
        this.center = loc;
        var cfg = plugin.getConfig();
        cfg.set("sell-area.x", loc.getX());
        cfg.set("sell-area.y", loc.getY());
        cfg.set("sell-area.z", loc.getZ());
        plugin.saveConfig();
    }

    public Location getCenter() { return center; }
}
