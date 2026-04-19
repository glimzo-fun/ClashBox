package net.glimzo.clashbox.arena;

import net.glimzo.clashbox.zone.ZoneType;
import org.bukkit.Location;
import org.bukkit.Material;

public class OreNode {

    private final Location location;
    private final ZoneType zone;
    private Material currentOre;
    private long respawnAt;

    public OreNode(Location location, ZoneType zone, Material initialOre) {
        this.location   = location;
        this.zone       = zone;
        this.currentOre = initialOre;
        this.respawnAt  = 0L;
    }

    public boolean isReadyToSpawn() {
        return currentOre == null && System.currentTimeMillis() >= respawnAt;
    }

    public boolean isMined() {
        return currentOre == null;
    }

    public Location getLocation()             { return location; }
    public ZoneType getZone()                 { return zone; }
    public Material getCurrentOre()           { return currentOre; }
    public void setCurrentOre(Material ore)   { this.currentOre = ore; }
    public long getRespawnAt()                { return respawnAt; }
    public void setRespawnAt(long ts)         { this.respawnAt = ts; }
}
