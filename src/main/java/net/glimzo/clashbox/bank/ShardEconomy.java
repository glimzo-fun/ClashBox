package net.glimzo.clashbox.bank;

import me.pikashrey.glimzocore.utilities.chat.CC;
import net.glimzo.clashbox.core.ClashBoxPlugin;
import net.glimzo.clashbox.player.ClashBoxProfile;

import java.util.UUID;

public class ShardEconomy {

    private final ClashBoxPlugin plugin;
    private final BankConfig     bankCfg;

    public ShardEconomy(ClashBoxPlugin plugin, BankConfig bankCfg) {
        this.plugin  = plugin;
        this.bankCfg = bankCfg;
    }

    public long addShards(UUID uuid, long amount, String reason) {
        if (amount <= 0) return 0;
        ClashBoxProfile profile = getProfile(uuid);
        if (profile == null) return 0;

        profile.setShardBalance(profile.getShardBalance() + amount);
        profile.addLifetimeShardBalance(amount);
        logTransaction(uuid, amount, "SHARD_ADD", reason);
        return amount;
    }

    public long removeShards(UUID uuid, long amount, String reason) {
        if (amount <= 0) return 0;
        ClashBoxProfile profile = getProfile(uuid);
        if (profile == null) return 0;
        if (profile.getShardBalance() < amount) return 0;

        profile.setShardBalance(profile.getShardBalance() - amount);
        logTransaction(uuid, -amount, "SHARD_REMOVE", reason);
        return amount;
    }

    public long getBalance(UUID uuid) {
        ClashBoxProfile p = getProfile(uuid);
        return p == null ? 0L : p.getShardBalance();
    }

    public long getLifetimeEarned(UUID uuid) {
        ClashBoxProfile p = getProfile(uuid);
        return p == null ? 0L : p.getLifetimeShardsEarned();
    }

    public boolean has(UUID uuid, long amount) {
        return getBalance(uuid) >= amount;
    }

    public String format(long amount) {
        return CC.translate("&b" + bankCfg.getShardsSymbol() + " &f" +
                String.format("%,d", amount) + " &7" + bankCfg.getShardsName());
    }

    public String formatShort(long amount) {
        return CC.translate("&b" + bankCfg.getShardsSymbol() + "&f" +
                String.format("%,d", amount));
    }

    public long applySoftcap(UUID uuid, long rawValue) {
        ClashBoxProfile profile = getProfile(uuid);
        if (profile == null) return rawValue;

        long cap = bankCfg.getHourlySoftcap();
        long now = System.currentTimeMillis();

        if ((now - profile.getSessionSellWindowStart()) > 3_600_000L) {
            profile.resetSessionSellTracking();
        }

        if (profile.getSessionSoldValue() >= cap) {
            return (long)(rawValue * bankCfg.getSoftcapReduction());
        }

        return rawValue;
    }

    private ClashBoxProfile getProfile(UUID uuid) {
        return plugin.getProfileManager().getProfile(uuid);
    }

    private void logTransaction(UUID uuid, long amount, String type, String reason) {
        plugin.getLogger().fine("[Shards] " + uuid + " | " + type +
                " | " + amount + " | " + reason);
    }
}
