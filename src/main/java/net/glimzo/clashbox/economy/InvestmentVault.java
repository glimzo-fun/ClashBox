package net.glimzo.clashbox.economy;

public class InvestmentVault {

    private final long   lockedAmount;
    private final long   lockTimestamp;
    private final long   unlockTimestamp;
    private final double interestRate;

    public InvestmentVault(long amount, long durationSeconds, double interestRate) {
        this.lockedAmount    = amount;
        this.interestRate    = interestRate;
        this.lockTimestamp   = System.currentTimeMillis();
        this.unlockTimestamp = lockTimestamp + (durationSeconds * 1000L);
    }

    // used when deserializing from storage
    public InvestmentVault(long amount, long lockTimestamp,
                           long unlockTimestamp, double interestRate) {
        this.lockedAmount    = amount;
        this.lockTimestamp   = lockTimestamp;
        this.unlockTimestamp = unlockTimestamp;
        this.interestRate    = interestRate;
    }

    public boolean isMatured() {
        return System.currentTimeMillis() >= unlockTimestamp;
    }

    public long getPayout() {
        return (long)(lockedAmount * (1.0 + interestRate));
    }

    public long getProfit() {
        return getPayout() - lockedAmount;
    }

    public long getMillisRemaining() {
        return Math.max(0, unlockTimestamp - System.currentTimeMillis());
    }

    public String getTimeRemainingFormatted() {
        long ms = getMillisRemaining();
        long totalSeconds = ms / 1000;
        long hours   = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        if (hours > 0)   return hours + "h " + minutes + "m";
        if (minutes > 0) return minutes + "m " + seconds + "s";
        return seconds + "s";
    }

    public long   getLockedAmount()    { return lockedAmount; }
    public long   getLockTimestamp()   { return lockTimestamp; }
    public long   getUnlockTimestamp() { return unlockTimestamp; }
    public double getInterestRate()    { return interestRate; }
}
