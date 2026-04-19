package net.glimzo.clashbox.sets;

import me.pikashrey.glimzocore.utilities.chat.CC;
import net.glimzo.clashbox.core.ClashBoxPlugin;
import net.glimzo.clashbox.player.ClashBoxProfile;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

public class SetBonusListener implements Listener {

    private final ClashBoxPlugin plugin;
    private final SetManager     setManager;

    public SetBonusListener(ClashBoxPlugin plugin) {
        this.plugin     = plugin;
        this.setManager = plugin.getSetManager();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onRightClick(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR &&
            event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player    player = event.getPlayer();
        ItemStack held   = player.getItemInHand();
        if (held == null) return;

        String setId = setManager.extractSetId(held);
        if (setId == null) return;

        SetPiece piece = setManager.extractSetPiece(held);
        if (piece == null) return;

        ArmorSet set = setManager.getSet(setId);
        if (set == null) return;

        ClashBoxProfile profile = plugin.getProfileManager().getProfile(player);
        if (profile == null) return;

        if (!set.getId().equals(profile.getOwnedSetPiece(piece))) {
            player.sendMessage(CC.translate("&cYou don't own this piece."));
            return;
        }

        int tierNum = plugin.getTierManager().getTier(player).getNumber();
        if (tierNum < set.getTierRequired()) {
            player.sendMessage(CC.translate("&cRequires Tier &e" + set.getTierRequired() +
                    " &cto equip."));
            return;
        }

        ItemStack current = piece.getFromPlayer(player);
        if (current != null && current.getType() != org.bukkit.Material.AIR) {
            player.getInventory().addItem(current);
        }
        piece.setOnPlayer(player, held);
        player.setItemInHand(null);

        player.sendMessage(CC.translate("&a✦ Equipped " + set.getColor() +
                "&l" + set.getName() + "'s " + piece.getDisplayName() + "&a!"));
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getSlotType() != InventoryType.SlotType.ARMOR) return;

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            setManager.removeSetBonus(player);
            for (ArmorSet set : setManager.getSets().values()) {
                ClashBoxProfile profile = plugin.getProfileManager().getProfile(player);
                if (profile != null && setManager.isWearingFullSet(player, set, profile)) {
                    setManager.applySetBonus(player, set);
                    break;
                }
            }
        }, 1L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        setManager.removeSetBonus(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        event.getDrops().removeIf(item -> setManager.extractSetId(item) != null);
    }
}
