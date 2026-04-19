package net.glimzo.clashbox.sets;

import net.glimzo.clashbox.core.ClashBoxPlugin;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;

import java.util.Map;

public class SetRoomInteractListener implements Listener {

    private static final double INTERACT_RADIUS_SQ = 4.0;
    private final ClashBoxPlugin plugin;

    public SetRoomInteractListener(ClashBoxPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityInteract(PlayerInteractAtEntityEvent event) {
        Player player = event.getPlayer();
        Entity entity = event.getRightClicked();

        if (!(entity instanceof ArmorStand)) return;

        String setId = findSetForArmorStand(entity.getLocation());
        if (setId == null) return;

        ArmorSet set = plugin.getSetManager().getSet(setId);
        if (set == null) return;

        event.setCancelled(true);
        player.playSound(player.getLocation(), Sound.NOTE_PLING, 0.6f, 1.4f);
        new SetDisplayMenu(plugin, player, set).open();
    }

    private String findSetForArmorStand(Location loc) {
        for (Map.Entry<String, Location> entry :
                plugin.getSetManager().getArmorStandLocations().entrySet()) {
            Location stored = entry.getValue();
            if (stored.getWorld() != null && stored.getWorld().equals(loc.getWorld())) {
                double dx = loc.getX() - stored.getX();
                double dy = loc.getY() - stored.getY();
                double dz = loc.getZ() - stored.getZ();
                if (dx * dx + dy * dy + dz * dz <= INTERACT_RADIUS_SQ) {
                    return entry.getKey();
                }
            }
        }
        return null;
    }
}
