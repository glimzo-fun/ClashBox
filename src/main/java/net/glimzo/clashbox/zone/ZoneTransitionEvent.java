package net.glimzo.clashbox.zone;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class ZoneTransitionEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player   player;
    private final ZoneType from;
    private final ZoneType to;
    private boolean cancelled;

    public ZoneTransitionEvent(Player player, ZoneType from, ZoneType to) {
        this.player = player;
        this.from   = from;
        this.to     = to;
    }

    public Player  getPlayer()                        { return player; }
    public ZoneType getFrom()                         { return from; }
    public ZoneType getTo()                           { return to; }

    @Override public boolean isCancelled()            { return cancelled; }
    @Override public void setCancelled(boolean cancel){ this.cancelled = cancel; }
    @Override public HandlerList getHandlers()        { return HANDLERS; }
    public static HandlerList getHandlerList()        { return HANDLERS; }
}
