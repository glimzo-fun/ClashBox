package net.glimzo.clashbox.bank;

import me.pikashrey.glimzocore.utilities.chat.CC;
import net.glimzo.clashbox.utilities.ConfigFile;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class BankConfig {

    private final JavaPlugin plugin;
    private FileConfiguration cfg;

    public BankConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        plugin.saveResource("bank.yml", false);
        this.cfg = ConfigFile.load(plugin, "bank.yml");
    }

    public String getShardsName()   { return cfg.getString("shards.name",   "Shards"); }
    public String getShardsSymbol() { return cfg.getString("shards.symbol", "◆"); }

    public Material getShardMaterial() {
        try {
            return Material.valueOf(cfg.getString("shards.material", "QUARTZ").toUpperCase());
        } catch (IllegalArgumentException e) {
            return Material.QUARTZ;
        }
    }

    public ItemStack buildShardItem(int amount) {
        ItemStack item = new ItemStack(getShardMaterial(), Math.min(amount, 64));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(CC.translate(cfg.getString("shards.item-name", "&b&l✦ Shard")));
            List<String> rawLore = cfg.getStringList("shards.item-lore");
            List<String> lore = new ArrayList<>();
            for (String l : rawLore) lore.add(CC.translate(l));
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    public String format(long amount) {
        return getShardsSymbol() + " " + String.format("%,d", amount);
    }

    public double getDeathLossPercent(String zone) {
        return cfg.getDouble("wallet.death-loss-percent." + zone, 0.0);
    }

    public long getHourlySoftcap() {
        return cfg.getLong("wallet.hourly-softcap", 10000);
    }

    public double getSoftcapReduction() {
        return cfg.getDouble("wallet.softcap-reduction", 0.85);
    }

    public double getSavingsInterestRatePerHour() {
        return cfg.getDouble("savings.interest-rate-per-hour", 0.001);
    }

    public long getSavingsDefaultCapacity() {
        return cfg.getLong("savings.default-capacity", 50000);
    }

    public double getDepositTaxPercent() {
        return cfg.getDouble("savings.deposit-tax-percent", 0.5);
    }

    public long getCapacityPerUpgradeLevel() {
        return cfg.getLong("savings.capacity-per-upgrade-level", 50000);
    }

    public long getMaxInvestment() {
        return cfg.getLong("investment.max-amount", 500000);
    }

    public boolean isAutoReinvestEnabled() {
        return cfg.getBoolean("investment.auto-reinvest", false);
    }

    public Map<Long, Double> getInvestmentTiers() {
        Map<Long, Double> tiers = new LinkedHashMap<>();
        ConfigurationSection sec = cfg.getConfigurationSection("investment.tiers");
        if (sec == null) return tiers;
        for (String key : sec.getKeys(false)) {
            try {
                tiers.put(Long.parseLong(key), sec.getDouble(key));
            } catch (NumberFormatException ignored) {}
        }
        return tiers;
    }

    public boolean isLoansEnabled()        { return cfg.getBoolean("loans.enabled", true); }
    public long    getLoanMinimum()        { return cfg.getLong("loans.minimum", 500); }
    public long    getLoanMaximum()        { return cfg.getLong("loans.maximum", 500000); }
    public long    getCompoundIntervalSeconds() {
        return cfg.getLong("loans.compound-interval-seconds", 21600);
    }
    public double  getLoanDeathPenaltyPercent() {
        return cfg.getDouble("loans.death-penalty-percent", 10.0);
    }
    public boolean isBlockDepositWhileIndebted() {
        return cfg.getBoolean("loans.block-deposit-while-indebted", true);
    }
    public String  getBorrowCapMode() {
        return cfg.getString("loans.borrow-cap-mode", "lifetime");
    }

    public boolean isCreditEnabled()     { return cfg.getBoolean("credit.enabled", true); }
    public int getDefaultCreditScore()   { return cfg.getInt("credit.default-score", 500); }
    public int getMinCreditScore()       { return cfg.getInt("credit.min-score", 300); }
    public int getMaxCreditScore()       { return cfg.getInt("credit.max-score", 850); }

    public int getCreditEvent(String eventKey) {
        return cfg.getInt("credit.events." + eventKey, 0);
    }

    public List<CreditTier> getCreditTiers() {
        List<CreditTier> tiers = new ArrayList<>();
        List<?> rawList = cfg.getList("credit.tiers");
        if (rawList == null) return tiers;
        for (Object obj : rawList) {
            if (!(obj instanceof Map<?, ?> map)) continue;
            int    minScore  = toInt(map.get("min-score"),  300);
            int    maxScore  = toInt(map.get("max-score"),  850);
            double maxBorrow = toDouble(map.get("max-borrow-percent"), 0.35);
            double rate      = toDouble(map.get("interest-rate-per-compound"), 0.10);
            Object rawLabel  = map.containsKey("label") ? map.get("label") : "&7Unknown";
            String label     = rawLabel != null ? rawLabel.toString() : "&7Unknown";
            tiers.add(new CreditTier(minScore, maxScore, maxBorrow, rate, label));
        }
        tiers.sort(Comparator.comparingInt(CreditTier::minScore));
        return tiers;
    }

    public CreditTier getTierForScore(int score) {
        List<CreditTier> tiers = getCreditTiers();
        CreditTier best = tiers.isEmpty() ? new CreditTier(300, 850, 0.35, 0.10, "&7Average") : tiers.get(0);
        for (CreditTier tier : tiers) {
            if (score >= tier.minScore()) best = tier;
        }
        return best;
    }

    public int  getMaxTransactionHistory()          { return cfg.getInt("transactions.max-history", 50); }
    public boolean isLoggingEnabled(String type)    { return cfg.getBoolean("transactions.log-" + type, true); }

    private int toInt(Object o, int def) {
        if (o instanceof Number n) return n.intValue();
        try { return Integer.parseInt(String.valueOf(o)); } catch (Exception e) { return def; }
    }

    private double toDouble(Object o, double def) {
        if (o instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(String.valueOf(o)); } catch (Exception e) { return def; }
    }

    public record CreditTier(
            int    minScore,
            int    maxScore,
            double maxBorrowPercent,
            double interestRatePerCompound,
            String label
    ) {
        public String displayLabel() { return CC.translate(label); }
    }
}
