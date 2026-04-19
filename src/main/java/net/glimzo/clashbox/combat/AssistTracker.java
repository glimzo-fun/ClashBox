package net.glimzo.clashbox.combat;

import net.glimzo.clashbox.core.ClashBoxPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.*;

public class AssistTracker implements Listener {

    private final ClashBoxPlugin plugin;

    // victimUUID -> Map<attackerUUID, totalDamageDealt>
    private final Map<UUID, Map<UUID, Double>> damageMap = new HashMap<>();
    private final Map<UUID, Double> maxHpSnapshot = new HashMap<>();

    public AssistTracker(ClashBoxPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;

        Player attacker = null;
        if (event.getDamager() instanceof Player p) {
            attacker = p;
        } else if (event.getDamager() instanceof org.bukkit.entity.Arrow arrow
                && arrow.getShooter() instanceof Player p) {
            attacker = p;
        }
        if (attacker == null) return;
        if (attacker.equals(victim)) return;

        maxHpSnapshot.putIfAbsent(victim.getUniqueId(), victim.getMaxHealth());

        damageMap
            .computeIfAbsent(victim.getUniqueId(), k -> new HashMap<>())
            .merge(attacker.getUniqueId(), event.getFinalDamage(), Double::sum);
    }

    public List<UUID> getAssisters(UUID victimUuid, UUID killerUuid) {
        Map<UUID, Double> contributions = damageMap.get(victimUuid);
        if (contributions == null) return Collections.emptyList();

        double maxHp = maxHpSnapshot.getOrDefault(victimUuid, 20.0);
        double threshold = plugin.getCBConfig().getAssistDamageThreshold() * maxHp;

        List<UUID> assisters = new ArrayList<>();
        for (Map.Entry<UUID, Double> entry : contributions.entrySet()) {
            UUID attacker = entry.getKey();
            if (attacker.equals(killerUuid)) continue;
            if (entry.getValue() >= threshold) {
                assisters.add(attacker);
            }
        }
        return assisters;
    }

    public void clearPlayer(UUID uuid) {
        damageMap.remove(uuid);
        maxHpSnapshot.remove(uuid);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        clearPlayer(event.getPlayer().getUniqueId());
    }
}
