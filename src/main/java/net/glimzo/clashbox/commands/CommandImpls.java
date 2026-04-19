package net.glimzo.clashbox.commands;

import net.glimzo.clashbox.core.ClashBoxPlugin;
import net.glimzo.clashbox.economy.TransactionResult;
import net.glimzo.clashbox.player.ClashBoxProfile;
import net.glimzo.clashbox.progression.UpgradeType;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import me.pikashrey.glimzocore.utilities.chat.CC;

class BankCommandImpl implements CommandExecutor {
    private final ClashBoxPlugin plugin;
    BankCommandImpl(ClashBoxPlugin p) { this.plugin = p; }

    @Override
    public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        if (!(s instanceof Player player)) {
            s.sendMessage(CC.translate("&cPlayers only.")); return true;
        }
        ClashBoxProfile profile = plugin.getProfileManager().getProfile(player);
        if (profile == null) { player.sendMessage(CC.translate("&cProfile not loaded.")); return true; }

        if (a.length == 0) {
            plugin.getBankCommandHandler().handleCommand(player, new String[]{"balance"});
            return true;
        }

        switch (a[0].toLowerCase()) {
            case "deposit", "dep" -> {
                if (a.length < 2) { player.sendMessage(CC.translate("&cUsage: /bank deposit <amount>")); return true; }
                long amount = parseAmount(a[1], plugin.getShardEconomy().getBalance(player.getUniqueId()));
                if (amount <= 0) { player.sendMessage(CC.translate("&cInvalid amount.")); return true; }
                TransactionResult r = plugin.getSavingsManager().deposit(player.getUniqueId(), amount);
                player.sendMessage(CC.translate(r.getMessage()));
            }
            case "withdraw", "with" -> {
                if (a.length < 2) { player.sendMessage(CC.translate("&cUsage: /bank withdraw <amount>")); return true; }
                long amount = parseAmount(a[1], profile.getSavingsBalance());
                if (amount <= 0) { player.sendMessage(CC.translate("&cInvalid amount.")); return true; }
                TransactionResult r = plugin.getSavingsManager().withdraw(player.getUniqueId(), amount);
                player.sendMessage(CC.translate(r.getMessage()));
            }
            case "invest" -> {
                if (a.length < 3) {
                    player.sendMessage(CC.translate("&cUsage: /bank invest <amount> <2h|8h|24h>"));
                    player.sendMessage(CC.translate("&7Tiers: &a2h &7-> &a3% &7| &a8h &7-> &a6% &7| &a24h &7-> &a10%"));
                    return true;
                }
                long amount   = parseAmount(a[1], profile.getSavingsBalance());
                long duration = parseDuration(a[2]);
                if (amount <= 0 || duration <= 0) {
                    player.sendMessage(CC.translate("&cInvalid amount or duration. Use: 2h, 8h, 24h")); return true;
                }
                TransactionResult r = plugin.getSavingsManager().lockInvestment(player.getUniqueId(), amount, duration);
                player.sendMessage(CC.translate(r.getMessage()));
            }
            case "claim" -> {
                TransactionResult r = plugin.getSavingsManager().claimInvestment(player.getUniqueId());
                player.sendMessage(CC.translate(r.getMessage()));
            }
            case "balance", "bal" ->
                plugin.getBankCommandHandler().handleCommand(player, new String[]{"balance"});
            default ->
                player.sendMessage(CC.translate("&cUnknown sub-command. Use: deposit, withdraw, invest, claim, balance"));
        }
        return true;
    }

    private long parseAmount(String s, long max) {
        if (s.equalsIgnoreCase("all") || s.equalsIgnoreCase("max")) return max;
        try { return Math.max(0, Long.parseLong(s)); }
        catch (NumberFormatException e) { return -1; }
    }

    private long parseDuration(String s) {
        return switch (s.toLowerCase()) {
            case "2h"  -> 7200;
            case "8h"  -> 28800;
            case "24h" -> 86400;
            default    -> -1;
        };
    }
}

class StatsCommandImpl implements CommandExecutor {
    private final ClashBoxPlugin plugin;
    StatsCommandImpl(ClashBoxPlugin p) { this.plugin = p; }

    @Override
    public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        ClashBoxProfile profile;
        String targetName;

        if (a.length >= 1) {
            Player target = plugin.getServer().getPlayer(a[0]);
            if (target == null) { s.sendMessage(CC.translate("&cPlayer not found or offline.")); return true; }
            profile    = plugin.getProfileManager().getProfile(target);
            targetName = target.getName();
        } else {
            if (!(s instanceof Player sender)) {
                s.sendMessage(CC.translate("&cConsole usage: /stats <player>")); return true;
            }
            profile    = plugin.getProfileManager().getProfile(sender);
            targetName = sender.getName();
        }

        if (profile == null) { s.sendMessage(CC.translate("&cProfile not loaded.")); return true; }

        s.sendMessage(CC.translate("&8━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
        s.sendMessage(CC.translate("&b&l  ✦ STATS - " + targetName));
        s.sendMessage(CC.translate("&8━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
        s.sendMessage(CC.translate("&7  &eSeason " + profile.getCurrentSeason()));
        s.sendMessage(CC.translate("&7  Kills:    &f" + profile.getSeasonKills()));
        s.sendMessage(CC.translate("&7  Deaths:   &f" + profile.getSeasonDeaths()));
        s.sendMessage(CC.translate("&7  K/D:      &f" + profile.getKD()));
        s.sendMessage(CC.translate("&7  Shards:   &b◆ " + profile.getSeasonCoinsEarned()));
        s.sendMessage(CC.translate("&7  Bounties: &f" + profile.getSeasonBountiesClaimed()));
        s.sendMessage(CC.translate("&7  Prestige: &d" + profile.getPrestigeLevel()));
        s.sendMessage(CC.translate("&8-----------------------------"));
        s.sendMessage(CC.translate("&7  &fLifetime Kills:  &f" + profile.getLifetimeKills()));
        s.sendMessage(CC.translate("&7  &fLifetime Deaths: &f" + profile.getLifetimeDeaths()));
        s.sendMessage(CC.translate("&8━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
        return true;
    }
}

class LeaderboardCommandImpl implements CommandExecutor {
    private final ClashBoxPlugin plugin;
    LeaderboardCommandImpl(ClashBoxPlugin p) { this.plugin = p; }

    @Override
    public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        String type = a.length >= 1 ? a[0].toLowerCase() : "kills";

        var profiles = new java.util.ArrayList<>(plugin.getProfileManager().getCachedProfiles());

        java.util.Comparator<ClashBoxProfile> comparator = switch (type) {
            case "coins" -> java.util.Comparator.comparingLong(ClashBoxProfile::getSeasonCoinsEarned).reversed();
            case "kd"    -> java.util.Comparator.comparingDouble(ClashBoxProfile::getKD).reversed();
            default      -> java.util.Comparator.comparingInt(ClashBoxProfile::getSeasonKills).reversed();
        };
        String title = switch (type) {
            case "coins" -> "&e&lTop Shards (Season)";
            case "kd"    -> "&a&lTop K/D (Season)";
            default      -> "&c&lTop Kills (Season)";
        };

        profiles.sort(comparator);
        s.sendMessage(CC.translate("&8━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
        s.sendMessage(CC.translate("  " + title));
        s.sendMessage(CC.translate("&8━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));

        int rank = 1;
        for (ClashBoxProfile p : profiles) {
            if (rank > 10) break;
            String medal = rank == 1 ? "&6#1" : rank == 2 ? "&7#2" : rank == 3 ? "&c#3" : "&f#" + rank;
            String value = switch (type) {
                case "coins" -> "&b◆ " + p.getSeasonCoinsEarned();
                case "kd"    -> "&a" + p.getKD();
                default      -> "&c" + p.getSeasonKills() + " kills";
            };
            s.sendMessage(CC.translate("  " + medal + " &f" + p.getLastKnownName() + " &8- " + value));
            rank++;
        }
        s.sendMessage(CC.translate("&8━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
        return true;
    }
}

class BountyCommandImpl implements CommandExecutor {
    private final ClashBoxPlugin plugin;
    BountyCommandImpl(ClashBoxPlugin p) { this.plugin = p; }

    @Override
    public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        if (a.length == 0 || a[0].equalsIgnoreCase("list")) {
            s.sendMessage(CC.translate("&8━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
            s.sendMessage(CC.translate("&6&l  ✦ ACTIVE BOUNTIES"));
            s.sendMessage(CC.translate("&8━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
            boolean any = false;
            for (org.bukkit.entity.Player online : plugin.getServer().getOnlinePlayers()) {
                if (plugin.getBountyManager().hasBounty(online.getUniqueId())) {
                    long val    = plugin.getBountyManager().getBountyValue(online.getUniqueId());
                    int  streak = plugin.getStreakTracker().getStreak(online);
                    s.sendMessage(CC.translate("&6★ &f" + online.getName() +
                            " &8- &b◆ " + val + " &8(&c" + streak + " streak&8)"));
                    any = true;
                }
            }
            if (!any) s.sendMessage(CC.translate("&7  No active bounties."));
            s.sendMessage(CC.translate("&8━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
        }
        return true;
    }
}

class TeamCommandImpl implements CommandExecutor {
    private final ClashBoxPlugin plugin;
    TeamCommandImpl(ClashBoxPlugin p) { this.plugin = p; }

    @Override
    public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        if (!(s instanceof Player player)) return true;
        if (a.length == 0) { sendHelp(player); return true; }

        switch (a[0].toLowerCase()) {
            case "create" -> {
                String err = plugin.getTeamManager().createTeam(player);
                player.sendMessage(CC.translate(err != null ? err :
                        "&a&l[Team] Team created! Invite players with &e/team invite <n>"));
            }
            case "invite" -> {
                if (a.length < 2) { player.sendMessage(CC.translate("&cUsage: /team invite <player>")); return true; }
                org.bukkit.entity.Player target = plugin.getServer().getPlayer(a[1]);
                if (target == null) { player.sendMessage(CC.translate("&cPlayer not found.")); return true; }
                String err = plugin.getTeamManager().invite(player, target);
                player.sendMessage(CC.translate(err == null ? "&aInvite sent to &f" + target.getName() : err));
            }
            case "accept" -> {
                String err = plugin.getTeamManager().acceptInvite(player);
                player.sendMessage(CC.translate(err == null ? "&a&l[Team] Joined the team!" : err));
            }
            case "leave" -> {
                String err = plugin.getTeamManager().leaveTeam(player);
                player.sendMessage(CC.translate(err == null ? "&c[Team] You left the team." : err));
            }
            case "vault" -> {
                if (a.length < 3) { player.sendMessage(CC.translate("&cUsage: /team vault <deposit|withdraw> <amount>")); return true; }
                String sub = a[1].toLowerCase();
                if (!sub.equals("deposit") && !sub.equals("withdraw")) {
                    player.sendMessage(CC.translate("&cUsage: /team vault <deposit|withdraw> <amount>")); return true;
                }
                long maxAmount;
                if (sub.equals("deposit")) {
                    maxAmount = plugin.getShardEconomy().getBalance(player.getUniqueId());
                } else {
                    var t = plugin.getTeamManager().getTeam(player);
                    maxAmount = t != null ? t.getVaultBalance() : 0;
                }
                long amount;
                if (a[2].equalsIgnoreCase("all") || a[2].equalsIgnoreCase("max")) {
                    amount = maxAmount;
                } else {
                    try { amount = Long.parseLong(a[2].replace(",", "")); }
                    catch (NumberFormatException e) { player.sendMessage(CC.translate("&cInvalid amount.")); return true; }
                }
                if (amount <= 0) { player.sendMessage(CC.translate("&cAmount must be positive.")); return true; }
                String err = sub.equals("deposit")
                        ? plugin.getTeamManager().depositToVault(player, amount)
                        : plugin.getTeamManager().withdrawFromVault(player, amount);
                if (err != null) player.sendMessage(CC.translate(err));
            }
            case "info" -> {
                var team = plugin.getTeamManager().getTeam(player);
                if (team == null) { player.sendMessage(CC.translate("&cYou are not in a team.")); return true; }
                player.sendMessage(CC.translate("&8━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
                player.sendMessage(CC.translate("&b&l  ✦ TEAM INFO"));
                player.sendMessage(CC.translate("&8━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
                player.sendMessage(CC.translate("&7  Vault: &a◆ " + team.getVaultBalance()));
                player.sendMessage(CC.translate("&7  Members (" + team.getMembers().size() + "):"));
                for (java.util.UUID memberId : team.getMembers()) {
                    org.bukkit.OfflinePlayer member = plugin.getServer().getOfflinePlayer(memberId);
                    boolean online = member.isOnline();
                    String leaderTag  = memberId.equals(team.getLeader()) ? " &6[Leader]" : "";
                    String statusColor = online ? "&a" : "&7";
                    String name = member.getName() != null ? member.getName() : memberId.toString().substring(0, 8);
                    player.sendMessage(CC.translate("  " + statusColor + name + leaderTag));
                }
                player.sendMessage(CC.translate("&8━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
            }
            default -> sendHelp(player);
        }
        return true;
    }

    private void sendHelp(Player p) {
        p.sendMessage(CC.translate("&b&lTeam Commands:"));
        p.sendMessage(CC.translate("&e/team create &7- Create a new team"));
        p.sendMessage(CC.translate("&e/team invite <player> &7- Invite a player"));
        p.sendMessage(CC.translate("&e/team accept &7- Accept a team invite"));
        p.sendMessage(CC.translate("&e/team leave &7- Leave your team"));
        p.sendMessage(CC.translate("&e/team vault deposit|withdraw <amount> &7- Team vault"));
        p.sendMessage(CC.translate("&e/team info &7- View team details"));
    }
}
