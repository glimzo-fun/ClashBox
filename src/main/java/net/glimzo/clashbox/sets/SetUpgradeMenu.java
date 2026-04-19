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
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class SetUpgradeMenu extends GlimzoMenu {

    private final ClashBoxPlugin plugin;
    private final ArmorSet       set;
    private final SetManager     setManager;

    private static final short GLASS_BLACK = 15;
    private static final short GLASS_GREY  = 7;
    private static final short GLASS_GREEN = 5;

    public SetUpgradeMenu(ClashBoxPlugin plugin, Player player, ArmorSet set) {
        super(GlimzoCore.getInstance(), player, CC.translate(set.getColor() + set.getName() + " &7- Offers"), 54);
        this.plugin     = plugin;
        this.set        = set;
        this.setManager = plugin.getSetManager();
    }

    @Override
    protected void buildContent() {
        fillEmpty();
        buildHeader();
        buildPieceSlots();
        buildCraftGlassSlot();
        buildFooter();
    }

    private void buildHeader() {
        ItemStack icon = new ItemBuilder(set.getGlassMaterial() != Material.AIR
                ? set.getGlassMaterial() : Material.NETHER_STAR)
                .name(CC.translate(set.getColor() + "&l" + set.getName() + "'s Set"))
                .lore(
                        CC.translate(set.getTier().display()),
                        CC.translate("&8--------------"),
                        CC.translate("&7Set Bonus: &e" + set.getSetBonusDescription())
                )
                .durability(set.getGlassDamage())
                .build();
        set(new Slot(4) { @Override public ItemStack getItem() { return icon; } });

        for (int i : new int[]{0, 1, 2, 3, 5, 6, 7, 8}) {
            ItemStack p = blackPane();
            set(new Slot(i) { @Override public ItemStack getItem() { return p; } });
        }
    }

    private void buildPieceSlots() {
        ClashBoxProfile profile = plugin.getProfileManager().getProfile(player);
        if (profile == null) return;

        int[]      slots  = {20, 22, 24, 26};
        SetPiece[] pieces = SetPiece.values();

        for (int i = 0; i < pieces.length; i++) {
            SetPiece piece   = pieces[i];
            int      slotIdx = slots[i];
            buildSinglePieceSlot(slotIdx, piece, profile);
        }

        for (int fill : new int[]{9,10,11,12,13,14,15,16,17,
                                   18,19,21,23,25,27,
                                   28,29,30,31,32,33,34,35}) {
            ItemStack p = blackPane();
            set(new Slot(fill) { @Override public ItemStack getItem() { return p; } });
        }
    }

    private void buildSinglePieceSlot(int slotIdx, SetPiece piece, ClashBoxProfile profile) {
        boolean owned   = set.getId().equals(profile.getOwnedSetPiece(piece));
        boolean tierMet = plugin.getTierManager().getTier(player).getNumber() >= set.getTierRequired();

        if (owned) {
            ItemStack done = new ItemBuilder(Material.STAINED_GLASS_PANE)
                    .name(CC.translate("&a✔ " + set.getName() + "'s " + piece.getDisplayName()))
                    .lore(CC.translate("&7You already own this piece."),
                          CC.translate("&7Drag it into your armor slot to wear it."))
                    .durability(GLASS_GREEN)
                    .build();
            set(new Slot(slotIdx) { @Override public ItemStack getItem() { return done; } });
            return;
        }

        if (!tierMet) {
            ItemStack locked = new ItemBuilder(Material.STAINED_GLASS_PANE)
                    .name(CC.translate("&c🔒 " + piece.getDisplayName()))
                    .lore(CC.translate("&cRequires " + TierLevel.fromNumber(set.getTierRequired()).display()))
                    .durability(GLASS_GREY)
                    .build();
            set(new Slot(slotIdx) { @Override public ItemStack getItem() { return locked; } });
            return;
        }

        if (set.isStarter()) {
            buildStarterSlot(slotIdx, piece, profile);
        } else {
            buildUpgradeSlot(slotIdx, piece, profile);
        }
    }

    private void buildStarterSlot(int slotIdx, SetPiece piece, ClashBoxProfile profile) {
        long    cost      = set.getShardCost();
        long    balance   = plugin.getShardEconomy().getBalance(player.getUniqueId());
        boolean canAfford = balance >= cost;

        ItemStack preview = setManager.buildPieceItem(set, piece);
        appendLine(preview, CC.translate("&8--------------"));
        appendLine(preview, CC.translate("&7Cost: &b◆ " + cost + " Shards"));
        appendLine(preview, CC.translate(canAfford ? "&a► Click to purchase" : "&c✗ Not enough Shards"));

        set(new Slot(slotIdx) {
            @Override public ItemStack getItem() { return preview; }
            @Override public void onClick(Player p, InventoryClickEvent e) {
                if (!plugin.getShardEconomy().has(p.getUniqueId(), cost)) {
                    p.sendMessage(CC.translate("&cNeed &b◆ " + cost + " Shards.")); return;
                }
                plugin.getShardEconomy().removeShards(p.getUniqueId(), cost,
                        "Set purchase: " + set.getId() + " " + piece.name());
                givePiece(p, piece, profile);
            }
        });
    }

    private void buildUpgradeSlot(int slotIdx, SetPiece piece, ClashBoxProfile profile) {
        ArmorSet prevSet = set.getUpgradeFromSetId() != null
                ? plugin.getSetManager().getSet(set.getUpgradeFromSetId()) : null;

        int     needCrafted  = set.getIntermediatePieceCost();
        int     haveCrafted  = setManager.countItem(player, set.intermediateItemTag().replace("||",""));
        boolean hasPrevPiece = prevSet != null && prevSet.getId().equals(profile.getOwnedSetPiece(piece));

        String prevName = prevSet != null ? prevSet.getName() : "previous";

        List<String> reqLines = new ArrayList<>();
        reqLines.add(CC.translate("&8--------------"));
        reqLines.add(CC.translate("&7Requires:"));
        reqLines.add(CC.translate((hasPrevPiece ? "&a✔" : "&c✘") +
                " &71x " + prevName + "'s " + piece.getDisplayName()));
        reqLines.add(CC.translate((haveCrafted >= needCrafted ? "&a✔" : "&c✘") +
                " &7" + needCrafted + "x " + set.getName() + " Glass &8(" + haveCrafted + "/" + needCrafted + ")"));
        reqLines.add(CC.translate("&8--------------"));

        boolean canUpgrade = hasPrevPiece && haveCrafted >= needCrafted;
        reqLines.add(CC.translate(canUpgrade ? "&a► Click to upgrade" : "&c✗ Missing requirements"));

        ItemStack preview = setManager.buildPieceItem(set, piece);
        for (String line : reqLines) appendLine(preview, line);

        set(new Slot(slotIdx) {
            @Override public ItemStack getItem() { return preview; }
            @Override public void onClick(Player p, InventoryClickEvent e) {
                ClashBoxProfile prof = plugin.getProfileManager().getProfile(p);
                if (prof == null) return;
                if (prevSet == null || !prevSet.getId().equals(prof.getOwnedSetPiece(piece))) {
                    p.sendMessage(CC.translate("&cYou need &e" + prevName + "'s " +
                            piece.getDisplayName() + " &cfirst.")); return;
                }
                int have = setManager.countItem(p, set.intermediateItemTag().replace("||",""));
                if (have < needCrafted) {
                    p.sendMessage(CC.translate("&cNeed &e" + needCrafted + "x " + set.getName() +
                            " Glass&c. You have &e" + have + "&c.")); return;
                }
                setManager.removeItems(p, set.intermediateItemTag().replace("||",""), needCrafted);
                setManager.removePieceFromInventory(p, prevSet, piece);
                givePiece(p, piece, prof);
            }
        });
    }

    private void buildCraftGlassSlot() {
        if (set.isStarter() || set.getGlassMaterial() == Material.AIR) return;

        int     rawNeeded = set.getIntermediateGlassCost();
        int     rawHave   = setManager.countItem(player, set.glassItemTag().replace("||",""));
        boolean canCraft  = rawHave >= rawNeeded;

        ItemStack craftItem = setManager.buildIntermediateItem(set);
        appendLine(craftItem, CC.translate("&8--------------"));
        appendLine(craftItem, CC.translate((canCraft ? "&a✔" : "&c✘") +
                " &7" + rawNeeded + "x raw glass &8(" + rawHave + "/" + rawNeeded + ")"));
        appendLine(craftItem, CC.translate(canCraft ? "&a► Click to craft" : "&c✗ Not enough glass"));

        set(new Slot(40) {
            @Override public ItemStack getItem() { return craftItem; }
            @Override public void onClick(Player p, InventoryClickEvent e) {
                int have = setManager.countItem(p, set.glassItemTag().replace("||",""));
                if (have < rawNeeded) {
                    p.sendMessage(CC.translate("&cNeed &e" + rawNeeded + "x &c" +
                            set.getName() + " Glass&c. Have &e" + have + "&c.")); return;
                }
                setManager.removeItems(p, set.glassItemTag().replace("||",""), rawNeeded);
                p.getInventory().addItem(setManager.buildIntermediateItem(set));
                p.sendMessage(CC.translate("&a✦ Crafted &e1x " + set.getName() + " Glass&a!"));
                p.playSound(p.getLocation(), Sound.ANVIL_USE, 0.7f, 1.2f);
                build();
            }
        });
    }

    private void buildFooter() {
        long      balance = plugin.getShardEconomy().getBalance(player.getUniqueId());
        ItemStack wallet  = new ItemBuilder(Material.QUARTZ)
                .name(CC.translate("&b&l◆ Shards"))
                .lore(CC.translate("&7Balance: &b" + plugin.getShardEconomy().formatShort(balance)))
                .build();
        set(new Slot(49) { @Override public ItemStack getItem() { return wallet; } });
        for (int i : new int[]{36,37,38,39,41,42,43,44,45,46,47,48,50,51,52,53}) {
            ItemStack p = blackPane();
            set(new Slot(i) { @Override public ItemStack getItem() { return p; } });
        }
    }

    private void givePiece(Player player, SetPiece piece, ClashBoxProfile profile) {
        ItemStack item = setManager.buildPieceItem(set, piece);
        player.getInventory().addItem(item);
        profile.setOwnedSetPiece(piece, set.getId());
        profile.markDirty();
        player.sendMessage(CC.translate("&a✦ Obtained &l" + set.getName() + "'s " +
                piece.getDisplayName() + "&a! Drag it to your armor slot."));
        player.playSound(player.getLocation(), Sound.LEVEL_UP, 0.8f, 1.3f);
        build();
    }

    private void appendLine(ItemStack item, String line) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        lore.add(CC.translate(line));
        meta.setLore(lore);
        item.setItemMeta(meta);
    }

    private ItemStack blackPane() {
        return new ItemBuilder(Material.STAINED_GLASS_PANE)
                .name(" ").durability(GLASS_BLACK).build();
    }
}
