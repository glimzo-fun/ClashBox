package net.glimzo.clashbox.tier;

import net.glimzo.clashbox.bank.UnlockableFeature;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public enum TierLevel {

    TIER_1 (1,  "I",     "&7",  0,
            UnlockableFeature.WALLET, UnlockableFeature.ORE_SELLING, UnlockableFeature.KILL_REWARDS),

    TIER_2 (2,  "II",    "&a",  500,
            UnlockableFeature.SAVINGS_BANK),

    TIER_3 (3,  "III",   "&a",  1500,
            UnlockableFeature.ENCHANT_BASIC),

    TIER_4 (4,  "IV",    "&2",  3500,
            UnlockableFeature.INVESTMENT_BASIC),

    TIER_5 (5,  "V",     "&2",  7000,
            UnlockableFeature.ENCHANT_ADVANCED),

    TIER_6 (6,  "VI",    "&e",  14000,
            UnlockableFeature.INVESTMENT_FULL),

    TIER_7 (7,  "VII",   "&e",  25000,
            UnlockableFeature.LOAN_BASIC),

    TIER_8 (8,  "VIII",  "&6",  42000),

    TIER_9 (9,  "IX",    "&6",  65000),

    TIER_10(10, "X",     "&c",  100000,
            UnlockableFeature.LOAN_FULL),

    TIER_11(11, "XI",    "&c",  145000),
    TIER_12(12, "XII",   "&4",  200000),
    TIER_13(13, "XIII",  "&4",  270000),
    TIER_14(14, "XIV",   "&d",  360000),
    TIER_15(15, "XV",    "&d",  470000),
    TIER_16(16, "XVI",   "&5",  600000),
    TIER_17(17, "XVII",  "&5",  760000),
    TIER_18(18, "XVIII", "&b",  950000),
    TIER_19(19, "XIX",   "&b",  1200000),
    TIER_20(20, "XX",    "&3",  1500000);

    private final int    number;
    private final String roman;
    private final String color;
    private final long   notorietyRequired;
    private final List<UnlockableFeature> unlocks;

    TierLevel(int number, String roman, String color,
              long notorietyRequired, UnlockableFeature... unlocks) {
        this.number            = number;
        this.roman             = roman;
        this.color             = color;
        this.notorietyRequired = notorietyRequired;
        this.unlocks           = unlocks.length > 0
                ? Arrays.asList(unlocks)
                : Collections.emptyList();
    }

    public int    getNumber()            { return number; }
    public String getRoman()             { return roman; }
    public String getColor()             { return color; }
    public long   getNotorietyRequired() { return notorietyRequired; }
    public List<UnlockableFeature> getUnlocks() { return unlocks; }

    public String display() {
        return color + "Tier " + roman;
    }

    public static TierLevel fromNumber(int n) {
        for (TierLevel t : values()) {
            if (t.number == n) return t;
        }
        return TIER_1;
    }

    public static TierLevel forNotoriety(long notoriety) {
        TierLevel current = TIER_1;
        for (TierLevel t : values()) {
            if (notoriety >= t.notorietyRequired) current = t;
        }
        return current;
    }

    public TierLevel next() {
        TierLevel[] vals = values();
        int idx = ordinal() + 1;
        return idx < vals.length ? vals[idx] : this;
    }

    public boolean isMax() {
        return this == TIER_20;
    }
}
