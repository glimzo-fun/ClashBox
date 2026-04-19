package net.glimzo.clashbox.bank;

import me.pikashrey.glimzocore.GlimzoCore;
import me.pikashrey.glimzocore.menu.menu.GlimzoMenu;
import me.pikashrey.glimzocore.menu.slots.Slot;
import me.pikashrey.glimzocore.utilities.chat.CC;
import me.pikashrey.glimzocore.utilities.item.ItemBuilder;
import net.glimzo.clashbox.core.ClashBoxPlugin;
import net.glimzo.clashbox.economy.InvestmentVault;
import net.glimzo.clashbox.economy.TransactionResult;
import net.glimzo.clashbox.player.ClashBoxProfile;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class BankMenu extends GlimzoMenu {

    private final ClashBoxPlugin plugin;
    private final BankConfig     bankCfg;
    private final ShardEconomy   shards;
    private final SavingsManager savings;
    private final LoanManager    loans;
    private final CreditManager  credit;

    public BankMenu(ClashBoxPlugin plugin, Player player) {
        super(GlimzoCore.getInstance(), player, "&8&lClashBox &b&lBank", 54);
        this.plugin  = plugin;
        this.bankCfg = plugin.getBankConfig();
        this.shards  = plugin.getShardEconomy();
        this.savings = plugin.getSavingsManager();
        this.loans   = plugin.getLoanManager();
        this.credit  = plugin.getCreditManager();
    }

    @Override
    protected void buildContent() {
        fillEmpty();

        ClashBoxProfile profile = plugin.getProfileManager().getProfile(player);
        if (profile == null) return;

        buildWalletSection(profile);
        buildSavingsSection(profile);
        buildInvestmentSection(profile);
        buildLoanSection(profile);
        buildCreditSection(profile);
        buildChequeButton();
        buildStatementButton();
        buildCloseButton();
    }

    private void buildWalletSection(ClashBoxProfile profile) {
        long walletBalance  = shards.getBalance(player.getUniqueId());
        long lifetimeEarned = shards.getLifetimeEarned(player.getUniqueId());

        ItemStack item = new ItemBuilder(bankCfg.getShardMaterial())
                .name("&b&l◆ Wallet")
                .lore(
                        "&8--------------------",
                        "&7Balance: &f" + shards.formatShort(walletBalance),
                        "&7Lifetime earned: &f" + shards.formatShort(lifetimeEarned),
                        "",
                        "&8This is your arena wallet.",
                        "&7Shards are earned from mining",
                        "&7and kills inside the arena.",
                        "&8--------------------"
                )
                .build();

        for (int slot : new int[]{2, 3, 4, 5, 6}) {
            final ItemStack display = item;
            set(new Slot(slot) {
                @Override public ItemStack getItem() { return display; }
            });
        }
    }

    private void buildSavingsSection(ClashBoxProfile profile) {
        boolean blocked = bankCfg.isBlockDepositWhileIndebted() && profile.getLoanBalance() > 0;

        ItemStack info = new ItemBuilder(Material.CHEST)
                .name("&a&l⬡ Savings Bank")
                .lore(
                        "&8--------------------",
                        "&7Balance: &a" + shards.formatShort(profile.getSavingsBalance()),
                        "&7Capacity: &f" + shards.formatShort(profile.getSavingsCapacity()),
                        "&7Interest: &a" + String.format("%.2f", bankCfg.getSavingsInterestRatePerHour() * 100) + "%&7/hr (offline)",
                        "",
                        "&7Deposit tax: &c" + bankCfg.getDepositTaxPercent() + "%",
                        blocked ? "&c⚠ Deposits blocked (active loan)" : "&a✔ Deposits available",
                        "&8--------------------"
                )
                .build();

        ItemStack depositBtn = new ItemBuilder(Material.EMERALD)
                .name("&a▲ Deposit")
                .lore("&7Click to deposit your wallet", "&7to savings (chat input).")
                .build();

        ItemStack withdrawBtn = new ItemBuilder(Material.REDSTONE)
                .name("&c▼ Withdraw")
                .lore("&7Click to withdraw from savings", "&7to your wallet (chat input).")
                .build();

        set(new Slot(10) { @Override public ItemStack getItem() { return info; } });
        set(new Slot(11) { @Override public ItemStack getItem() { return info; } });
        set(new Slot(19) { @Override public ItemStack getItem() { return info; } });
        set(new Slot(20) { @Override public ItemStack getItem() { return info; } });
        set(new Slot(28) { @Override public ItemStack getItem() { return info; } });

        set(new Slot(29) {
            @Override public ItemStack getItem() { return depositBtn; }
            @Override public void onClick(Player p, InventoryClickEvent e) {
                p.closeInventory();
                p.sendMessage(CC.translate("&8[&bBank&8] &7Enter deposit amount in chat (or &eall&7):"));
                plugin.getBankCommandHandler().awaitDepositInput(p);
                p.playSound(p.getLocation(), Sound.NOTE_PLING, 0.6f, 1.2f);
            }
        });

        set(new Slot(37) {
            @Override public ItemStack getItem() { return withdrawBtn; }
            @Override public void onClick(Player p, InventoryClickEvent e) {
                p.closeInventory();
                p.sendMessage(CC.translate("&8[&bBank&8] &7Enter withdrawal amount in chat (or &eall&7):"));
                plugin.getBankCommandHandler().awaitWithdrawInput(p);
                p.playSound(p.getLocation(), Sound.NOTE_PLING, 0.6f, 0.8f);
            }
        });
    }

    private void buildInvestmentSection(ClashBoxProfile profile) {
        InvestmentVault vault = profile.getInvestmentVault();

        if (vault == null) {
            int slot = 15;
            for (var entry : bankCfg.getInvestmentTiers().entrySet()) {
                long   duration = entry.getKey();
                double rate     = entry.getValue();

                ItemStack btn = new ItemBuilder(Material.GOLD_INGOT)
                        .name("&6&l⧖ Invest " + fmtDuration(duration))
                        .lore(
                                "&8--------------------",
                                "&7Lock duration: &e" + fmtDuration(duration),
                                "&7Return rate: &a+" + (int)(rate * 100) + "%",
                                "",
                                "&7Click to invest from savings.",
                                "&7(Chat input for amount)",
                                "&8--------------------"
                        )
                        .build();

                final long dur = duration;
                set(new Slot(slot) {
                    @Override public ItemStack getItem() { return btn; }
                    @Override public void onClick(Player p, InventoryClickEvent e) {
                        p.closeInventory();
                        p.sendMessage(CC.translate("&8[&bBank&8] &7Enter investment amount for " +
                                fmtDuration(dur) + " vault (or &eall&7):"));
                        plugin.getBankCommandHandler().awaitInvestInput(p, dur);
                        p.playSound(p.getLocation(), Sound.NOTE_PLING, 0.6f, 1.4f);
                    }
                });

                slot += 9;
                if (slot > 51) break;
            }
        } else {
            boolean matured = vault.isMatured();
            String statusLine = matured
                    ? "&a&lMATURED - Click to claim!"
                    : "&7Matures in: &e" + vault.getTimeRemainingFormatted();

            ItemStack vaultItem = new ItemBuilder(Material.GOLD_BLOCK)
                    .name("&6&l⧖ Active Investment")
                    .lore(
                            "&8--------------------",
                            "&7Locked: &f" + shards.formatShort(vault.getLockedAmount()),
                            "&7Payout: &a" + shards.formatShort(vault.getPayout()),
                            "&7Profit: &a+" + shards.formatShort(vault.getProfit()),
                            "",
                            statusLine,
                            "&8--------------------"
                    )
                    .build();

            for (int s : new int[]{15, 16, 24, 25, 33}) {
                set(new Slot(s) {
                    @Override public ItemStack getItem() { return vaultItem; }
                    @Override public void onClick(Player p, InventoryClickEvent e) {
                        if (!vault.isMatured()) {
                            p.sendMessage(CC.translate("&cNot matured yet - " +
                                    vault.getTimeRemainingFormatted()));
                            return;
                        }
                        TransactionResult result = savings.claimInvestment(p.getUniqueId());
                        p.sendMessage(CC.translate(result.getMessage()));
                        if (result.isSuccess()) {
                            p.playSound(p.getLocation(), Sound.LEVEL_UP, 0.8f, 1.3f);
                            plugin.getServer().getScheduler().runTask(plugin, () -> build());
                        }
                    }
                });
            }
        }
    }

    private void buildLoanSection(ClashBoxProfile profile) {
        long loanBal = profile.getLoanBalance();

        if (loanBal <= 0) {
            long maxLoan = credit.calculateMaxLoan(player.getUniqueId());
            BankConfig.CreditTier tier = credit.getTier(player.getUniqueId());

            ItemStack loanInfo = new ItemBuilder(Material.BOOK)
                    .name("&e&l$ Loan Available")
                    .lore(
                            "&8--------------------",
                            "&7Max loan: &e" + shards.formatShort(maxLoan),
                            "&7Interest: &c" + (int)(tier.interestRatePerCompound() * 100) + "% &7per 6h (compounds)",
                            "&7Credit: " + credit.formatScore(player.getUniqueId()),
                            "",
                            "&eClick to take a loan.",
                            "&7(Chat input for amount)",
                            "&8--------------------"
                    )
                    .build();

            for (int s : new int[]{33, 34, 42, 43, 51}) {
                set(new Slot(s) {
                    @Override public ItemStack getItem() { return loanInfo; }
                    @Override public void onClick(Player p, InventoryClickEvent e) {
                        if (maxLoan < bankCfg.getLoanMinimum()) {
                            p.sendMessage(CC.translate("&cYour credit score is too low to qualify for a loan."));
                            return;
                        }
                        p.closeInventory();
                        p.sendMessage(CC.translate("&8[&bBank&8] &7Enter loan amount (max: " +
                                shards.formatShort(maxLoan) + "&7):"));
                        plugin.getBankCommandHandler().awaitLoanInput(p);
                        p.playSound(p.getLocation(), Sound.NOTE_PLING, 0.6f, 1.0f);
                    }
                });
            }

        } else {
            long principal = profile.getLoanPrincipal();
            long interest  = loanBal - principal;
            BankConfig.CreditTier tier = credit.getTier(player.getUniqueId());

            ItemStack activeLoan = new ItemBuilder(Material.REDSTONE_BLOCK)
                    .name("&c&l⚠ Active Loan")
                    .lore(
                            "&8--------------------",
                            "&7Original: &f" + shards.formatShort(principal),
                            "&7Interest: &c+" + shards.formatShort(interest),
                            "&7Total owed: &c&l" + shards.formatShort(loanBal),
                            "&7Rate: &c" + (int)(tier.interestRatePerCompound() * 100) + "% &7per 6h",
                            "",
                            "&eClick to repay (chat input).",
                            "&8--------------------"
                    )
                    .build();

            ItemStack repayAllBtn = new ItemBuilder(Material.EMERALD_BLOCK)
                    .name("&a✔ Repay All - " + shards.formatShort(loanBal))
                    .lore("&7Click to repay entire loan at once.")
                    .build();

            for (int s : new int[]{33, 34, 42}) {
                set(new Slot(s) { @Override public ItemStack getItem() { return activeLoan; } });
            }

            set(new Slot(43) {
                @Override public ItemStack getItem() { return activeLoan; }
                @Override public void onClick(Player p, InventoryClickEvent e) {
                    p.closeInventory();
                    p.sendMessage(CC.translate("&8[&bBank&8] &7Enter repayment amount (or &eall&7):"));
                    plugin.getBankCommandHandler().awaitRepayInput(p);
                }
            });

            set(new Slot(51) {
                @Override public ItemStack getItem() { return repayAllBtn; }
                @Override public void onClick(Player p, InventoryClickEvent e) {
                    TransactionResult result = loans.repayLoan(p.getUniqueId(), loanBal);
                    p.sendMessage(CC.translate(result.getMessage()));
                    if (result.isSuccess()) {
                        p.playSound(p.getLocation(), Sound.LEVEL_UP, 0.8f, 1.2f);
                        plugin.getServer().getScheduler().runTask(plugin, () -> build());
                    } else {
                        p.playSound(p.getLocation(), Sound.NOTE_BASS, 0.6f, 0.7f);
                    }
                }
            });
        }
    }

    private void buildCreditSection(ClashBoxProfile profile) {
        BankConfig.CreditTier tier = bankCfg.getTierForScore(profile.getCreditScore());

        ItemStack creditItem = new ItemBuilder(Material.NETHER_STAR)
                .name("&d&l★ Credit Score")
                .lore(
                        "&8--------------------",
                        "&7Score: " + credit.formatScore(player.getUniqueId()),
                        "&7Range: &f" + tier.minScore() + " &8- &f" + tier.maxScore(),
                        "&7Max borrow: &e" + (int)(tier.maxBorrowPercent() * 100) + "%",
                        "&7Loan rate: &c" + (int)(tier.interestRatePerCompound() * 100) + "% &7per 6h",
                        "",
                        "&7Improve by repaying loans on time.",
                        "&8--------------------"
                )
                .build();

        for (int s : new int[]{38, 39, 40}) {
            set(new Slot(s) { @Override public ItemStack getItem() { return creditItem; } });
        }
    }

    private void buildChequeButton() {
        ItemStack btn = new ItemBuilder(Material.PAPER)
                .name("&f&l✉ Write a Cheque")
                .lore(
                        "&8--------------------",
                        "&7Deducts shards from your wallet",
                        "&7and creates a transferable cheque.",
                        "",
                        "&7Give the paper to anyone -",
                        "&7they can deposit it with &e/deposit&7.",
                        "",
                        "&eClick to write a cheque.",
                        "&7(chat input for amount)",
                        "&8--------------------"
                )
                .build();

        set(new Slot(47) {
            @Override public ItemStack getItem() { return btn; }
            @Override public void onClick(Player p, InventoryClickEvent e) {
                p.closeInventory();
                p.sendMessage(CC.translate("&8[&bBank&8] &7Enter cheque amount (shards from wallet):"));
                plugin.getBankCommandHandler().awaitChequeInput(p);
                p.playSound(p.getLocation(), org.bukkit.Sound.NOTE_PLING, 0.6f, 1.5f);
            }
        });
    }

    private void buildStatementButton() {
        ItemStack btn = new ItemBuilder(Material.MAP)
                .name("&7&l☰ Bank Statement")
                .lore("&7View last 10 transactions.")
                .build();

        set(new Slot(46) {
            @Override public ItemStack getItem() { return btn; }
            @Override public void onClick(Player p, InventoryClickEvent e) {
                p.closeInventory();
                plugin.getBankCommandHandler().showStatement(p, 10);
                p.playSound(p.getLocation(), Sound.CLICK, 0.5f, 1.0f);
            }
        });
    }

    private void buildCloseButton() {
        ItemStack btn = new ItemBuilder(Material.BARRIER)
                .name("&c✖ Close")
                .lore("&7Click to close.")
                .build();

        set(new Slot(49) {
            @Override public ItemStack getItem() { return btn; }
            @Override public void onClick(Player p, InventoryClickEvent e) {
                p.closeInventory();
            }
        });
    }

    private String fmtDuration(long seconds) {
        if (seconds >= 86400) return (seconds / 86400) + "d";
        if (seconds >= 3600)  return (seconds / 3600) + "h";
        if (seconds >= 60)    return (seconds / 60) + "m";
        return seconds + "s";
    }
}
