package net.glimzo.clashbox.arena;

import net.glimzo.clashbox.core.ClashBoxPlugin;
import net.glimzo.clashbox.player.PlayerState;
import net.glimzo.clashbox.player.PlayerStateManager;
import org.bukkit.Location;
import org.bukkit.Sound;
import net.glimzo.clashbox.ui.ParticleUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class SafeLandingHandler implements Listener {

    private final ClashBoxPlugin plugin;
    private final PlayerStateManager stateManager;
    private final Set<UUID> entryProtection = new HashSet<>();

    public SafeLandingHandler(ClashBoxPlugin plugin, PlayerStateManager stateManager) {
        this.plugin       = plugin;
        this.stateManager = stateManager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFallDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL) return;

        PlayerState state = stateManager.getState(player);
        if (state == PlayerState.ENTERING_ARENA || entryProtection.contains(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (stateManager.getState(player) != PlayerState.ENTERING_ARENA) return;

        Location to = event.getTo();
        if (to == null) return;

        if (!player.isOnGround()) return;

        fireLandingSequence(player);
    }

    private void fireLandingSequence(Player player) {
        if (stateManager.getState(player) != PlayerState.ENTERING_ARENA) return;
        // don't set state here - onArenaLanded will call updateZone which detects
        // the correct zone and sets the matching PlayerState. setting it prematurely
        // causes onArenaLanded to bail out early, skipping zone detection and starter kit

        Location loc = player.getLocation();

        UUID uuid = player.getUniqueId();
        entryProtection.add(uuid);
        plugin.getServer().getScheduler().runTaskLater(plugin, () ->
                entryProtection.remove(uuid), 60L);

        ParticleUtil.explosionLarge(loc);
        ParticleUtil.crit(loc, 20);
        ParticleUtil.smoke(loc, 10);

        player.playSound(loc, Sound.FALL_BIG, 1.0f, 0.7f);
        player.getWorld().playSound(loc, Sound.EXPLODE, 0.3f, 1.5f);

        player.addPotionEffect(new PotionEffect(
                PotionEffectType.SPEED, 60, 1, false, false));
        player.removePotionEffect(PotionEffectType.JUMP);

        plugin.getTitleService().sendTitle(player,
                "&b&l⚔ ARENA", "&7Fight. Mine. Survive.", 3, 30, 8);

        plugin.getPitEntryManager().onArenaLanded(player);
    }
}
