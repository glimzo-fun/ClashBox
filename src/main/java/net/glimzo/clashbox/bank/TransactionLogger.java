package net.glimzo.clashbox.bank;

import me.pikashrey.glimzocore.GlimzoCore;
import net.glimzo.clashbox.core.ClashBoxPlugin;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class TransactionLogger {

    private final ClashBoxPlugin plugin;
    private final BankConfig     bankCfg;

    public TransactionLogger(ClashBoxPlugin plugin, BankConfig bankCfg) {
        this.plugin  = plugin;
        this.bankCfg = bankCfg;
    }

    public void init() {
        createTable();
    }

    private void createTable() {
        String sql = """
                CREATE TABLE IF NOT EXISTS clashbox_transactions (
                    id          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
                    uuid        VARCHAR(36)  NOT NULL,
                    amount      BIGINT       NOT NULL,
                    type        VARCHAR(32)  NOT NULL,
                    reason      VARCHAR(255) NOT NULL DEFAULT '',
                    timestamp   BIGINT       NOT NULL,
                    INDEX idx_uuid (uuid),
                    INDEX idx_ts   (timestamp)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                """;
        try (Connection con = getConnection();
             Statement stmt = con.createStatement()) {
            stmt.executeUpdate(sql);
        } catch (SQLException e) {
            plugin.getLogger().severe("[ClashBox] Failed to create transactions table: " + e.getMessage());
        }
    }

    public void log(UUID uuid, long amount, String type, String reason) {
        String configKey = typeToConfigKey(type);
        if (configKey != null && !bankCfg.isLoggingEnabled(configKey)) return;

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            String sql = "INSERT INTO clashbox_transactions (uuid, amount, type, reason, timestamp) VALUES (?,?,?,?,?)";
            try (Connection con = getConnection();
                 PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ps.setLong(2,   amount);
                ps.setString(3, type);
                ps.setString(4, reason.length() > 255 ? reason.substring(0, 255) : reason);
                ps.setLong(5,   System.currentTimeMillis());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().warning("[ClashBox] Failed to log transaction: " + e.getMessage());
            }

            pruneOldEntries(uuid);
        });
    }

    public List<TransactionEntry> getHistory(UUID uuid, int limit) {
        List<TransactionEntry> entries = new ArrayList<>();
        String sql = """
                SELECT amount, type, reason, timestamp
                FROM clashbox_transactions
                WHERE uuid = ?
                ORDER BY timestamp DESC
                LIMIT ?
                """;
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    entries.add(new TransactionEntry(
                            rs.getLong("amount"),
                            rs.getString("type"),
                            rs.getString("reason"),
                            rs.getLong("timestamp")
                    ));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("[ClashBox] Failed to fetch transaction history: " + e.getMessage());
        }
        return entries;
    }

    private void pruneOldEntries(UUID uuid) {
        int maxHistory = bankCfg.getMaxTransactionHistory();
        String sql = """
                DELETE FROM clashbox_transactions
                WHERE uuid = ?
                  AND id NOT IN (
                      SELECT id FROM (
                          SELECT id FROM clashbox_transactions
                          WHERE uuid = ?
                          ORDER BY timestamp DESC
                          LIMIT ?
                      ) sub
                  )
                """;
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, uuid.toString());
            ps.setInt(3, maxHistory);
            ps.executeUpdate();
        } catch (SQLException ignored) {}
    }

    private String typeToConfigKey(String type) {
        return switch (type) {
            case "SHARD_ADD", "SHARD_REMOVE" -> "ore-sells";
            case "KILL_REWARD"               -> "kill-rewards";
            case "SAVINGS_DEPOSIT"           -> "deposits";
            case "SAVINGS_WITHDRAW"          -> "withdrawals";
            case "INVESTMENT_LOCK", "INVESTMENT_CLAIM", "INVESTMENT_REINVEST" -> "investments";
            case "LOAN_TAKEN", "LOAN_REPAYMENT", "LOAN_SEASON_DEDUCT",
                 "LOAN_DEATH_PENALTY"        -> "loan-events";
            case "SAVINGS_INTEREST", "LOAN_INTEREST",
                 "LOAN_INTEREST_OFFLINE"     -> "interest";
            default                          -> null;
        };
    }

    private Connection getConnection() throws SQLException {
        return GlimzoCore.getInstance().getMysqlManager().getConnection();
    }

    public record TransactionEntry(long amount, String type, String reason, long timestamp) {
        public String formatTimestamp() {
            java.time.Instant instant = java.time.Instant.ofEpochMilli(timestamp);
            java.time.LocalDateTime ldt = java.time.LocalDateTime.ofInstant(
                    instant, java.time.ZoneId.systemDefault());
            return String.format("%02d/%02d %02d:%02d",
                    ldt.getDayOfMonth(), ldt.getMonthValue(),
                    ldt.getHour(), ldt.getMinute());
        }
    }
}
