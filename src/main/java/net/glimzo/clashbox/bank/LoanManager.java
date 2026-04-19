package net.glimzo.clashbox.bank;

import me.pikashrey.glimzocore.utilities.chat.CC;
import net.glimzo.clashbox.core.ClashBoxPlugin;
import net.glimzo.clashbox.economy.TransactionResult;
import net.glimzo.clashbox.player.ClashBoxProfile;
import org.bukkit.entity.Player;

import java.util.UUID;

public class LoanManager {

    private final ClashBoxPlugin plugin;
    private final BankConfig     bankCfg;
    private final ShardEconomy   shards;
    private final CreditManager  credit;
    private final TransactionLogger txLog;

    public LoanManager(ClashBoxPlugin plugin, BankConfig bankCfg,
                       ShardEconomy shards, CreditManager credit,
                       TransactionLogger txLog) {
        this.plugin  = plugin;
        this.bankCfg = bankCfg;
        this.shards  = shards;
        this.credit  = credit;
        this.txLog   = txLog;
    }

    public TransactionResult takeLoan(UUID uuid, long requestedAmount) {
        if (!bankCfg.isLoansEnabled())
            return TransactionResult.fail("&cLoans are currently disabled.");

        ClashBoxProfile profile = getProfile(uuid);
        if (profile == null) return TransactionResult.fail("&cProfile not loaded.");
        if (profile.getLoanBalance() > 0)
            return TransactionResult.fail(
                    "&cYou already have an active loan of " +
                    shards.formatShort(profile.getLoanBalance()) +
                    ". Repay it first.");

        long maxLoan = credit.calculateMaxLoan(uuid);
        if (maxLoan < bankCfg.getLoanMinimum())
            return TransactionResult.fail(
                    "&cYour credit score is too low to qualify for a loan. " +
                    "Build your score by repaying debts on time.");

        if (requestedAmount < bankCfg.getLoanMinimum())
            return TransactionResult.fail(
                    "&cMinimum loan is " + shards.formatShort(bankCfg.getLoanMinimum()) + ".");

        if (requestedAmount > maxLoan)
            return TransactionResult.fail(
                    "&cWith your credit score, you can borrow up to " +
                    shards.formatShort(maxLoan) + ".");

        long now = System.currentTimeMillis();
        profile.setLoanPrincipal(requestedAmount);
        profile.setLoanBalance(requestedAmount);
        profile.setLoanTakenTimestamp(now);
        profile.setLoanLastCompoundTimestamp(now);
        profile.markDirty();

        shards.addShards(uuid, requestedAmount, "Loan disbursement");

        BankConfig.CreditTier tier = credit.getTier(uuid);
        txLog.log(uuid, requestedAmount, "LOAN_TAKEN",
                "Took loan of " + requestedAmount + " @ " +
                (int)(tier.interestRatePerCompound() * 100) + "% per 6h");

        return TransactionResult.success(requestedAmount,
                "&aLoan approved! " + shards.formatShort(requestedAmount) +
                " &7added to wallet.\n" +
                "&7Interest: &c" + (int)(tier.interestRatePerCompound() * 100) +
                "% &7every &e6h &7| Score: " + credit.formatScore(uuid));
    }

    public TransactionResult repayLoan(UUID uuid, long amount) {
        ClashBoxProfile profile = getProfile(uuid);
        if (profile == null) return TransactionResult.fail("&cProfile not loaded.");
        if (profile.getLoanBalance() <= 0)
            return TransactionResult.fail("&cYou have no active loan.");

        long owed = profile.getLoanBalance();
        long toPay = Math.min(amount, owed);

        if (!shards.has(uuid, toPay))
            return TransactionResult.fail(
                    "&cInsufficient wallet balance. You owe " +
                    shards.formatShort(owed) + ".");

        shards.removeShards(uuid, toPay, "Loan repayment");

        boolean wasFullRepayment = (toPay >= owed);
        boolean wasEarly = wasFullRepayment &&
                (System.currentTimeMillis() - profile.getLoanTakenTimestamp()) <
                (bankCfg.getCompoundIntervalSeconds() * 1000L * 2);

        profile.setLoanBalance(owed - toPay);
        if (profile.getLoanBalance() <= 0) {
            profile.setLoanBalance(0);
            profile.setLoanPrincipal(0);
            profile.setLoanTakenTimestamp(0);
            profile.setLoanLastCompoundTimestamp(0);
        }
        profile.markDirty();

        txLog.log(uuid, -toPay, "LOAN_REPAYMENT",
                "Repaid " + toPay + " | Remaining: " + profile.getLoanBalance());

        if (wasFullRepayment) {
            credit.onLoanRepaidOnTime(uuid);
            if (wasEarly) credit.onLoanRepaidEarly(uuid);
        }

        String remaining = profile.getLoanBalance() > 0
                ? " &7Remaining: " + shards.formatShort(profile.getLoanBalance())
                : " &aLoan fully repaid!";

        return TransactionResult.success(toPay,
                "&aRepaid " + shards.formatShort(toPay) + "." + remaining);
    }

    public void startCompoundTask() {
        long intervalTicks = bankCfg.getCompoundIntervalSeconds() * 20L;
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                applyCompound(player.getUniqueId(), false);
            }
        }, intervalTicks, intervalTicks);
    }

    public void applyOfflineCompounds(UUID uuid) {
        ClashBoxProfile profile = getProfile(uuid);
        if (profile == null || profile.getLoanBalance() <= 0) return;

        long now          = System.currentTimeMillis();
        long lastCompound = profile.getLoanLastCompoundTimestamp();
        long intervalMs   = bankCfg.getCompoundIntervalSeconds() * 1000L;
        long elapsedMs    = now - lastCompound;
        int  cyclesMissed = (int)(elapsedMs / intervalMs);

        if (cyclesMissed <= 0) return;

        BankConfig.CreditTier tier = credit.getTier(uuid);
        double rate = tier.interestRatePerCompound();

        long balance = profile.getLoanBalance();
        long totalInterest = 0;

        for (int i = 0; i < cyclesMissed; i++) {
            long interest = (long) Math.ceil(balance * rate);
            balance      += interest;
            totalInterest += interest;
            credit.onMissedInterestPayment(uuid);
        }

        profile.setLoanBalance(balance);
        profile.setLoanLastCompoundTimestamp(lastCompound + (cyclesMissed * intervalMs));
        profile.markDirty();

        txLog.log(uuid, totalInterest, "LOAN_INTEREST_OFFLINE",
                cyclesMissed + " missed cycles, total interest=" + totalInterest);

        Player player = plugin.getServer().getPlayer(uuid);
        if (player != null && player.isOnline()) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline()) return;
                player.sendMessage(CC.translate(
                        "&8[&bClashBox Bank&8] &cLoan alert! &7You missed " +
                        cyclesMissed + " interest cycle" + (cyclesMissed > 1 ? "s" : "") +
                        " while offline. Current debt: " +
                        shards.formatShort(profile.getLoanBalance())));
            }, 80L);
        }
    }

    public void applyCompound(UUID uuid, boolean silent) {
        ClashBoxProfile profile = getProfile(uuid);
        if (profile == null || profile.getLoanBalance() <= 0) return;

        BankConfig.CreditTier tier = credit.getTier(uuid);
        double rate = tier.interestRatePerCompound();

        long interest = (long) Math.ceil(profile.getLoanBalance() * rate);
        profile.setLoanBalance(profile.getLoanBalance() + interest);
        profile.setLoanLastCompoundTimestamp(System.currentTimeMillis());
        profile.markDirty();

        credit.onMissedInterestPayment(uuid);

        txLog.log(uuid, interest, "LOAN_INTEREST",
                "Compounded @ " + (int)(rate * 100) + "% | New balance: " +
                profile.getLoanBalance());

        if (!silent) {
            Player player = plugin.getServer().getPlayer(uuid);
            if (player != null && player.isOnline()) {
                player.sendMessage(CC.translate(
                        "&8[&bClashBox Bank&8] &cLoan interest applied! &7+" +
                        shards.formatShort(interest) +
                        " | Total owed: " + shards.formatShort(profile.getLoanBalance()) +
                        " | Repay: &e/bank repay all"));
            }
        }
    }

    public void processSeasonEnd(UUID uuid) {
        ClashBoxProfile profile = getProfile(uuid);
        if (profile == null || profile.getLoanBalance() <= 0) return;

        long owed     = profile.getLoanBalance();
        long savings  = profile.getSavingsBalance();
        long deducted = Math.min(owed, savings);

        profile.setSavingsBalance(savings - deducted);
        profile.setLoanBalance(0);
        profile.setLoanPrincipal(0);
        profile.setLoanTakenTimestamp(0);
        profile.setLoanLastCompoundTimestamp(0);
        profile.markDirty();

        credit.onSeasonEndUnpaid(uuid);

        txLog.log(uuid, -deducted, "LOAN_SEASON_DEDUCT",
                "Season end auto-deduct. Forgiven: " + (owed - deducted));

        Player player = plugin.getServer().getPlayer(uuid);
        if (player != null) {
            player.sendMessage(CC.translate(
                    "&8[&bClashBox Bank&8] &cSeason ended with unpaid loan! " +
                    shards.formatShort(deducted) + " &cdeducted from savings. " +
                    "&7Credit score penalised."));
        }
    }

    public void applyDeathPenalty(UUID uuid) {
        ClashBoxProfile profile = getProfile(uuid);
        if (profile == null || profile.getLoanBalance() <= 0) return;

        double penaltyPct = bankCfg.getLoanDeathPenaltyPercent() / 100.0;
        long   penalty    = (long) Math.ceil(profile.getLoanBalance() * penaltyPct);

        profile.setLoanBalance(profile.getLoanBalance() + penalty);
        profile.markDirty();

        credit.onDeathPenalty(uuid);

        txLog.log(uuid, penalty, "LOAN_DEATH_PENALTY",
                "Died in Core with active loan. Penalty: +" + penalty);

        Player player = plugin.getServer().getPlayer(uuid);
        if (player != null) {
            player.sendMessage(CC.translate(
                    "&8[&bClashBox Bank&8] &cDeath penalty! &7Loan grew by +" +
                    shards.formatShort(penalty) +
                    " | Total owed: " + shards.formatShort(profile.getLoanBalance())));
        }
    }

    public boolean hasActiveLoan(UUID uuid) {
        ClashBoxProfile p = getProfile(uuid);
        return p != null && p.getLoanBalance() > 0;
    }

    public long getLoanBalance(UUID uuid) {
        ClashBoxProfile p = getProfile(uuid);
        return p == null ? 0 : p.getLoanBalance();
    }

    public long getLoanPrincipal(UUID uuid) {
        ClashBoxProfile p = getProfile(uuid);
        return p == null ? 0 : p.getLoanPrincipal();
    }

    private ClashBoxProfile getProfile(UUID uuid) {
        return plugin.getProfileManager().getProfile(uuid);
    }
}
