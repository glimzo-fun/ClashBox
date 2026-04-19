package net.glimzo.clashbox.commands;

import me.pikashrey.glimzocore.utilities.chat.CC;
import net.glimzo.clashbox.core.ClashBoxPlugin;
import net.glimzo.clashbox.enchant.EnchantNpcCategory;
import net.glimzo.clashbox.enchant.EnchantNpcMenu;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class EnchantNpcCommand {

    private static final String LOBBY_WORLD = "world";

    private final ClashBoxPlugin plugin;

    public EnchantNpcCommand(ClashBoxPlugin plugin) {
        this.plugin = plugin;
    }

    public void handle(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(CC.translate("&cThis command can only be used by players."));
            return;
        }

        if (args.length < 2) {
            player.sendMessage(CC.translate("&cUsage: /cb npcinteract <SWORD|ARMOUR|TOOLS|PROJECTILE>"));
            return;
        }

        if (!player.getWorld().getName().equals(LOBBY_WORLD)) {
            player.sendMessage(CC.translate(
                    "&8[✦ Enchanter&8] &cYou must be in the lobby to use enchant NPCs."));
            return;
        }

        EnchantNpcCategory category = EnchantNpcCategory.fromString(args[1]);
        if (category == null) {
            player.sendMessage(CC.translate(
                    "&cUnknown NPC category: &e" + args[1] +
                    "&c. Valid: SWORD, ARMOUR, TOOLS, PROJECTILE"));
            return;
        }

        player.sendMessage(CC.translate(category.getAnvilHint()));
        new EnchantNpcMenu(plugin, player, category).open();
    }
}
