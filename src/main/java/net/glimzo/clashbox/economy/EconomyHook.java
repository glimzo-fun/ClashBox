package net.glimzo.clashbox.economy;

import net.glimzo.clashbox.core.ClashBoxPlugin;
import net.glimzo.clashbox.player.ClashBoxProfile;

import java.util.UUID;

public class EconomyHook {

    private final ClashBoxPlugin plugin;

    public EconomyHook(ClashBoxPlugin plugin,
                       WalletService walletService,
                       BankManager bankManager) {
        this.plugin = plugin;
    }

    public boolean addToWallet(UUID uuid, long amount, String type, String reason) {
        return plugin.getShardEconomy().addShards(uuid, amount, "[" + type + "] " + reason) > 0;
    }

    public boolean deductFromWallet(UUID uuid, long amount, String type, String reason) {
        return plugin.getShardEconomy().removeShards(uuid, amount, "[" + type + "] " + reason) > 0;
    }

    public long getWalletBalance(UUID uuid) {
        return plugin.getShardEconomy().getBalance(uuid);
    }

    public boolean hasWalletBalance(UUID uuid, long amount) {
        return plugin.getShardEconomy().has(uuid, amount);
    }

    public TransactionResult depositToBank(UUID uuid, long amount) {
        return plugin.getSavingsManager().deposit(uuid, amount);
    }

    public TransactionResult withdrawFromBank(UUID uuid, long amount) {
        return plugin.getSavingsManager().withdraw(uuid, amount);
    }

    public boolean addToBank(UUID uuid, long amount, String type, String reason) {
        ClashBoxProfile profile = plugin.getProfileManager().getProfile(uuid);
        if (profile == null) return false;
        profile.setSavingsBalance(profile.getSavingsBalance() + amount);
        profile.markDirty();
        return true;
    }

    public TransactionResult lockInvestment(UUID uuid, long amount, long durationSeconds) {
        return plugin.getSavingsManager().lockInvestment(uuid, amount, durationSeconds);
    }

    public TransactionResult claimInvestment(UUID uuid) {
        return plugin.getSavingsManager().claimInvestment(uuid);
    }
}
