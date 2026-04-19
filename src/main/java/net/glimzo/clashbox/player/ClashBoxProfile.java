package net.glimzo.clashbox.player;

import me.pikashrey.glimzocore.api.player.GlobalPlayer;
import me.pikashrey.glimzocore.api.player.PlayerData;
import net.glimzo.clashbox.economy.InvestmentVault;
import net.glimzo.clashbox.enchant.AppliedEnchant;
import net.glimzo.clashbox.enchant.GearSlot;
import net.glimzo.clashbox.progression.UpgradeType;
import net.glimzo.clashbox.sets.SetPiece;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

public class ClashBoxProfile {

    private final UUID   uuid;
    private String       lastKnownName;

    private long         bankBalance;
    private long         bankCapacity;
    private InvestmentVault investmentVault;

    private long sessionSoldValue;
    private long sessionSellWindowStart;

    private int lifetimeKills;
    private int lifetimeDeaths;
    private int lifetimeBountiesClaimed;
    private int bountiesPlacedOnSelf;

    private int seasonKills;
    private int seasonDeaths;
    private int seasonBountiesClaimed;
    private int currentSeason;

    private final Map<UpgradeType, Integer> upgradeLevels = new EnumMap<>(UpgradeType.class);

    private UUID teamId;

    private long shardBalance;
    private long lifetimeShardsEarned;

    private long savingsBalance;
    private long savingsCapacity;

    private long loanPrincipal;
    private long loanBalance;
    private long loanTakenTimestamp;
    private long loanLastCompoundTimestamp;

    private int  creditScore;
    private long notoriety;
    private int  tierNumber;

    private final java.util.Map<GearSlot, java.util.List<AppliedEnchant>> enchants =
            new java.util.EnumMap<>(GearSlot.class);
    private final java.util.Map<String, Integer> vanillaEnchantLevels = new java.util.HashMap<>();

    private final Map<SetPiece, String> ownedSetPieces = new EnumMap<>(SetPiece.class);

    private boolean dirty = false;

    public ClashBoxProfile(UUID uuid, String name, long defaultBankCapacity, int currentSeason) {
        this.uuid          = uuid;
        this.lastKnownName = name;
        this.bankCapacity  = defaultBankCapacity;
        this.currentSeason = currentSeason;
        for (UpgradeType t : UpgradeType.values()) upgradeLevels.put(t, 0);
    }

    public void markDirty()  { this.dirty = true;  }
    public void clearDirty() { this.dirty = false; }
    public boolean isDirty() { return dirty; }

    public long getSeasonCoinsEarned() {
        try {
            PlayerData data = GlobalPlayer.get(uuid);
            if (data == null) return 0L;
            java.lang.reflect.Method m = data.getClass().getMethod("getSeasonCoins");
            Object result = m.invoke(data);
            if (result instanceof Number n) return n.longValue();
        } catch (Exception ignored) {}
        return 0L;
    }

    public int getPrestigeLevel() {
        try {
            PlayerData data = GlobalPlayer.get(uuid);
            if (data != null) return data.getPrestige();
        } catch (Exception ignored) {}
        return 0;
    }

    public double getKD() {
        if (lifetimeDeaths == 0) return lifetimeKills;
        return Math.round((double) lifetimeKills / lifetimeDeaths * 100.0) / 100.0;
    }

    public int getUpgradeLevel(UpgradeType type) {
        return upgradeLevels.getOrDefault(type, 0);
    }

    public void setUpgradeLevel(UpgradeType type, int level) {
        upgradeLevels.put(type, level);
        markDirty();
    }

    public boolean isInTeam() { return teamId != null; }

    public void resetSessionSellTracking() {
        this.sessionSoldValue       = 0;
        this.sessionSellWindowStart = System.currentTimeMillis();
    }

    public void incrementLifetimeKills()        { lifetimeKills++;  seasonKills++;  markDirty(); }
    public void incrementLifetimeDeaths()       { lifetimeDeaths++; seasonDeaths++; markDirty(); }
    public void incrementBountiesClaimed()      { lifetimeBountiesClaimed++; seasonBountiesClaimed++; markDirty(); }
    public void incrementBountiesPlacedOnSelf() { bountiesPlacedOnSelf++; markDirty(); }

    public UUID   getUuid()                  { return uuid; }
    public String getLastKnownName()         { return lastKnownName; }
    public void   setLastKnownName(String n) { this.lastKnownName = n; }

    public long getBankBalance()         { return bankBalance; }
    public void setBankBalance(long v)   { this.bankBalance = Math.max(0, v); markDirty(); }
    public long getBankCapacity()        { return bankCapacity; }
    public void setBankCapacity(long v)  { this.bankCapacity = v; markDirty(); }

    public InvestmentVault getInvestmentVault()                  { return investmentVault; }
    public void            setInvestmentVault(InvestmentVault v) { this.investmentVault = v; markDirty(); }

    public long getSessionSoldValue()             { return sessionSoldValue; }
    public void setSessionSoldValue(long v)       { this.sessionSoldValue = v; }
    public long getSessionSellWindowStart()       { return sessionSellWindowStart; }
    public void setSessionSellWindowStart(long v) { this.sessionSellWindowStart = v; }

    public int  getLifetimeKills()                { return lifetimeKills; }
    public void setLifetimeKills(int v)           { this.lifetimeKills = v; }
    public int  getLifetimeDeaths()               { return lifetimeDeaths; }
    public void setLifetimeDeaths(int v)          { this.lifetimeDeaths = v; }
    public int  getLifetimeBountiesClaimed()      { return lifetimeBountiesClaimed; }
    public void setLifetimeBountiesClaimed(int v) { this.lifetimeBountiesClaimed = v; }
    public int  getBountiesPlacedOnSelf()         { return bountiesPlacedOnSelf; }
    public void setBountiesPlacedOnSelf(int v)    { this.bountiesPlacedOnSelf = v; }

    public int  getSeasonKills()                  { return seasonKills; }
    public void setSeasonKills(int v)             { this.seasonKills = v; }
    public int  getSeasonDeaths()                 { return seasonDeaths; }
    public void setSeasonDeaths(int v)            { this.seasonDeaths = v; }
    public int  getSeasonBountiesClaimed()        { return seasonBountiesClaimed; }
    public void setSeasonBountiesClaimed(int v)   { this.seasonBountiesClaimed = v; }
    public int  getCurrentSeason()                { return currentSeason; }
    public void setCurrentSeason(int v)           { this.currentSeason = v; markDirty(); }

    public Map<UpgradeType, Integer> getUpgradeLevels() { return upgradeLevels; }

    public UUID getTeamId()        { return teamId; }
    public void setTeamId(UUID id) { this.teamId = id; markDirty(); }

    public void resetSeasonStats(int newSeason) {
        this.currentSeason = newSeason;
        this.seasonKills   = 0;
        this.seasonDeaths  = 0;
        this.seasonBountiesClaimed = 0;
        markDirty();
    }

    public long getShardBalance()               { return shardBalance; }
    public void setShardBalance(long v)         { this.shardBalance = Math.max(0, v); markDirty(); }
    public long getLifetimeShardsEarned()       { return lifetimeShardsEarned; }
    public void addLifetimeShardBalance(long v) { this.lifetimeShardsEarned += v; markDirty(); }

    public long getSavingsBalance()         { return savingsBalance; }
    public void setSavingsBalance(long v)   { this.savingsBalance = Math.max(0, v); markDirty(); }
    public long getSavingsCapacity()        { return savingsCapacity > 0 ? savingsCapacity : 50000L; }
    public void setSavingsCapacity(long v)  { this.savingsCapacity = v; markDirty(); }

    public long getLoanPrincipal()                   { return loanPrincipal; }
    public void setLoanPrincipal(long v)             { this.loanPrincipal = v; markDirty(); }
    public long getLoanBalance()                     { return loanBalance; }
    public void setLoanBalance(long v)               { this.loanBalance = Math.max(0, v); markDirty(); }
    public long getLoanTakenTimestamp()              { return loanTakenTimestamp; }
    public void setLoanTakenTimestamp(long v)        { this.loanTakenTimestamp = v; markDirty(); }
    public long getLoanLastCompoundTimestamp()       { return loanLastCompoundTimestamp; }
    public void setLoanLastCompoundTimestamp(long v) { this.loanLastCompoundTimestamp = v; markDirty(); }
    public boolean hasActiveLoan()                   { return loanBalance > 0; }

    public int  getCreditScore()      { return creditScore > 0 ? creditScore : 500; }
    public void setCreditScore(int v) { this.creditScore = v; markDirty(); }
    public long getNotoriety()        { return notoriety; }
    public void addNotoriety(long v)  { this.notoriety += v; markDirty(); }
    public int  getTierNumber()       { return tierNumber < 1 ? 1 : tierNumber; }
    public void setTierNumber(int v)  { this.tierNumber = v; markDirty(); }

    public java.util.List<AppliedEnchant> getEnchants(GearSlot slot) {
        return enchants.computeIfAbsent(slot, k -> new java.util.ArrayList<>());
    }
    public void setEnchants(GearSlot slot, java.util.List<AppliedEnchant> list) {
        enchants.put(slot, list); markDirty();
    }
    public java.util.Map<GearSlot, java.util.List<AppliedEnchant>> getAllEnchants() { return enchants; }

    public int getVanillaEnchantLevel(String enchantKey) {
        return vanillaEnchantLevels.getOrDefault(enchantKey, 0);
    }
    public void setVanillaEnchantLevel(String key, int level) {
        vanillaEnchantLevels.put(key, level); markDirty();
    }
    public java.util.Map<String, Integer> getVanillaEnchantLevels() { return vanillaEnchantLevels; }

    public String getOwnedSetPiece(SetPiece piece) {
        return ownedSetPieces.get(piece);
    }

    public void setOwnedSetPiece(SetPiece piece, String setId) {
        if (setId == null) ownedSetPieces.remove(piece);
        else ownedSetPieces.put(piece, setId);
        markDirty();
    }

    public Map<SetPiece, String> getOwnedSetPieces() {
        return ownedSetPieces;
    }

    public void setAllOwnedSetPieces(Map<SetPiece, String> data) {
        ownedSetPieces.clear();
        if (data != null) ownedSetPieces.putAll(data);
    }
}
