package net.glimzo.clashbox.bank;

import me.pikashrey.glimzocore.utilities.chat.CC;
import net.glimzo.clashbox.core.ClashBoxPlugin;
import net.glimzo.clashbox.economy.TransactionResult;
import net.glimzo.clashbox.player.ClashBoxProfile;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class BankCommandHandler implements Listener {

    private final ClashBoxPlugin plugin;
    private final BankConfig     bankCfg;
    private final ShardEconomy   shards;
    private final SavingsManager savings;
    private final LoanManager    loans;
    private final CreditManager  credit;
    private final TransactionLogger txLog;

    private final Map<UUID, PendingAction> awaitingInput = new ConcurrentHashMap<>();

    private static final DateTimeFormatter TS_FMT =
            DateTimeFormatter.ofPattern("dd/MM HH:mm");

    public BankCommandHandler(ClashBoxPlugin plugin,
                              BankConfig bankCfg, ShardEconomy shards,
                              SavingsManager savings, LoanManager loans,
                              CreditManager credit, TransactionLogger txLog) {
        this.plugin  = plugin;
        this.bankCfg = bankCfg;
        this.shards  = shards;
        this.savings = savings;
        this.loans   = loans;
        this.credit  = credit;
        this.txLog   = txLog;
    }

    public void handleCommand(Player player, String[] args) {
        if (args.length == 0) {
            new BankMenu(plugin, player).open();
            return;
        }

        switch (args[0].toLowerCase()) {
            case "deposit", "dep"   -> handleDeposit(player, args);
            case "withdraw", "with" -> handleWithdraw(player, args);
            case "invest"           -> handleInvest(player, args);
            case "claim"            -> handleClaim(player);
            case "loan"             -> handleLoan(player, args);
            case "repay"            -> handleRepay(player, args);
            case "statement", "log" -> showStatement(player, 10);
            case "balance", "bal"   -> showBalance(player);
            case "cheque", "check"  -> handleCheque(player, args);
            default                 -> sendHelp(player);
        }
    }

    private void handleDeposit(Player player, String[] args) {
        if (args.length < 2) { awaitDepositInput(player); return; }
        long amount = parseAmount(args[1], shards.getBalance(player.getUniqueId()));
        if (amount <= 0) { player.sendMessage(CC.translate("&cInvalid amount.")); return; }
        TransactionResult r = savings.deposit(player.getUniqueId(), amount);
        player.sendMessage(CC.translate(r.getMessage()));
    }

    private void handleWithdraw(Player player, String[] args) {
        if (args.length < 2) { awaitWithdrawInput(player); return; }
        ClashBoxProfile profile = plugin.getProfileManager().getProfile(player);
        long max = profile != null ? profile.getSavingsBalance() : 0;
        long amount = parseAmount(args[1], max);
        if (amount <= 0) { player.sendMessage(CC.translate("&cInvalid amount.")); return; }
        TransactionResult r = savings.withdraw(player.getUniqueId(), amount);
        player.sendMessage(CC.translate(r.getMessage()));
    }

    private void handleInvest(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(CC.translate("&cUsage: /bank invest <amount> <2h|8h|24h|72h>"));
            return;
        }
        ClashBoxProfile profile = plugin.getProfileManager().getProfile(player);
        long max = profile != null ? profile.getSavingsBalance() : 0;
        long amount   = parseAmount(args[1], max);
        long duration = parseDuration(args[2]);
        if (amount <= 0 || duration <= 0) {
            player.sendMessage(CC.translate("&cInvalid amount or duration. Use: 2h, 8h, 24h, 72h"));
            return;
        }
        TransactionResult r = savings.lockInvestment(player.getUniqueId(), amount, duration);
        player.sendMessage(CC.translate(r.getMessage()));
    }

    private void handleClaim(Player player) {
        TransactionResult r = savings.claimInvestment(player.getUniqueId());
        player.sendMessage(CC.translate(r.getMessage()));
    }

    private void handleLoan(Player player, String[] args) {
        if (args.length < 2 || args[1].equalsIgnoreCase("info")) {
            showLoanInfo(player); return;
        }
        long amount = parseAmount(args[1], credit.calculateMaxLoan(player.getUniqueId()));
        if (amount <= 0) { player.sendMessage(CC.translate("&cInvalid amount.")); return; }
        TransactionResult r = loans.takeLoan(player.getUniqueId(), amount);
        player.sendMessage(CC.translate(r.getMessage()));
    }

    private void handleRepay(Player player, String[] args) {
        if (args.length < 2) { awaitRepayInput(player); return; }
        long owed = loans.getLoanBalance(player.getUniqueId());
        long amount = parseAmount(args[1], owed);
        if (amount <= 0) { player.sendMessage(CC.translate("&cInvalid amount.")); return; }
        TransactionResult r = loans.repayLoan(player.getUniqueId(), amount);
        player.sendMessage(CC.translate(r.getMessage()));
    }

    private void handleCheque(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(CC.translate("&cUsage: &e/bank cheque <amount>"));
            player.sendMessage(CC.translate("&7Writes a cheque deducting shards from your wallet."));
            player.sendMessage(CC.translate("&7Anyone holding the cheque can use &e/deposit&7."));
            return;
        }
        long balance = shards.getBalance(player.getUniqueId());
        long amount  = parseAmount(args[1], balance);
        if (amount <= 0) {
            player.sendMessage(CC.translate("&cInvalid amount.")); return;
        }
        if (amount > balance) {
            player.sendMessage(CC.translate("&cYou don't have enough shards. Balance: " +
                    shards.formatShort(balance)));
            return;
        }
        boolean ok = plugin.getChequeManager().writeCheque(player, amount);
        if (ok) {
            player.sendMessage(CC.translate("&aCheque written for " + shards.format(amount) +
                    " &a- shards deducted from your wallet."));
            player.sendMessage(CC.translate("&7Give the paper to someone and they can use &e/deposit&7."));
            player.playSound(player.getLocation(), org.bukkit.Sound.LEVEL_UP, 0.5f, 1.4f);
        } else {
            player.sendMessage(CC.translate("&cFailed to write cheque. Do you have enough shards?"));
        }
    }

    private void showBalance(Player player) {
        ClashBoxProfile profile = plugin.getProfileManager().getProfile(player);
        if (profile == null) return;

        player.sendMessage(CC.translate("&8&m                                        "));
        player.sendMessage(CC.translate("  &b&lCLASHBOX BANK &8- &7" + player.getName()));
        player.sendMessage(CC.translate("&8&m                                        "));
        player.sendMessage(CC.translate("  &7Wallet:   &b" + shards.formatShort(shards.getBalance(player.getUniqueId()))));
        player.sendMessage(CC.translate("  &7Savings:  &a" + shards.formatShort(profile.getSavingsBalance()) +
                " &8/ &7" + shards.formatShort(profile.getSavingsCapacity())));

        if (profile.getInvestmentVault() != null) {
            var vault = profile.getInvestmentVault();
            String status = vault.isMatured() ? "&a&lREADY" : "&e" + vault.getTimeRemainingFormatted();
            player.sendMessage(CC.translate("  &7Vault:    &6" + shards.formatShort(vault.getLockedAmount()) +
                    " &8-> &a" + shards.formatShort(vault.getPayout()) +
                    " &8(" + status + "&8)"));
        } else {
            player.sendMessage(CC.translate("  &7Vault:    &8None &7- use &e/bank invest"));
        }

        if (profile.getLoanBalance() > 0) {
            player.sendMessage(CC.translate("  &7Loan:     &c&l-" + shards.formatShort(profile.getLoanBalance()) +
                    " &7(repay: &e/bank repay all&7)"));
        }

        player.sendMessage(CC.translate("  &7Credit:   " + credit.formatScore(player.getUniqueId())));
        player.sendMessage(CC.translate("&8&m                                        "));
    }

    private void showLoanInfo(Player player) {
        ClashBoxProfile profile = plugin.getProfileManager().getProfile(player);
        if (profile == null) return;

        long maxLoan = credit.calculateMaxLoan(player.getUniqueId());
        BankConfig.CreditTier tier = credit.getTier(player.getUniqueId());

        player.sendMessage(CC.translate("&8&m                                        "));
        player.sendMessage(CC.translate("  &e&lLOAN INFORMATION"));
        player.sendMessage(CC.translate("&8&m                                        "));

        if (profile.getLoanBalance() > 0) {
            long sinceMs  = System.currentTimeMillis() - profile.getLoanTakenTimestamp();
            long hoursAgo = sinceMs / 3_600_000;
            player.sendMessage(CC.translate("  &7Status:    &c&lACTIVE"));
            player.sendMessage(CC.translate("  &7Principal: &f" + shards.formatShort(profile.getLoanPrincipal())));
            player.sendMessage(CC.translate("  &7Interest:  &c+" + shards.formatShort(profile.getLoanBalance() - profile.getLoanPrincipal())));
            player.sendMessage(CC.translate("  &7Total owed: &c&l" + shards.formatShort(profile.getLoanBalance())));
            player.sendMessage(CC.translate("  &7Taken:     &7" + hoursAgo + "h ago"));
            player.sendMessage(CC.translate("  &7Rate:      &c" + (int)(tier.interestRatePerCompound() * 100) + "% &7per 6h"));
        } else {
            player.sendMessage(CC.translate("  &7Status:    &aNo active loan"));
            player.sendMessage(CC.translate("  &7Max loan:  &e" + shards.formatShort(maxLoan)));
            player.sendMessage(CC.translate("  &7Rate:      &c" + (int)(tier.interestRatePerCompound() * 100) + "% &7per 6h (compounds)"));
        }

        player.sendMessage(CC.translate("  &7Credit:    " + credit.formatScore(player.getUniqueId())));
        player.sendMessage(CC.translate("&8&m                                        "));
    }

    public void showStatement(Player player, int limit) {
        player.sendMessage(CC.translate("&8[&bBank&8] &7Fetching statement..."));

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            List<TransactionLogger.TransactionEntry> entries =
                    txLog.getHistory(player.getUniqueId(), limit);

            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;

                player.sendMessage(CC.translate("&8&m                                        "));
                player.sendMessage(CC.translate("  &b&lBANK STATEMENT &8- Last " + limit));
                player.sendMessage(CC.translate("&8&m                                        "));

                if (entries.isEmpty()) {
                    player.sendMessage(CC.translate("  &7No transactions yet."));
                } else {
                    for (var entry : entries) {
                        String sign = entry.amount() >= 0 ? "&a+" : "&c";
                        String ts   = formatTimestamp(entry.timestamp());
                        String type = formatType(entry.type());
                        player.sendMessage(CC.translate(
                                "  &8[&7" + ts + "&8] " + type + " " +
                                sign + shards.formatShort(Math.abs(entry.amount()))));
                    }
                }

                player.sendMessage(CC.translate("&8&m                                        "));
            });
        });
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        PendingAction pending = awaitingInput.remove(player.getUniqueId());
        if (pending == null) return;

        event.setCancelled(true);
        String input = event.getMessage().trim();

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            long amount = parseAmount(input, pending.maxAmount());
            if (amount <= 0) {
                player.sendMessage(CC.translate("&cInvalid amount. Cancelled."));
                return;
            }

            if (pending.type() == ActionType.CHEQUE) {
                boolean ok = plugin.getChequeManager().writeCheque(player, amount);
                if (ok) {
                    player.sendMessage(CC.translate("&aCheque written for " +
                            shards.format(amount) + " &a- shards deducted from your wallet."));
                    player.sendMessage(CC.translate("&7Give the paper to someone and they can use &e/deposit&7."));
                    player.playSound(player.getLocation(), org.bukkit.Sound.LEVEL_UP, 0.6f, 1.4f);
                } else {
                    player.sendMessage(CC.translate("&cNot enough shards in your wallet."));
                }
                return;
            }

            TransactionResult result = switch (pending.type()) {
                case DEPOSIT  -> savings.deposit(player.getUniqueId(), amount);
                case WITHDRAW -> savings.withdraw(player.getUniqueId(), amount);
                case INVEST   -> savings.lockInvestment(player.getUniqueId(), amount, pending.extra());
                case LOAN     -> loans.takeLoan(player.getUniqueId(), amount);
                case REPAY    -> loans.repayLoan(player.getUniqueId(), amount);
                case CHEQUE   -> throw new IllegalStateException("unreachable");
            };

            player.sendMessage(CC.translate(result.getMessage()));
            if (result.isSuccess()) {
                player.playSound(player.getLocation(), org.bukkit.Sound.LEVEL_UP, 0.6f, 1.2f);
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        awaitingInput.remove(event.getPlayer().getUniqueId());
    }

    public void awaitDepositInput(Player p) {
        long max = shards.getBalance(p.getUniqueId());
        awaitingInput.put(p.getUniqueId(), new PendingAction(ActionType.DEPOSIT, max, 0));
    }

    public void awaitWithdrawInput(Player p) {
        ClashBoxProfile profile = plugin.getProfileManager().getProfile(p);
        long max = profile != null ? profile.getSavingsBalance() : 0;
        awaitingInput.put(p.getUniqueId(), new PendingAction(ActionType.WITHDRAW, max, 0));
    }

    public void awaitInvestInput(Player p, long duration) {
        ClashBoxProfile profile = plugin.getProfileManager().getProfile(p);
        long max = profile != null ? profile.getSavingsBalance() : 0;
        awaitingInput.put(p.getUniqueId(), new PendingAction(ActionType.INVEST, max, duration));
    }

    public void awaitLoanInput(Player p) {
        long max = credit.calculateMaxLoan(p.getUniqueId());
        awaitingInput.put(p.getUniqueId(), new PendingAction(ActionType.LOAN, max, 0));
    }

    public void awaitRepayInput(Player p) {
        long max = loans.getLoanBalance(p.getUniqueId());
        awaitingInput.put(p.getUniqueId(), new PendingAction(ActionType.REPAY, max, 0));
    }

    public void awaitChequeInput(Player p) {
        long max = shards.getBalance(p.getUniqueId());
        awaitingInput.put(p.getUniqueId(), new PendingAction(ActionType.CHEQUE, max, 0));
    }

    private void sendHelp(Player player) {
        player.sendMessage(CC.translate("&b&lClashBox Bank Commands:"));
        player.sendMessage(CC.translate("&e/bank &7- Open bank GUI"));
        player.sendMessage(CC.translate("&e/bank balance &7- Quick balance view"));
        player.sendMessage(CC.translate("&e/bank deposit <amount|all> &7- Deposit to savings"));
        player.sendMessage(CC.translate("&e/bank withdraw <amount|all> &7- Withdraw from savings"));
        player.sendMessage(CC.translate("&e/bank invest <amount> <2h|8h|24h|72h> &7- Lock investment"));
        player.sendMessage(CC.translate("&e/bank claim &7- Claim matured investment"));
        player.sendMessage(CC.translate("&e/bank loan <amount> &7- Take a loan"));
        player.sendMessage(CC.translate("&e/bank loan info &7- Loan details"));
        player.sendMessage(CC.translate("&e/bank repay <amount|all> &7- Repay loan"));
        player.sendMessage(CC.translate("&e/bank statement &7- Transaction history"));
        player.sendMessage(CC.translate("&e/bank cheque <amount> &7- Write a transferable cheque"));
        player.sendMessage(CC.translate("&e/deposit [wallet|savings] &7- Deposit held cheque"));
    }

    private long parseAmount(String s, long max) {
        if (s.equalsIgnoreCase("all") || s.equalsIgnoreCase("max")) return max;
        try { return Math.max(0, Long.parseLong(s.replace(",", ""))); }
        catch (NumberFormatException e) { return -1; }
    }

    private long parseDuration(String s) {
        return switch (s.toLowerCase()) {
            case "2h"  -> 7200L;
            case "8h"  -> 28800L;
            case "24h" -> 86400L;
            case "72h" -> 259200L;
            default    -> -1L;
        };
    }

    private String formatTimestamp(long ts) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(ts),
                ZoneId.systemDefault()).format(TS_FMT);
    }

    private String formatType(String type) {
        return switch (type) {
            case "SAVINGS_DEPOSIT"    -> "&a[Deposit]     ";
            case "SAVINGS_WITHDRAW"   -> "&c[Withdraw]    ";
            case "SAVINGS_INTEREST"   -> "&2[Interest]    ";
            case "INVESTMENT_LOCK"    -> "&6[Invest Lock] ";
            case "INVESTMENT_CLAIM"   -> "&6[Invest Claim]";
            case "LOAN_TAKEN"         -> "&e[Loan]        ";
            case "LOAN_REPAYMENT"     -> "&a[Repayment]   ";
            case "LOAN_INTEREST"      -> "&c[Loan Int]    ";
            case "LOAN_DEATH_PENALTY" -> "&4[Death Pen]   ";
            case "SHARD_ADD"          -> "&b[Earned]      ";
            case "SHARD_REMOVE"       -> "&7[Spent]       ";
            default                   -> "&7[" + type + "]";
        };
    }

    private enum ActionType { DEPOSIT, WITHDRAW, INVEST, LOAN, REPAY, CHEQUE }

    private record PendingAction(ActionType type, long maxAmount, long extra) {}
}
