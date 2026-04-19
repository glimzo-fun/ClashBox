package net.glimzo.clashbox.player;

import net.glimzo.clashbox.core.ClashBoxPlugin;
import net.glimzo.clashbox.zone.ZoneType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerStateManager implements Listener {

    private final ClashBoxPlugin plugin;
    private final Map<UUID, PlayerState> states    = new HashMap<>();
    private final Map<UUID, ZoneType>    lastZones = new HashMap<>();
    private final Map<UUID, Long>        lastMoveCheck = new HashMap<>();

    public PlayerStateManager(ClashBoxPlugin plugin) {
        this.plugin = plugin;
    }

    public PlayerState getState(Player player) {
        return states.getOrDefault(player.getUniqueId(), PlayerState.IN_LOBBY);
    }

    public void setState(Player player, PlayerState state) {
        states.put(player.getUniqueId(), state);
    }

    public boolean isInArena(Player player) {
        PlayerState s = getState(player);
        return s == PlayerState.IN_OUTER
            || s == PlayerState.IN_MID
            || s == PlayerState.IN_CORE
            || s == PlayerState.ENTERING_ARENA;
    }

    public boolean isAliveInArena(Player player) {
        PlayerState s = getState(player);
        return s == PlayerState.IN_OUTER
            || s == PlayerState.IN_MID
            || s == PlayerState.IN_CORE;
    }

    public boolean isPvPAllowed(Player player) {
        PlayerState s = getState(player);
        return s == PlayerState.IN_MID || s == PlayerState.IN_CORE;
    }

    public ZoneType getLastZone(Player player) {
        return lastZones.getOrDefault(player.getUniqueId(), ZoneType.OUTER);
    }

    public void updateZone(Player player, ZoneType zone) {
        lastZones.put(player.getUniqueId(), zone);
        switch (zone) {
            case OUTER -> setState(player, PlayerState.IN_OUTER);
            case MID   -> setState(player, PlayerState.IN_MID);
            case CORE  -> setState(player, PlayerState.IN_CORE);
        }
    }

    public long getLastMoveCheck(Player player) {
        return lastMoveCheck.getOrDefault(player.getUniqueId(), 0L);
    }

    public void updateLastMoveCheck(Player player) {
        lastMoveCheck.put(player.getUniqueId(), System.currentTimeMillis());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        states.remove(id);
        lastZones.remove(id);
        lastMoveCheck.remove(id);
    }

    public void remove(Player player) {
        UUID id = player.getUniqueId();
        states.remove(id);
        lastZones.remove(id);
        lastMoveCheck.remove(id);
    }
}
