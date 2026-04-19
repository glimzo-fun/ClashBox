package net.glimzo.clashbox.enchant;

import me.pikashrey.glimzocore.GlimzoCore;
import me.pikashrey.glimzocore.menu.menu.GlimzoMenu;
import me.pikashrey.glimzocore.menu.slots.Slot;
import me.pikashrey.glimzocore.utilities.chat.CC;
import me.pikashrey.glimzocore.utilities.item.ItemBuilder;
import net.glimzo.clashbox.core.ClashBoxPlugin;
import net.glimzo.clashbox.player.ClashBoxProfile;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.ArrayList;
import java.util.List;

public class EnchantApplyMenu extends GlimzoMenu implements Listener {

    private final ClashBoxPlugin plugin;

    public EnchantApplyMenu(ClashBoxPlugin plugin, Player player) {
        super(GlimzoCore.getInstance(), player, "&8&lEnchanting &5Table", 54);
        this.plugin = plugin;
    }

    @Override
    protected void buildContent() {
        fillEmpty();
        buildGearRow();
        buildActiveEnchantsDisplay();
        buildInstructions();
    }

    private void buildGearRow() {
        ItemStack[] gear = new ItemStack[]{
                player.getInventory().getItemInHand(),
                player.getInventory().getHelmet(),
                player.getInventory().getChestplate(),
                player.getInventory().getLeggings(),
                player.getInventory().getBoots()
        };

        String[] labels = {"&f⚔ In Hand", "&bHelmet", "&bChestplate", "&bLeggings", "&bBoots"};
        int[] slots     = {22, 29, 31, 33, 35};

        ClashBoxProfile profile = plugin.getProfileManager().getProfile(player);
        if (profile == null) return;

        for (int i = 0; i < gear.length; i++) {
            ItemStack item = gear[i];
            if (item == null || item.getType() == Material.AIR) continue;

            GearSlot gearSlot = GearSlot.fromItem(item);
            if (gearSlot == null) continue;

            List<AppliedEnchant> enchants = profile.getEnchants(gearSlot);
            List<String> lore = new ArrayList<>();
            lore.add(CC.translate("&8--------------------"));
            lore.add(CC.translate("&7Applied custom enchants:"));

            if (enchants.isEmpty()) {
                lore.add(CC.translate("  &8None"));
            } else {
                for (AppliedEnchant e : enchants) {
                    lore.add(CC.translate("  &5✦ " + e.getType().getDisplayName() +
                            " " + EnchantManager.toRoman(e.getLevel())));
                }
            }

            lore.add(CC.translate("&8--------------------"));
            lore.add(CC.translate("&eHold an enchant book"));
            lore.add(CC.translate("&eand right-click to apply."));

            ItemStack display = item.clone();
            if (display.hasItemMeta()) {
                var meta = display.getItemMeta();
                List<String> existing = meta.hasLore() ? meta.getLore() : new ArrayList<>();
                existing.addAll(lore);
                meta.setLore(existing);
                display.setItemMeta(meta);
            }

            set(new Slot(slots[i]) {
                @Override public ItemStack getItem() { return display; }
            });
        }
    }

    private void buildActiveEnchantsDisplay() {
        ClashBoxProfile profile = plugin.getProfileManager().getProfile(player);
        if (profile == null) return;

        List<String> allEnchants = new ArrayList<>();
        for (GearSlot slot : GearSlot.values()) {
            for (AppliedEnchant e : profile.getEnchants(slot)) {
                allEnchants.add(CC.translate("&5✦ " + e.getType().getDisplayName() +
                        " " + EnchantManager.toRoman(e.getLevel()) +
                        " &8[" + slot.name() + "]"));
            }
        }

        ItemStack summary = new ItemBuilder(Material.NETHER_STAR)
                .name("&d&lAll Active Enchants")
                .lore(allEnchants.isEmpty()
                        ? List.of(CC.translate("&7None applied yet."))
                        : allEnchants)
                .build();

        set(new Slot(4) { @Override public ItemStack getItem() { return summary; } });
    }

    private void buildInstructions() {
        ItemStack info = new ItemBuilder(Material.BOOK)
                .name("&e&lHow to Enchant")
                .lore(
                    "&71. Buy enchant books from the NPC.",
                    "&72. Hold the book in your hand.",
                    "&73. Right-click to apply it to",
                    "&7   the appropriate gear.",
                    "",
                    "&7Books stack by type & level.",
                    "&7Visit the store to see what's",
                    "&7available for your Tier."
                )
                .build();

        set(new Slot(49) { @Override public ItemStack getItem() { return info; } });
    }

    public static class TableInteractListener implements Listener {
        private final ClashBoxPlugin plugin;

        public TableInteractListener(ClashBoxPlugin plugin) {
            this.plugin = plugin;
        }

        @EventHandler
        public void onInteract(PlayerInteractEvent event) {
            if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
            if (event.getClickedBlock() == null) return;
            if (event.getClickedBlock().getType() != Material.ENCHANTMENT_TABLE) return;

            if (!plugin.getPlayerStateManager().isAliveInArena(event.getPlayer()) &&
                plugin.getPlayerStateManager().getState(event.getPlayer()) !=
                    net.glimzo.clashbox.player.PlayerState.IN_LOBBY) return;

            event.setCancelled(true);
            new EnchantApplyMenu(plugin, event.getPlayer()).open();
            event.getPlayer().playSound(event.getPlayer().getLocation(),
                    Sound.NOTE_PLING, 0.6f, 1.2f);
        }
    }
}
