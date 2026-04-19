package net.glimzo.clashbox.tier;

public enum NotorietySource {

    KILL_OUTER        (1),
    KILL_MID          (3),
    KILL_CORE         (8),
    KILL_BOUNTY       (15),
    ORE_SELL_1000     (1),
    SUPPLY_DROP       (20),
    VEIN_BURST        (5),
    BANK_RAID_WIN     (30),
    LOAN_REPAID       (2),
    INVESTMENT_MATURE (1),
    STREAK_MILESTONE  (0);

    private final long baseAmount;

    NotorietySource(long base) {
        this.baseAmount = base;
    }

    public long getAmount()                { return baseAmount; }
    public long getAmount(long multiplier) { return baseAmount * multiplier; }
}
