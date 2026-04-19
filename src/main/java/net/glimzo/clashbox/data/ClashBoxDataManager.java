package net.glimzo.clashbox.data;

import me.pikashrey.glimzocore.GlimzoCore;
import net.glimzo.clashbox.core.ClashBoxPlugin;
import net.glimzo.clashbox.economy.InvestmentVault;
import net.glimzo.clashbox.enchant.AppliedEnchant;
import net.glimzo.clashbox.enchant.GearSlot;
import net.glimzo.clashbox.player.ClashBoxProfile;
import net.glimzo.clashbox.progression.UpgradeType;
import net.glimzo.clashbox.sets.SetPiece;

import java.sql.*;
import java.util.*;

public class ClashBoxDataManager {

    private final ClashBoxPlugin plugin;

    public ClashBoxDataManager(ClashBoxPlugin plugin) {
        this.plugin = plugin;
    }

    public void init() {
        createTable();
        addMissingColumns();
        plugin.getLogger().info("[ClashBox] MySQL table initialised.");
    }

    private void createTable() {
        String sql = """
                CREATE TABLE IF NOT EXISTS clashbox_players (
                    uuid              VARCHAR(36)  NOT NULL PRIMARY KEY,
                    bank_balance      BIGINT       NOT NULL DEFAULT 0,
                    bank_capacity     BIGINT       NOT NULL DEFAULT 50000,
                    vault_amount      BIGINT       NOT NULL DEFAULT 0,
                    vault_lock_ts     BIGINT       NOT NULL DEFAULT 0,
                    vault_unlock_ts   BIGINT       NOT NULL DEFAULT 0,
                    vault_rate        DOUBLE       NOT NULL DEFAULT 0.0,
                    upgrade_sword     INT          NOT NULL DEFAULT 0,
                    upgrade_pickaxe   INT          NOT NULL DEFAULT 0,
                    upgrade_armor     INT          NOT NULL DEFAULT 0,
                    upgrade_bank_cap  INT          NOT NULL DEFAULT 0,
                    upgrade_carry     INT          NOT NULL DEFAULT 0,
                    lifetime_kills    INT          NOT NULL DEFAULT 0,
                    lifetime_deaths   INT          NOT NULL DEFAULT 0,
                    lifetime_bounties INT          NOT NULL DEFAULT 0,
                    season_kills      INT          NOT NULL DEFAULT 0,
                    season_deaths     INT          NOT NULL DEFAULT 0,
                    season_bounties   INT          NOT NULL DEFAULT 0,
                    current_season    INT          NOT NULL DEFAULT 1,
                    team_id           VARCHAR(36)           DEFAULT NULL,
                    shard_balance     BIGINT       NOT NULL DEFAULT 0,
                    lifetime_shards   BIGINT       NOT NULL DEFAULT 0,
                    savings_balance   BIGINT       NOT NULL DEFAULT 0,
                    savings_capacity  BIGINT       NOT NULL DEFAULT 50000,
                    loan_principal    BIGINT       NOT NULL DEFAULT 0,
                    loan_balance      BIGINT       NOT NULL DEFAULT 0,
                    loan_taken_ts     BIGINT       NOT NULL DEFAULT 0,
                    loan_compound_ts  BIGINT       NOT NULL DEFAULT 0,
                    credit_score      INT          NOT NULL DEFAULT 500,
                    notoriety         BIGINT       NOT NULL DEFAULT 0,
                    tier_number       INT          NOT NULL DEFAULT 1,
                    enchants_json     TEXT                  DEFAULT NULL,
                    owned_sets        TEXT                  DEFAULT NULL
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                """;
        try (Connection con = getConnection(); Statement stmt = con.createStatement()) {
            stmt.executeUpdate(sql);
        } catch (SQLException e) {
            plugin.getLogger().severe("[ClashBox] Failed to create table: " + e.getMessage());
        }
    }

    private void addMissingColumns() {
        String[] alters = {
            "ALTER TABLE clashbox_players ADD COLUMN IF NOT EXISTS owned_sets TEXT DEFAULT NULL"
        };
        try (Connection con = getConnection(); Statement stmt = con.createStatement()) {
            for (String sql : alters) {
                try { stmt.executeUpdate(sql); } catch (SQLException ignored) {}
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("[ClashBox] Could not add missing columns: " + e.getMessage());
        }
    }

    public ClashBoxProfile loadProfile(UUID uuid, String name) {
        long defaultCap = plugin.getCBConfig().getBankDefaultCapacity();
        int  season     = plugin.getCBConfig().getCurrentSeason();
        ClashBoxProfile profile = new ClashBoxProfile(uuid, name, defaultCap, season);

        String sql = "SELECT * FROM clashbox_players WHERE uuid = ?";
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    profile.setBankBalance(rs.getLong("bank_balance"));
                    profile.setBankCapacity(rs.getLong("bank_capacity"));

                    long vaultAmount = rs.getLong("vault_amount");
                    if (vaultAmount > 0) {
                        profile.setInvestmentVault(new InvestmentVault(
                                vaultAmount,
                                rs.getLong("vault_lock_ts"),
                                rs.getLong("vault_unlock_ts"),
                                rs.getDouble("vault_rate")));
                    }

                    profile.setUpgradeLevel(UpgradeType.SWORD_LEVEL,      rs.getInt("upgrade_sword"));
                    profile.setUpgradeLevel(UpgradeType.PICKAXE_LEVEL,    rs.getInt("upgrade_pickaxe"));
                    profile.setUpgradeLevel(UpgradeType.ARMOR_LEVEL,      rs.getInt("upgrade_armor"));
                    profile.setUpgradeLevel(UpgradeType.BANK_CAPACITY,    rs.getInt("upgrade_bank_cap"));
                    profile.setUpgradeLevel(UpgradeType.CARRY_PROTECTION, rs.getInt("upgrade_carry"));

                    profile.setLifetimeKills(rs.getInt("lifetime_kills"));
                    profile.setLifetimeDeaths(rs.getInt("lifetime_deaths"));
                    profile.setLifetimeBountiesClaimed(rs.getInt("lifetime_bounties"));
                    profile.setSeasonKills(rs.getInt("season_kills"));
                    profile.setSeasonDeaths(rs.getInt("season_deaths"));
                    profile.setSeasonBountiesClaimed(rs.getInt("season_bounties"));
                    profile.setCurrentSeason(rs.getInt("current_season"));

                    String teamId = rs.getString("team_id");
                    if (teamId != null) {
                        try { profile.setTeamId(UUID.fromString(teamId)); }
                        catch (IllegalArgumentException ignored) {}
                    }

                    profile.setShardBalance(rs.getLong("shard_balance"));
                    profile.addLifetimeShardBalance(rs.getLong("lifetime_shards"));
                    profile.setSavingsBalance(rs.getLong("savings_balance"));
                    profile.setSavingsCapacity(rs.getLong("savings_capacity"));
                    profile.setLoanPrincipal(rs.getLong("loan_principal"));
                    profile.setLoanBalance(rs.getLong("loan_balance"));
                    profile.setLoanTakenTimestamp(rs.getLong("loan_taken_ts"));
                    profile.setLoanLastCompoundTimestamp(rs.getLong("loan_compound_ts"));
                    profile.setCreditScore(rs.getInt("credit_score"));
                    profile.addNotoriety(rs.getLong("notoriety"));
                    profile.setTierNumber(rs.getInt("tier_number"));

                    deserializeEnchants(profile, rs.getString("enchants_json"));
                    deserializeOwnedSets(profile, rs.getString("owned_sets"));
                } else {
                    insertNewProfile(con, uuid);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("[ClashBox] Failed to load profile for " + uuid + ": " + e.getMessage());
        }

        profile.clearDirty();
        return profile;
    }

    private void insertNewProfile(Connection con, UUID uuid) throws SQLException {
        String sql = "INSERT INTO clashbox_players (uuid) VALUES (?)";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
        }
    }

    public void saveProfile(ClashBoxProfile profile) {
        if (!profile.isDirty()) return;

        String sql = """
                INSERT INTO clashbox_players
                    (uuid, bank_balance, bank_capacity,
                     vault_amount, vault_lock_ts, vault_unlock_ts, vault_rate,
                     upgrade_sword, upgrade_pickaxe, upgrade_armor,
                     upgrade_bank_cap, upgrade_carry,
                     lifetime_kills, lifetime_deaths, lifetime_bounties,
                     season_kills, season_deaths, season_bounties,
                     current_season, team_id,
                     shard_balance, lifetime_shards,
                     savings_balance, savings_capacity,
                     loan_principal, loan_balance, loan_taken_ts, loan_compound_ts,
                     credit_score, notoriety, tier_number, enchants_json, owned_sets)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                ON DUPLICATE KEY UPDATE
                    bank_balance     = VALUES(bank_balance),
                    bank_capacity    = VALUES(bank_capacity),
                    vault_amount     = VALUES(vault_amount),
                    vault_lock_ts    = VALUES(vault_lock_ts),
                    vault_unlock_ts  = VALUES(vault_unlock_ts),
                    vault_rate       = VALUES(vault_rate),
                    upgrade_sword    = VALUES(upgrade_sword),
                    upgrade_pickaxe  = VALUES(upgrade_pickaxe),
                    upgrade_armor    = VALUES(upgrade_armor),
                    upgrade_bank_cap = VALUES(upgrade_bank_cap),
                    upgrade_carry    = VALUES(upgrade_carry),
                    lifetime_kills   = VALUES(lifetime_kills),
                    lifetime_deaths  = VALUES(lifetime_deaths),
                    lifetime_bounties= VALUES(lifetime_bounties),
                    season_kills     = VALUES(season_kills),
                    season_deaths    = VALUES(season_deaths),
                    season_bounties  = VALUES(season_bounties),
                    current_season   = VALUES(current_season),
                    team_id          = VALUES(team_id),
                    shard_balance    = VALUES(shard_balance),
                    lifetime_shards  = VALUES(lifetime_shards),
                    savings_balance  = VALUES(savings_balance),
                    savings_capacity = VALUES(savings_capacity),
                    loan_principal   = VALUES(loan_principal),
                    loan_balance     = VALUES(loan_balance),
                    loan_taken_ts    = VALUES(loan_taken_ts),
                    loan_compound_ts = VALUES(loan_compound_ts),
                    credit_score     = VALUES(credit_score),
                    notoriety        = VALUES(notoriety),
                    tier_number      = VALUES(tier_number),
                    enchants_json    = VALUES(enchants_json),
                    owned_sets       = VALUES(owned_sets)
                """;

        InvestmentVault vault = profile.getInvestmentVault();

        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1,  profile.getUuid().toString());
            ps.setLong(2,    profile.getBankBalance());
            ps.setLong(3,    profile.getBankCapacity());
            ps.setLong(4,    vault != null ? vault.getLockedAmount()    : 0L);
            ps.setLong(5,    vault != null ? vault.getLockTimestamp()   : 0L);
            ps.setLong(6,    vault != null ? vault.getUnlockTimestamp() : 0L);
            ps.setDouble(7,  vault != null ? vault.getInterestRate()    : 0.0);
            ps.setInt(8,     profile.getUpgradeLevel(UpgradeType.SWORD_LEVEL));
            ps.setInt(9,     profile.getUpgradeLevel(UpgradeType.PICKAXE_LEVEL));
            ps.setInt(10,    profile.getUpgradeLevel(UpgradeType.ARMOR_LEVEL));
            ps.setInt(11,    profile.getUpgradeLevel(UpgradeType.BANK_CAPACITY));
            ps.setInt(12,    profile.getUpgradeLevel(UpgradeType.CARRY_PROTECTION));
            ps.setInt(13,    profile.getLifetimeKills());
            ps.setInt(14,    profile.getLifetimeDeaths());
            ps.setInt(15,    profile.getLifetimeBountiesClaimed());
            ps.setInt(16,    profile.getSeasonKills());
            ps.setInt(17,    profile.getSeasonDeaths());
            ps.setInt(18,    profile.getSeasonBountiesClaimed());
            ps.setInt(19,    profile.getCurrentSeason());
            ps.setString(20, profile.getTeamId() != null ? profile.getTeamId().toString() : null);
            ps.setLong(21,   profile.getShardBalance());
            ps.setLong(22,   profile.getLifetimeShardsEarned());
            ps.setLong(23,   profile.getSavingsBalance());
            ps.setLong(24,   profile.getSavingsCapacity());
            ps.setLong(25,   profile.getLoanPrincipal());
            ps.setLong(26,   profile.getLoanBalance());
            ps.setLong(27,   profile.getLoanTakenTimestamp());
            ps.setLong(28,   profile.getLoanLastCompoundTimestamp());
            ps.setInt(29,    profile.getCreditScore());
            ps.setLong(30,   profile.getNotoriety());
            ps.setInt(31,    profile.getTierNumber());
            ps.setString(32, serializeEnchants(profile));
            ps.setString(33, serializeOwnedSets(profile));

            ps.executeUpdate();
            profile.clearDirty();

        } catch (SQLException e) {
            plugin.getLogger().severe("[ClashBox] Failed to save profile for " +
                    profile.getLastKnownName() + ": " + e.getMessage());
        }
    }

    public void saveAll() {
        for (ClashBoxProfile p : plugin.getProfileManager().getCachedProfiles()) {
            saveProfile(p);
        }
    }

    private String serializeOwnedSets(ClashBoxProfile profile) {
        try {
            Map<String, String> data = new LinkedHashMap<>();
            for (Map.Entry<SetPiece, String> e : profile.getOwnedSetPieces().entrySet()) {
                data.put(e.getKey().name(), e.getValue());
            }
            return GlimzoCore.getInstance().getGson().toJson(data);
        } catch (Exception e) {
            plugin.getLogger().warning("[ClashBox] Failed to serialize owned_sets: " + e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private void deserializeOwnedSets(ClashBoxProfile profile, String json) {
        if (json == null || json.isEmpty()) return;
        try {
            Map<String, String> data = GlimzoCore.getInstance().getGson()
                    .fromJson(json, Map.class);
            Map<SetPiece, String> result = new java.util.EnumMap<>(SetPiece.class);
            for (Map.Entry<String, String> e : data.entrySet()) {
                SetPiece piece = SetPiece.fromString(e.getKey());
                if (piece != null && e.getValue() != null) result.put(piece, e.getValue());
            }
            profile.setAllOwnedSetPieces(result);
        } catch (Exception e) {
            plugin.getLogger().warning("[ClashBox] Failed to deserialize owned_sets: " + e.getMessage());
        }
    }

    private String serializeEnchants(ClashBoxProfile profile) {
        try {
            Map<String, Object> data = new LinkedHashMap<>();
            Map<String, List<String>> customEnchants = new LinkedHashMap<>();
            for (GearSlot slot : GearSlot.values()) {
                List<AppliedEnchant> enchants = profile.getEnchants(slot);
                if (!enchants.isEmpty()) {
                    List<String> tags = new ArrayList<>();
                    for (AppliedEnchant e : enchants) tags.add(e.toTag());
                    customEnchants.put(slot.name(), tags);
                }
            }
            data.put("custom", customEnchants);
            data.put("vanilla", profile.getVanillaEnchantLevels());
            return GlimzoCore.getInstance().getGson().toJson(data);
        } catch (Exception e) {
            plugin.getLogger().warning("[ClashBox] Failed to serialize enchants: " + e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private void deserializeEnchants(ClashBoxProfile profile, String json) {
        if (json == null || json.isEmpty()) return;
        try {
            Map<String, Object> data = GlimzoCore.getInstance().getGson()
                    .fromJson(json, Map.class);
            Object customRaw = data.get("custom");
            if (customRaw instanceof Map<?, ?> customMap) {
                for (var entry : customMap.entrySet()) {
                    try {
                        GearSlot slot = GearSlot.valueOf(entry.getKey().toString());
                        List<AppliedEnchant> enchants = new ArrayList<>();
                        if (entry.getValue() instanceof List<?> tags) {
                            for (Object tag : tags) {
                                AppliedEnchant ae = AppliedEnchant.fromTag(tag.toString());
                                if (ae != null) enchants.add(ae);
                            }
                        }
                        profile.setEnchants(slot, enchants);
                    } catch (Exception ignored) {}
                }
            }
            Object vanillaRaw = data.get("vanilla");
            if (vanillaRaw instanceof Map<?, ?> vanillaMap) {
                for (var entry : vanillaMap.entrySet()) {
                    try {
                        profile.setVanillaEnchantLevel(entry.getKey().toString(),
                                ((Number) entry.getValue()).intValue());
                    } catch (Exception ignored) {}
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[ClashBox] Failed to deserialize enchants: " + e.getMessage());
        }
    }

    private Connection getConnection() throws SQLException {
        return GlimzoCore.getInstance().getMysqlManager().getConnection();
    }
}
