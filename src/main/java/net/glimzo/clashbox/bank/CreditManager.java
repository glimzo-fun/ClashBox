package net.glimzo.clashbox.bank;

import me.pikashrey.glimzocore.utilities.chat.CC;
import net.glimzo.clashbox.core.ClashBoxPlugin;
import net.glimzo.clashbox.player.ClashBoxProfile;

import java.util.UUID;

public class CreditManager {

    private final ClashBoxPlugin plugin;
    private final BankConfig     bankCfg;

    public CreditManager(ClashBoxPlugin plugin, BankConfig bankCfg) {
        this.plugin  = plugin;
        this.bankCfg = bankCfg;
    }

    public int getScore(UUID uuid) {
        ClashBoxProfile p = getProfile(uuid);
        return p == null ? bankCfg.getDefaultCreditScore() : p.getCreditScore();
    }

    public BankConfig.CreditTier getTier(UUID uuid) {
        return bankCfg.getTierForScore(getScore(uuid));
    }

    public String formatScore(UUID uuid) {
        int score = getScore(uuid);
        BankConfig.CreditTier tier = bankCfg.getTierForScore(score);
        return CC.translate(tier.label() + " &8(&f" + score + "&8)");
    }

    private void adjust(UUID uuid, int delta, String reason) {
        if (!bankCfg.isCreditEnabled()) return;
        ClashBoxProfile profile = getProfile(uuid);
        if (profile == null) return;

        int current  = profile.getCreditScore();
        int newScore = Math.max(bankCfg.getMinCreditScore(),
                       Math.min(bankCfg.getMaxCreditScore(), current + delta));
        profile.setCreditScore(newScore);
        profile.markDirty();

        plugin.getLogger().fine("[Credit] " + uuid + " | " + current +
                " -> " + newScore + " (" + (delta > 0 ? "+" : "") + delta + ") | " + reason);
    }

    public void onLoanRepaidOnTime(UUID uuid) {
        adjust(uuid, bankCfg.getCreditEvent("loan-repaid-on-time"), "Loan repaid on time");
        notifyScore(uuid, "Loan repaid on time");
    }

    public void onLoanRepaidEarly(UUID uuid) {
        adjust(uuid, bankCfg.getCreditEvent("loan-repaid-early"), "Loan repaid early (bonus)");
    }

    public void onWeeklyActive(UUID uuid) {
        adjust(uuid, bankCfg.getCreditEvent("weekly-active-player"), "Weekly active player");
    }

    public void onMissedInterestPayment(UUID uuid) {
        adjust(uuid, bankCfg.getCreditEvent("missed-interest-payment"), "Missed interest payment");
        notifyScore(uuid, "&cMissed interest payment - score decreased");
    }

    public void onSeasonEndUnpaid(UUID uuid) {
        adjust(uuid, bankCfg.getCreditEvent("season-end-unpaid"), "Season ended with unpaid loan");
    }

    public void onDeathPenalty(UUID uuid) {
        adjust(uuid, bankCfg.getCreditEvent("death-penalty-triggered"), "Death penalty on loan");
    }

    public long calculateMaxLoan(UUID uuid) {
        ClashBoxProfile profile = getProfile(uuid);
        if (profile == null) return 0;

        BankConfig.CreditTier tier = getTier(uuid);
        double percent = tier.maxBorrowPercent();
        String mode    = bankCfg.getBorrowCapMode();

        long base;
        if ("lifetime".equalsIgnoreCase(mode)) {
            long savings  = profile.getSavingsBalance();
            long lifetime = profile.getLifetimeShardsEarned();
            base = (savings + lifetime) / 2;
        } else {
            base = profile.getSavingsBalance();
        }

        long rawMax = (long)(base * percent);
        if (rawMax < bankCfg.getLoanMinimum()) return 0;

        return Math.min(rawMax, bankCfg.getLoanMaximum());
    }

    private ClashBoxProfile getProfile(UUID uuid) {
        return plugin.getProfileManager().getProfile(uuid);
    }

    private void notifyScore(UUID uuid, String reason) {
        org.bukkit.entity.Player player = plugin.getServer().getPlayer(uuid);
        if (player == null) return;
        player.sendMessage(CC.translate(
                "&8[&bCredit&8] &7Score: " + formatScore(uuid) +
                " &8| &7" + reason));
    }
}
