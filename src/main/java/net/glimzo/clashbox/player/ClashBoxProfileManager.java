package net.glimzo.clashbox.player;

import me.pikashrey.glimzocore.utilities.chat.CC;
import net.glimzo.clashbox.core.ClashBoxPlugin;
import net.glimzo.clashbox.data.ClashBoxDataManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ClashBoxProfileManager implements Listener {

    private final ClashBoxPlugin      plugin;
    private final ClashBoxDataManager dataManager;
    private final Map<UUID, ClashBoxProfile> cache = new HashMap<>();

    public ClashBoxProfileManager(ClashBoxPlugin plugin, ClashBoxDataManager dataManager) {
        this.plugin      = plugin;
        this.dataManager = dataManager;
    }

    public ClashBoxProfile getProfile(Player player) { return cache.get(player.getUniqueId()); }
    public ClashBoxProfile getProfile(UUID uuid)     { return cache.get(uuid); }
    public Collection<ClashBoxProfile> getCachedProfiles() { return cache.values(); }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID   uuid   = player.getUniqueId();

        ClashBoxProfile profile = dataManager.loadProfile(uuid, player.getName());
        profile.setLastKnownName(player.getName());
        cache.put(uuid, profile);

        plugin.getPlayerStateManager().setState(player, PlayerState.IN_LOBBY);

        plugin.getSavingsManager().applyLoginInterest(player);
        plugin.getLoanManager().applyOfflineCompounds(player.getUniqueId());
        checkInvestmentMaturity(player, profile);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID   uuid   = player.getUniqueId();

        plugin.getSetManager().removeSetBonus(player);

        ClashBoxProfile profile = cache.remove(uuid);
        if (profile != null) dataManager.saveProfile(profile);
    }

    private void checkInvestmentMaturity(Player player, ClashBoxProfile profile) {
        if (profile.getInvestmentVault() == null) return;
        if (!profile.getInvestmentVault().isMatured()) return;

        net.glimzo.clashbox.economy.TransactionResult result =
                plugin.getSavingsManager().claimInvestment(player.getUniqueId());

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            if (result.isSuccess()) {
                player.sendMessage(CC.translate(
                        "&8[&bClashBox Bank&8] &a&lINVESTMENT MATURED! &r&7+" +
                        plugin.getShardEconomy().formatShort(result.getAmount()) +
                        " &7deposited to savings."));
                plugin.getTitleService().sendTitle(player,
                        "&a&lINVESTMENT MATURED!",
                        "&7+" + plugin.getShardEconomy().formatShort(result.getAmount()) + " in savings",
                        10, 60, 20);
                player.playSound(player.getLocation(), org.bukkit.Sound.LEVEL_UP, 0.8f, 1.3f);
            }
        }, 40L);
    }

    public void saveAll() {
        for (ClashBoxProfile p : cache.values()) dataManager.saveProfile(p);
        plugin.getLogger().info("[ClashBox] Saved " + cache.size() + " profiles.");
    }
}
