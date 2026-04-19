package net.glimzo.clashbox.zone;

import net.glimzo.clashbox.core.ClashBoxPlugin;
import net.glimzo.clashbox.utilities.ConfigFile;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

public class ZoneConfig {

    private final ClashBoxPlugin plugin;
    private FileConfiguration cfg;

    public ZoneConfig(ClashBoxPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        cfg = ConfigFile.load(plugin, "zones.yml");
    }

    private ConfigurationSection zone(String key) {
        return cfg.getConfigurationSection("zones." + key);
    }

    public boolean isPvpEnabled(ZoneType zone) {
        ConfigurationSection s = zone(zone.configKey());
        return s != null && s.getBoolean("pvp", zone != ZoneType.OUTER);
    }

    public boolean isBlockPlacementAllowed(ZoneType zone) {
        ConfigurationSection s = zone(zone.configKey());
        return s == null || s.getBoolean("block-placement", true);
    }

    public boolean isBlockBreakAllowed(ZoneType zone) {
        ConfigurationSection s = zone(zone.configKey());
        return s == null || s.getBoolean("block-break", true);
    }

    public boolean isFallDamageEnabled(ZoneType zone) {
        ConfigurationSection s = zone(zone.configKey());
        return s == null || s.getBoolean("fall-damage", true);
    }

    public boolean isHungerEnabled(ZoneType zone) {
        ConfigurationSection s = zone(zone.configKey());
        return s == null || s.getBoolean("hunger", true);
    }

    public boolean isMobSpawningEnabled(ZoneType zone) {
        ConfigurationSection s = zone(zone.configKey());
        return s != null && s.getBoolean("mob-spawning", false);
    }

    public double getDeathLossPercent(ZoneType zone) {
        ConfigurationSection s = zone(zone.configKey());
        return s == null ? 0.0 : s.getDouble("death-loss-percent", 0.0);
    }

    public double getKillRewardMultiplier(ZoneType zone) {
        ConfigurationSection s = zone(zone.configKey());
        return s == null ? 1.0 : s.getDouble("kill-reward-multiplier", 1.0);
    }

    public boolean isSellAllowed(ZoneType zone) {
        ConfigurationSection s = zone(zone.configKey());
        return s == null || s.getBoolean("sell-access", true);
    }

    public double getSellFeePercent(ZoneType zone) {
        ConfigurationSection s = zone(zone.configKey());
        return s == null ? 0.0 : s.getDouble("sell-fee-percent", 0.0);
    }

    public int getZoneRadius(ZoneType zone) {
        ConfigurationSection s = zone(zone.configKey());
        return s == null ? 60 : s.getInt("radius", 60);
    }

    public int getYMin(ZoneType zone) {
        ConfigurationSection s = zone(zone.configKey());
        return s == null ? 0 : s.getInt("y-min", 0);
    }

    public int getYMax(ZoneType zone) {
        ConfigurationSection s = zone(zone.configKey());
        return s == null ? 256 : s.getInt("y-max", 256);
    }

    public String getEntryTitle(ZoneType zone) {
        ConfigurationSection s = zone(zone.configKey());
        return s == null ? zone.displayName() : s.getString("entry-title", zone.displayName());
    }

    public String getEntrySubtitle(ZoneType zone) {
        ConfigurationSection s = zone(zone.configKey());
        return s == null ? "" : s.getString("entry-subtitle", "");
    }
}
