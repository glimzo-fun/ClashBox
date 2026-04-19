package net.glimzo.clashbox.core;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class ClashBoxConfig {

    private final JavaPlugin plugin;

    public ClashBoxConfig(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public String getLobbyWorldName() {
        return plugin.getConfig().getString("world.lobby-world-name", "world");
    }

    public String getArenaWorldName() {
        return plugin.getConfig().getString("world.arena-world-name", "box");
    }

    @Deprecated
    public String getWorldName() {
        return getArenaWorldName();
    }

    public double getCenterX() {
        return plugin.getConfig().getDouble("world.center-x", 0);
    }

    public double getCenterZ() {
        return plugin.getConfig().getDouble("world.center-z", 0);
    }

    public Location getLobbySpawn() {
        World w = plugin.getServer().getWorld(getLobbyWorldName());
        double x     = plugin.getConfig().getDouble("world.lobby-spawn.x", 0.5);
        double y     = plugin.getConfig().getDouble("world.lobby-spawn.y", 100.0);
        double z     = plugin.getConfig().getDouble("world.lobby-spawn.z", 0.5);
        float  yaw   = (float) plugin.getConfig().getDouble("world.lobby-spawn.yaw", 0.0);
        float  pitch = (float) plugin.getConfig().getDouble("world.lobby-spawn.pitch", 0.0);
        return new Location(w, x, y, z, yaw, pitch);
    }

    public double getPitRadius() {
        return plugin.getConfig().getDouble("world.pit-radius", 5.0);
    }

    public double getPitEntryY() {
        return plugin.getConfig().getDouble("world.pit-entry-y", 95.0);
    }

    public int getZoneRadius(String zone) {
        return plugin.getConfig().getInt("zones." + zone + ".radius", 60);
    }

    public int getZoneYMin(String zone) {
        return plugin.getConfig().getInt("zones." + zone + ".y-min", 0);
    }

    public int getZoneYMax(String zone) {
        return plugin.getConfig().getInt("zones." + zone + ".y-max", 256);
    }

    public boolean isZonePvPEnabled(String zone) {
        return plugin.getConfig().getBoolean("zones." + zone + ".pvp-enabled", false);
    }

    public double getZoneKillMultiplier(String zone) {
        return plugin.getConfig().getDouble("zones." + zone + ".kill-reward-multiplier", 1.0);
    }

    public boolean hasZoneSellAccess(String zone) {
        return plugin.getConfig().getBoolean("zones." + zone + ".sell-access", true);
    }

    public double getZoneSellFeePercent(String zone) {
        return plugin.getConfig().getDouble("zones." + zone + ".sell-fee-percent", 0.0);
    }

    public double getZoneDeathLossPercent(String zone) {
        return plugin.getConfig().getDouble("zones." + zone + ".death-loss-percent", 0.0);
    }

    public long getBaseKillReward() {
        return plugin.getConfig().getLong("economy.base-kill-reward", 150);
    }

    public Map<Material, Long> getOreBasePrices() {
        Map<Material, Long> prices = new EnumMap<>(Material.class);
        ConfigurationSection section = plugin.getConfig()
                .getConfigurationSection("economy.base-ore-prices");
        if (section == null) return prices;
        for (String key : section.getKeys(false)) {
            try {
                Material mat = Material.valueOf(key);
                prices.put(mat, section.getLong(key));
            } catch (IllegalArgumentException ignored) {}
        }
        return prices;
    }

    public double getOreZoneMultiplier(String zone) {
        return plugin.getConfig().getDouble("economy.ore-zone-multipliers." + zone, 1.0);
    }

    public long getBankDefaultCapacity() {
        return plugin.getConfig().getLong("economy.bank.default-capacity", 50000);
    }

    public double getBankDepositTaxPercent() {
        return plugin.getConfig().getDouble("economy.bank.deposit-tax-percent", 0.5);
    }

    public Map<Long, Double> getInvestmentTiers() {
        Map<Long, Double> tiers = new LinkedHashMap<>();
        ConfigurationSection section = plugin.getConfig()
                .getConfigurationSection("economy.bank.investment-tiers");
        if (section == null) return tiers;
        for (String key : section.getKeys(false)) {
            tiers.put(Long.parseLong(key), section.getDouble(key));
        }
        return tiers;
    }

    public long getMaxInvestment() {
        return plugin.getConfig().getLong("economy.bank.max-investment", 100000);
    }

    public long getSellHourlySoftcap() {
        return plugin.getConfig().getLong("economy.sell-hourly-softcap", 10000);
    }

    public double getSellSoftcapReduction() {
        return plugin.getConfig().getDouble("economy.sell-softcap-reduction", 0.85);
    }

    public double getStreakCoinMultiplierPerKill() {
        return plugin.getConfig().getDouble("combat.streak-coin-multiplier-per-kill", 0.10);
    }

    public List<Integer> getStreakAnnounceMilestones() {
        return plugin.getConfig().getIntegerList("combat.streak-announce-milestones");
    }

    public int getStreakBountyThreshold() {
        return plugin.getConfig().getInt("combat.streak.bounty-threshold", 5);
    }

    public double getBountyBaseMultiplier() {
        return plugin.getConfig().getDouble("combat.bounty.base-multiplier", 2.0);
    }

    public long getBountyMinimumValue() {
        return plugin.getConfig().getLong("combat.bounty.minimum-value", 500);
    }

    public int getBountyExpirySeconds() {
        return plugin.getConfig().getInt("combat.bounty.expiry-seconds", 600);
    }

    public int getRespawnDelayTicks() {
        return plugin.getConfig().getInt("combat.respawn-delay-ticks", 60);
    }

    public double getAssistDamageThreshold() {
        return plugin.getConfig().getDouble("combat.assist-damage-threshold", 0.15);
    }

    public double getAssistRewardPercent() {
        return plugin.getConfig().getDouble("combat.assist-reward-percent", 0.40);
    }

    public int getOreRegenCheckInterval() {
        return plugin.getConfig().getInt("ore-regen.check-interval-ticks", 100);
    }

    public int getOreRegenMin(String zone) {
        return plugin.getConfig().getInt("ore-regen." + zone + "-regen-min", 20);
    }

    public int getOreRegenMax(String zone) {
        return plugin.getConfig().getInt("ore-regen." + zone + "-regen-max", 60);
    }

    public int getPortalActiveCount() {
        return plugin.getConfig().getInt("portal.active-count", 1);
    }

    public int getPortalRelocationInterval() {
        return plugin.getConfig().getInt("portal.relocation-interval", 120);
    }

    public boolean isPortalAnnounceEnabled() {
        return plugin.getConfig().getBoolean("portal.announce-in-chat", true);
    }

    public List<Location> getPortalSpawnLocations() {
        List<Location> locations = new ArrayList<>();
        World w = plugin.getServer().getWorld(getArenaWorldName());
        if (w == null) return locations;
        List<?> rawList = plugin.getConfig().getList("portal.spawn-locations");
        if (rawList == null) return locations;
        for (Object obj : rawList) {
            if (obj instanceof Map<?, ?> map) {
                double x = ((Number) (map.containsKey("x")   ? map.get("x")   : 0.0)).doubleValue();
                double y = ((Number) (map.containsKey("y")   ? map.get("y")   : 64.0)).doubleValue();
                double z = ((Number) (map.containsKey("z")   ? map.get("z")   : 0.0)).doubleValue();
                locations.add(new Location(w, x, y, z));
            }
        }
        return locations;
    }

    public int getEventMinPlayers() {
        return plugin.getConfig().getInt("events.min-players-to-fire", 3);
    }

    public boolean isEventEnabled(String event) {
        return plugin.getConfig().getBoolean("events." + event + ".enabled", true);
    }

    public int getEventMinDelay(String event) {
        return plugin.getConfig().getInt("events." + event + ".min-delay-seconds", 300);
    }

    public int getEventMaxDelay(String event) {
        return plugin.getConfig().getInt("events." + event + ".max-delay-seconds", 600);
    }

    public int getUpgradeMaxLevel(String upgrade) {
        return plugin.getConfig().getInt("progression.upgrades." + upgrade + ".max-level", 5);
    }

    public List<Long> getUpgradeCosts(String upgrade) {
        List<Integer> rawCosts = plugin.getConfig()
                .getIntegerList("progression.upgrades." + upgrade + ".costs");
        List<Long> costs = new ArrayList<>();
        for (int c : rawCosts) costs.add((long) c);
        return costs;
    }

    public long getBankCapacityPerLevel() {
        return plugin.getConfig().getLong(
                "progression.upgrades.BANK_CAPACITY.capacity-per-level", 50000);
    }

    public int getCurrentSeason() {
        return plugin.getConfig().getInt("progression.season.current-season", 1);
    }

    public int getTeamMaxSize() {
        return plugin.getConfig().getInt("teams.max-size", 3);
    }

    public int getTeamInviteExpiry() {
        return plugin.getConfig().getInt("teams.invite-expiry-seconds", 60);
    }

    public int getScoreboardUpdateTicks() {
        return plugin.getConfig().getInt("ui.scoreboard-update-ticks", 10);
    }

    public int getActionBarUpdateTicks() {
        return plugin.getConfig().getInt("ui.actionbar-update-ticks", 5);
    }

    public String getColor(String key) {
        return plugin.getConfig().getString("ui.colors." + key, "&f");
    }

    public org.bukkit.configuration.file.FileConfiguration getConfig() {
        return plugin.getConfig();
    }
}
