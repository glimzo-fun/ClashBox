package net.glimzo.clashbox.commands;

import me.pikashrey.glimzocore.utilities.chat.CC;
import net.glimzo.clashbox.bank.ChequeManager;
import net.glimzo.clashbox.core.ClashBoxPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;

public class DepositCommand implements CommandExecutor, TabCompleter {

    private final ClashBoxPlugin plugin;

    public DepositCommand(ClashBoxPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(CC.translate("&cThis command is for players only."));
            return true;
        }

        String account = args.length >= 1 ? args[0] : "wallet";

        if (!account.equalsIgnoreCase("wallet") &&
            !account.equalsIgnoreCase("savings") &&
            !account.equalsIgnoreCase("save")) {
            player.sendMessage(CC.translate("&cUsage: &e/deposit [wallet|savings]"));
            return true;
        }

        ChequeManager cheques = plugin.getChequeManager();
        String result = cheques.depositCheque(player, account);
        player.sendMessage(result);

        if (result.startsWith(CC.translate("&a"))) {
            player.playSound(player.getLocation(), org.bukkit.Sound.LEVEL_UP, 0.6f, 1.3f);
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command,
                                      String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("wallet", "savings");
        }
        return List.of();
    }
}
