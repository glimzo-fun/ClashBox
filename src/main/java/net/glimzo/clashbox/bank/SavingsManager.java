package net.glimzo.clashbox.bank;

import me.pikashrey.glimzocore.api.player.GlobalPlayer;
import me.pikashrey.glimzocore.utilities.chat.CC;
import net.glimzo.clashbox.core.ClashBoxPlugin;
import net.glimzo.clashbox.economy.TransactionResult;
import net.glimzo.clashbox.player.ClashBoxProfile;
import org.bukkit.entity.Player;

import java.util.UUID;

public class SavingsManager {

    private final ClashBoxPlugin plugin;
    private final BankConfig     bankCfg;
    private final ShardEconomy   shards;
    private final TransactionLogger txLog;

    public SavingsManager(ClashBoxPlugin plugin, BankConfig bankCfg,
                          ShardEconomy shards, TransactionLogger txLog) {
        this.plugin  = plugin;
        this.bankCfg = bankCfg;
        this.shards  = shards;
        this.txLog   = txLog;
    }

    public void applyLoginInterest(Player player) {
        UUID uuid = player.getUniqueId();
        ClashBoxProfile profile = plugin.getProfileManager().getProfile(uuid);
        if (profile == null) return;
        if (profile.getSavingsBalance() <= 0) return;

        long lastSeen = 0;
        var gcData = GlobalPlayer.get(player);
        if (gcData != null) {
            lastSeen = gcData.getLastSeenTime();
        }
        if (lastSeen <= 0) return;

        long now       = System.currentTimeMillis();
        long elapsedMs = now - lastSeen;
        if (elapsedMs <= 0) return;

        double hoursElapsed = elapsedMs / 3_600_000.0;
        double ratePerHour  = bankCfg.getSavingsInterestRatePerHour();
        long   interest     = (long)(profile.getSavingsBalance() * ratePerHour * hoursElapsed);

        if (interest <= 0) return;

        profile.setSavingsBalance(profile.getSavingsBalance() + interest);
        profile.markDirty();

        txLog.log(uuid, interest, "SAVINGS_INTEREST",
                String.format("%.1f hours offline @ %.2f%%/hr", hoursElapsed, ratePerHour * 100));

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            player.sendMessage(CC.translate(
                    "&8[&bClashBox Bank&8] &7Savings interest: &a+" +
                            shards.formatShort(interest) + " &7(" +
                            String.format("%.1f", hoursElapsed) + "h @ " +
                            String.format("%.2f", ratePerHour * 100) + "%/hr)"));
        }, 60L);
    }

    public TransactionResult deposit(UUID uuid, long amount) {
        ClashBoxProfile profile = plugin.getProfileManager().getProfile(uuid);
        if (profile == null) return TransactionResult.fail("&cProfile not loaded.");
        if (amount <= 0)     return TransactionResult.fail("&cAmount must be positive.");

        if (bankCfg.isBlockDepositWhileIndebted() && profile.getLoanBalance() > 0) {
            return TransactionResult.fail(
                    "&cYou cannot deposit to savings while you have an active loan. " +
                            "Repay it first: &e/bank repay all");
        }

        if (!shards.has(uuid, amount))
            return TransactionResult.fail("&cInsufficient wallet balance.");

        long capacity = profile.getSavingsCapacity();
        if (profile.getSavingsBalance() + amount > capacity)
            return TransactionResult.fail("&cSavings full. Max: " + shards.formatShort(capacity));

        double taxRate   = bankCfg.getDepositTaxPercent() / 100.0;
        long   tax       = (long) Math.ceil(amount * taxRate);
        long   deposited = amount - tax;

        shards.removeShards(uuid, amount, "Savings deposit");
        profile.setSavingsBalance(profile.getSavingsBalance() + deposited);
        profile.markDirty();

        txLog.log(uuid, deposited, "SAVINGS_DEPOSIT",
                "Deposited " + deposited + " (tax=" + tax + ")");

        return TransactionResult.success(deposited,
                "&aDeposited " + shards.formatShort(deposited) +
                        " &7to savings (&c-" + shards.formatShort(tax) + " tax&7)");
    }

    public TransactionResult withdraw(UUID uuid, long amount) {
        ClashBoxProfile profile = plugin.getProfileManager().getProfile(uuid);
        if (profile == null) return TransactionResult.fail("&cProfile not loaded.");
        if (amount <= 0)     return TransactionResult.fail("&cAmount must be positive.");

        if (profile.getSavingsBalance() < amount)
            return TransactionResult.fail("&cInsufficient savings balance.");

        profile.setSavingsBalance(profile.getSavingsBalance() - amount);
        profile.markDirty();
        shards.addShards(uuid, amount, "Savings withdrawal");

        txLog.log(uuid, amount, "SAVINGS_WITHDRAW", "Withdrawn to wallet");

        return TransactionResult.success(amount,
                "&aWithdrew " + shards.formatShort(amount) + " &7from savings.");
    }

    public TransactionResult lockInvestment(UUID uuid, long amount, long durationSeconds) {
        ClashBoxProfile profile = plugin.getProfileManager().getProfile(uuid);
        if (profile == null)
            return TransactionResult.fail("&cProfile not loaded.");
        if (profile.getInvestmentVault() != null)
            return TransactionResult.fail("&cYou already have an active investment.");
        if (profile.getSavingsBalance() < amount)
            return TransactionResult.fail("&cInsufficient savings balance.");

        long maxInv = bankCfg.getMaxInvestment();
        if (amount > maxInv)
            return TransactionResult.fail("&cMax investment is " + shards.formatShort(maxInv));

        double rate = bankCfg.getInvestmentTiers().getOrDefault(durationSeconds, -1.0);
        if (rate < 0)
            return TransactionResult.fail("&cInvalid duration. Use: 2h, 8h, 24h, 72h");

        profile.setSavingsBalance(profile.getSavingsBalance() - amount);
        profile.setInvestmentVault(
                new net.glimzo.clashbox.economy.InvestmentVault(amount, durationSeconds, rate));
        profile.markDirty();

        txLog.log(uuid, -amount, "INVESTMENT_LOCK",
                "Locked " + amount + " for " + fmtDuration(durationSeconds) +
                        " @ " + (int)(rate * 100) + "%");

        return TransactionResult.success(amount,
                "&aInvested " + shards.formatShort(amount) +
                        " &7for &e" + fmtDuration(durationSeconds) +
                        " &7@ &a" + (int)(rate * 100) + "% &7return.");
    }

    public TransactionResult claimInvestment(UUID uuid) {
        ClashBoxProfile profile = plugin.getProfileManager().getProfile(uuid);
        if (profile == null)
            return TransactionResult.fail("&cProfile not loaded.");
        if (profile.getInvestmentVault() == null)
            return TransactionResult.fail("&cNo active investment.");
        if (!profile.getInvestmentVault().isMatured())
            return TransactionResult.fail("&cNot matured yet - " +
                    profile.getInvestmentVault().getTimeRemainingFormatted());

        long payout = profile.getInvestmentVault().getPayout();
        profile.setSavingsBalance(profile.getSavingsBalance() + payout);

        if (bankCfg.isAutoReinvestEnabled()) {
            long duration = (profile.getInvestmentVault().getUnlockTimestamp()
                    - profile.getInvestmentVault().getLockTimestamp()) / 1000L;
            double rate = bankCfg.getInvestmentTiers().getOrDefault(duration, -1.0);
            if (rate >= 0) {
                profile.setSavingsBalance(profile.getSavingsBalance() - payout);
                profile.setInvestmentVault(
                        new net.glimzo.clashbox.economy.InvestmentVault(payout, duration, rate));
                txLog.log(uuid, payout, "INVESTMENT_REINVEST", "Auto-reinvested");
                return TransactionResult.success(payout,
                        "&aAuto-reinvested " + shards.formatShort(payout) +
                                " &7for another &e" + fmtDuration(duration) + "&7.");
            }
        }

        profile.setInvestmentVault(null);
        profile.markDirty();

        txLog.log(uuid, payout, "INVESTMENT_CLAIM", "Claimed matured vault");

        return TransactionResult.success(payout,
                "&aClaimed " + shards.formatShort(payout) + " &7from investment vault!");
    }

    public void startMaturityChecker() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (org.bukkit.entity.Player p : plugin.getServer().getOnlinePlayers()) {
                ClashBoxProfile profile = plugin.getProfileManager().getProfile(p);
                if (profile == null || profile.getInvestmentVault() == null) continue;
                if (!profile.getInvestmentVault().isMatured()) continue;

                TransactionResult result = claimInvestment(p.getUniqueId());
                if (result.isSuccess()) {
                    p.sendMessage(CC.translate("&8[&bClashBox Bank&8] &a&lINVESTMENT MATURED! &r" +
                            result.getMessage()));
                    plugin.getTitleService().sendTitle(p,
                            "&a&lINVESTMENT!", "&7+" + shards.formatShort(result.getAmount()),
                            10, 60, 20);
                    p.playSound(p.getLocation(), org.bukkit.Sound.LEVEL_UP, 0.8f, 1.3f);
                }
            }
        }, 1200L, 1200L);
    }

    private String fmtDuration(long seconds) {
        if (seconds >= 86400) return (seconds / 86400) + "d";
        if (seconds >= 3600)  return (seconds / 3600) + "h";
        if (seconds >= 60)    return (seconds / 60) + "m";
        return seconds + "s";
    }
}
