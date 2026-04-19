package net.glimzo.clashbox.sets;

import me.pikashrey.glimzocore.GlimzoCore;
import me.pikashrey.glimzocore.menu.menu.GlimzoMenu;
import me.pikashrey.glimzocore.menu.slots.Slot;
import me.pikashrey.glimzocore.utilities.chat.CC;
import me.pikashrey.glimzocore.utilities.item.ItemBuilder;
import net.glimzo.clashbox.core.ClashBoxPlugin;
import net.glimzo.clashbox.player.ClashBoxProfile;
import net.glimzo.clashbox.tier.TierLevel;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SetDisplayMenu extends GlimzoMenu {

    private final ClashBoxPlugin plugin;
    private final ArmorSet       set;
    private final SetManager     setManager;

    private static final short GLASS_BLACK = 15;
    private static final short GLASS_GREEN = 5;

    public SetDisplayMenu(ClashBoxPlugin plugin, Player player, ArmorSet set) {
        super(GlimzoCore.getInstance(), player,
                CC.translate(set.getColor() + set.getName() + "'s Set"), 54);
        this.plugin     = plugin;
        this.set        = set;
        this.setManager = plugin.getSetManager();
    }

    @Override
    protected void buildContent() {
        fillEmpty();
        buildHeader();
        buildPiecesDisplay();
        buildSetBonusSlot();
        buildUpgradeHint();
        buildFooter();
    }

    private void buildHeader() {
        ItemStack icon = new ItemBuilder(set.getGlassMaterial() != Material.AIR
                ? set.getGlassMaterial() : Material.NETHER_STAR)
                .name(CC.translate(set.getColor() + "&l" + set.getName() + "'s Set"))
                .lore(
                        CC.translate(set.getTier().display()),
                        CC.translate("&8--------------"),
                        CC.translate("&7Tier Required: " +
                                TierLevel.fromNumber(set.getTierRequired()).display()),
                        CC.translate("&8--------------"),
                        CC.translate("&7Right-click the &bOffers NPC &7to upgrade.")
                )
                .durability(set.getGlassDamage())
                .build();
        set(new Slot(4) { @Override public ItemStack getItem() { return icon; } });

        for (int i : new int[]{0,1,2,3,5,6,7,8}) {
            ItemStack p = blackPane();
            set(new Slot(i) { @Override public ItemStack getItem() { return p; } });
        }
    }

    private void buildPiecesDisplay() {
        ClashBoxProfile profile = plugin.getProfileManager().getProfile(player);
        int[] pieceSlots = {20, 22, 24, 26};
        SetPiece[] pieces = SetPiece.values();

        for (int i = 0; i < pieces.length; i++) {
            SetPiece piece = pieces[i];
            int slotIdx    = pieceSlots[i];
            boolean owned  = profile != null && set.getId().equals(profile.getOwnedSetPiece(piece));

            ItemStack item = setManager.buildPieceItem(set, piece);
            ItemMeta meta  = item.getItemMeta();
            if (meta != null) {
                List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
                lore.add(CC.translate("&8--------------"));
                lore.add(CC.translate("&7Enchants:"));
                for (Map.Entry<Enchantment, Integer> e : set.getEnchants(piece).entrySet()) {
                    lore.add(CC.translate("  &b" + formatEnchant(e.getKey()) + " " +
                            toRoman(e.getValue())));
                }
                lore.add(CC.translate("&8--------------"));
                lore.add(CC.translate(owned ? "&a✔ You own this piece" : "&7○ Not yet obtained"));
                meta.setLore(lore);
                item.setItemMeta(meta);
            }
            set(new Slot(slotIdx) { @Override public ItemStack getItem() { return item; } });
        }

        for (int fill : new int[]{9,10,11,12,13,14,15,16,17,18,19,21,23,25,27}) {
            ItemStack p = blackPane();
            set(new Slot(fill) { @Override public ItemStack getItem() { return p; } });
        }
    }

    private void buildSetBonusSlot() {
        ItemStack bonus = new ItemBuilder(Material.NETHER_STAR)
                .name(CC.translate("&e&lSet Bonus"))
                .lore(
                        CC.translate("&8--------------"),
                        CC.translate("&7" + set.getSetBonusDescription()),
                        CC.translate("&8--------------"),
                        CC.translate("&7Equip all 4 pieces to activate.")
                )
                .build();
        set(new Slot(40) { @Override public ItemStack getItem() { return bonus; } });
    }

    private void buildUpgradeHint() {
        String desc;
        if (set.isStarter()) {
            desc = "&7Cost: &b◆ " + set.getShardCost() + " Shards &7per piece";
        } else {
            desc = "&7Mine &e" + set.getName() + " Glass &7in the arena,\n" +
                   "&7then trade at the &bOffers NPC.";
        }
        ItemStack hint = new ItemBuilder(Material.BOOK)
                .name(CC.translate("&e&lHow to Obtain"))
                .lore(
                        CC.translate("&8--------------"),
                        CC.translate(desc),
                        CC.translate("&8--------------"),
                        CC.translate("&7Right-click the &bOffers NPC &7next to the"),
                        CC.translate("&7armor stand to open the upgrade menu.")
                )
                .build();
        set(new Slot(31) { @Override public ItemStack getItem() { return hint; } });
    }

    private void buildFooter() {
        for (int i : new int[]{28,29,30,32,33,34,35,36,37,38,39,41,42,43,44,45,46,47,48,49,50,51,52,53}) {
            ItemStack p = blackPane();
            set(new Slot(i) { @Override public ItemStack getItem() { return p; } });
        }
    }

    private String formatEnchant(Enchantment e) {
        return switch (e.getName()) {
            case "PROTECTION_ENVIRONMENTAL" -> "Protection";
            case "DURABILITY"               -> "Unbreaking";
            case "THORNS"                   -> "Thorns";
            case "FEATHER_FALLING"          -> "Feather Falling";
            case "PROTECTION_FIRE"          -> "Fire Protection";
            case "PROTECTION_EXPLOSIONS"    -> "Blast Protection";
            case "PROTECTION_PROJECTILE"    -> "Projectile Protection";
            default -> e.getName().replace("_", " ").toLowerCase();
        };
    }

    private String toRoman(int n) {
        return switch (n) {
            case 1 -> "I"; case 2 -> "II"; case 3 -> "III";
            case 4 -> "IV"; case 5 -> "V"; default -> String.valueOf(n);
        };
    }

    private ItemStack blackPane() {
        return new ItemBuilder(Material.STAINED_GLASS_PANE).name(" ").durability(GLASS_BLACK).build();
    }
}
