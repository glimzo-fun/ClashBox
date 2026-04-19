package net.glimzo.clashbox.commands;

import net.glimzo.clashbox.core.ClashBoxPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class LeaderboardCommand implements CommandExecutor {

    private final LeaderboardCommandImpl impl;

    public LeaderboardCommand(ClashBoxPlugin plugin) {
        this.impl = new LeaderboardCommandImpl(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        return impl.onCommand(sender, command, label, args);
    }
}
