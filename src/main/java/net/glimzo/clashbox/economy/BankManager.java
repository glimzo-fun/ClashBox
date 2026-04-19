package net.glimzo.clashbox.economy;

import me.pikashrey.glimzocore.utilities.chat.CC;
import net.glimzo.clashbox.core.ClashBoxPlugin;
import net.glimzo.clashbox.player.ClashBoxProfile;
import org.bukkit.entity.Player;

public class BankManager {

    private final ClashBoxPlugin plugin;

    public BankManager(ClashBoxPlugin plugin) {
        this.plugin = plugin;
    }

    public void startMaturityChecker() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                ClashBoxProfile profile = plugin.getProfileManager().getProfile(player);
                if (profile == null || profile.getInvestmentVault() == null) continue;
                if (!profile.getInvestmentVault().isMatured()) continue;

                TransactionResult result = plugin.getEconomyHook()
                        .claimInvestment(player.getUniqueId());

                if (result.isSuccess()) {
                    player.sendMessage(CC.translate(
                            "&8[&bClashBox&8] &a&l[Investment] &r&aMatured! +$" + result.getAmount() +
                            " added to your bank."));
                    plugin.getTitleService().sendTitle(player,
                            "&a&lINVESTMENT!", "&7+$" + result.getAmount() + " to bank", 10, 60, 20);
                    player.playSound(player.getLocation(),
                            org.bukkit.Sound.LEVEL_UP, 0.8f, 1.3f);
                }
            }
        }, 1200L, 1200L);
    }
}
