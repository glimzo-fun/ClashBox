package net.glimzo.clashbox.enchant;

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

public class EnchantNpcMenu extends GlimzoMenu {

    private final ClashBoxPlugin     plugin;
    private final EnchantNpcCategory category;
    private final EnchantManager     enchantManager;

    private static final short GLASS_BLACK = 15;
    private static final short GLASS_GREY  = 7;
    private static final short GLASS_GREEN = 5;

    public EnchantNpcMenu(ClashBoxPlugin plugin, Player player, EnchantNpcCategory category) {
        super(GlimzoCore.getInstance(), player, CC.translate(category.getTitle()), 54);
        this.plugin         = plugin;
        this.category       = category;
        this.enchantManager = plugin.getEnchantManager();
    }

    @Override
    protected void buildContent() {
        fillEmpty();
        buildHeader();
        buildEnchantGrid();
        buildFooter();
    }

    private void buildHeader() {
        ItemStack icon = new ItemBuilder(category.getIcon())
                .name(CC.translate(category.getTitle()))
                .lore(CC.translate(category.getSubtitle()))
                .build();
        set(new Slot(0) { @Override public ItemStack getItem() { return icon; } });

        for (int i = 1; i <= 7; i++) {
            if (i == 4) continue;
            final ItemStack pane = blackPane();
            final int s = i;
            set(new Slot(s) { @Override public ItemStack getItem() { return pane; } });
        }

        long      balance = plugin.getShardEconomy().getBalance(player.getUniqueId());
        TierLevel tier    = plugin.getTierManager().getTier(player);
        ItemStack wallet  = new ItemBuilder(Material.QUARTZ)
                .name(CC.translate("&b&l◆ Your Wallet"))
                .lore(
                        CC.translate("&7Balance: &b" + plugin.getShardEconomy().formatShort(balance)),
                        CC.translate("&7Tier:    " + tier.display()),
                        CC.translate("&8--------------"),
                        CC.translate("&7Higher tier unlocks more enchants.")
                ).build();
        set(new Slot(4) { @Override public ItemStack getItem() { return wallet; } });

        ItemStack hint = new ItemBuilder(Material.ANVIL)
                .name(CC.translate("&e&l⚒ How to Apply"))
                .lore(
                        CC.translate("&71. Purchase a book below."),
                        CC.translate("&72. &eCombine &7your &egear + book"),
                        CC.translate("&7   at the &banvil in front of me&7.")
                ).build();
        set(new Slot(8) { @Override public ItemStack getItem() { return hint; } });
    }

    private void buildEnchantGrid() {
        ClashBoxProfile profile = plugin.getProfileManager().getProfile(player);
        if (profile == null) return;

        int slot = 9;

        for (CustomEnchantType type : category.getCustomEnchants()) {
            if (slot >= 45) break;
            slot = buildCustomSlot(slot, profile, type);
        }

        var enchantCfg = plugin.getEnchantConfig();
        for (String key : category.getVanillaKeys()) {
            if (slot >= 45) break;
            var section = enchantCfg.getConfigurationSection(
                    "vanilla-extensions.enchants." + key);
            if (section == null) continue;
            slot = buildVanillaSlot(slot, profile, key, section);
        }
    }

    private int buildCustomSlot(int slotIndex, ClashBoxProfile profile, CustomEnchantType type) {
        int maxLevel  = plugin.getEnchantConfig()
                .getInt("custom-enchants." + type.name() + ".max-level", 3);
        int nextLevel = getNextCustomLevel(profile, type);

        if (nextLevel > maxLevel) {
            ItemStack maxed = new ItemBuilder(Material.STAINED_GLASS_PANE)
                    .name(CC.translate("&a&l✔ " + type.getDisplayName() + " &7(Maxed)"))
                    .lore(CC.translate("&7You own max level (" + maxLevel + ")."))
                    .durability(GLASS_GREEN).build();
            set(new Slot(slotIndex) { @Override public ItemStack getItem() { return maxed; } });
            return slotIndex + 1;
        }

        int     tierReq = getCustomTierReq(type, nextLevel);
        long    cost    = getCustomCost(type, nextLevel);
        boolean tierMet = plugin.getTierManager().getTier(player).getNumber() >= tierReq;

        if (!tierMet) {
            setLockedSlot(slotIndex, type.getDisplayName(), tierReq);
            return slotIndex + 1;
        }

        boolean   canAfford = plugin.getShardEconomy().has(player.getUniqueId(), cost);
        ItemStack book      = enchantManager.buildEnchantBook(type, nextLevel);
        appendPurchaseLine(book, canAfford, cost);

        final int  fLevel = nextLevel;
        final long fCost  = cost;

        set(new Slot(slotIndex) {
            @Override public ItemStack getItem() { return book; }
            @Override public void onClick(Player p, InventoryClickEvent e) {
                if (!plugin.getShardEconomy().has(p.getUniqueId(), fCost)) {
                    p.sendMessage(CC.translate("&cNot enough Shards. Need &b◆ " + fCost)); return;
                }
                plugin.getShardEconomy().removeShards(p.getUniqueId(), fCost,
                        "Enchant book: " + type.name() + " L" + fLevel);
                p.getInventory().addItem(enchantManager.buildEnchantBook(type, fLevel));
                p.sendMessage(CC.translate("&a✦ Purchased &5" + type.getDisplayName() +
                        " " + EnchantManager.toRoman(fLevel) +
                        " &7for &b◆ " + fCost));
                p.sendMessage(CC.translate(category.getAnvilHint()));
                p.playSound(p.getLocation(), Sound.LEVEL_UP, 0.7f, 1.3f);
                build();
            }
        });
        return slotIndex + 1;
    }

    private int buildVanillaSlot(int slotIndex, ClashBoxProfile profile,
                                  String key,
                                  org.bukkit.configuration.ConfigurationSection section) {
        String name   = section.getString("base-name", key);
        int    maxLvl = section.getInt("extended-max", 5);
        int    owned  = profile.getVanillaEnchantLevel(key);
        int    buyLvl = owned + 1;

        if (buyLvl > maxLvl) {
            ItemStack maxed = new ItemBuilder(Material.STAINED_GLASS_PANE)
                    .name(CC.translate("&a&l✔ " + name + " &7(Maxed)"))
                    .lore(CC.translate("&7You own max level (" + maxLvl + ")."))
                    .durability(GLASS_GREEN).build();
            set(new Slot(slotIndex) { @Override public ItemStack getItem() { return maxed; } });
            return slotIndex + 1;
        }

        List<?> costs    = section.getList("cost-per-level");
        List<?> tierReqs = section.getList("tier-required");
        long cost   = costs != null && buyLvl - 1 < costs.size()
                ? ((Number) costs.get(buyLvl - 1)).longValue() : 99999L;
        int tierReq = tierReqs != null && buyLvl - 1 < tierReqs.size()
                ? ((Number) tierReqs.get(buyLvl - 1)).intValue() : 1;

        boolean tierMet  = plugin.getTierManager().getTier(player).getNumber() >= tierReq;

        if (!tierMet) {
            setLockedSlot(slotIndex, name + " " + EnchantManager.toRoman(buyLvl), tierReq);
            return slotIndex + 1;
        }

        boolean canAfford = plugin.getShardEconomy().has(player.getUniqueId(), cost);

        ItemStack book = new ItemBuilder(Material.ENCHANTED_BOOK)
                .name(CC.translate("&e" + name + " " + EnchantManager.toRoman(buyLvl)))
                .lore(
                        CC.translate("&8--------------"),
                        CC.translate("&7Level: &f" + EnchantManager.toRoman(buyLvl)
                                + (owned > 0 ? " &8(owned: " + EnchantManager.toRoman(owned) + ")" : "")),
                        CC.translate("&7Cost:  &b◆ " + cost),
                        CC.translate("&7Tier:  " + TierLevel.fromNumber(tierReq).display()),
                        CC.translate("&8--------------"),
                        CC.translate(canAfford
                                ? "&a► Click to purchase"
                                : "&c✗ Not enough Shards (◆ " + cost + " required)")
                ).build();

        final String fKey  = key;
        final String fName = name;
        final int    fBuy  = buyLvl;
        final long   fCost = cost;

        set(new Slot(slotIndex) {
            @Override public ItemStack getItem() { return book; }
            @Override public void onClick(Player p, InventoryClickEvent e) {
                if (!plugin.getShardEconomy().has(p.getUniqueId(), fCost)) {
                    p.sendMessage(CC.translate("&cNeed &b◆ " + fCost)); return;
                }
                plugin.getShardEconomy().removeShards(p.getUniqueId(), fCost,
                        "Vanilla enchant: " + fKey + " L" + fBuy);

                ClashBoxProfile prof = plugin.getProfileManager().getProfile(p);
                if (prof != null) { prof.setVanillaEnchantLevel(fKey, fBuy); prof.markDirty(); }

                ItemStack give = new ItemBuilder(Material.ENCHANTED_BOOK)
                        .name(CC.translate("&e" + fName + " " + EnchantManager.toRoman(fBuy)))
                        .lore(CC.translate("&8||VANILLA:" + fKey + ":" + fBuy + "||"))
                        .build();
                p.getInventory().addItem(give);
                p.sendMessage(CC.translate("&aPurchased &e" + fName + " " +
                        EnchantManager.toRoman(fBuy) + " &7for &b◆ " + fCost));
                p.sendMessage(CC.translate(category.getAnvilHint()));
                p.playSound(p.getLocation(), Sound.LEVEL_UP, 0.7f, 1.3f);
                build();
            }
        });
        return slotIndex + 1;
    }

    private void setLockedSlot(int slotIndex, String enchantName, int tierReq) {
        ItemStack pane = new ItemBuilder(Material.STAINED_GLASS_PANE)
                .name(CC.translate("&7🔒 " + enchantName))
                .lore(
                        CC.translate("&cRequires " + TierLevel.fromNumber(tierReq).display()),
                        CC.translate("&7Earn more Notoriety to unlock.")
                ).durability(GLASS_GREY).build();
        set(new Slot(slotIndex) { @Override public ItemStack getItem() { return pane; } });
    }

    private void buildFooter() {
        for (int i = 45; i <= 53; i++) {
            final ItemStack pane = blackPane();
            final int s = i;
            set(new Slot(s) { @Override public ItemStack getItem() { return pane; } });
        }
    }

    private int getNextCustomLevel(ClashBoxProfile profile, CustomEnchantType type) {
        int maxOwned = 0;
        for (GearSlot slot : type.getApplicableSlots()) {
            for (AppliedEnchant e : profile.getEnchants(slot)) {
                if (e.getType() == type) maxOwned = Math.max(maxOwned, e.getLevel());
            }
        }
        return maxOwned + 1;
    }

    private int getCustomTierReq(CustomEnchantType type, int level) {
        List<?> reqs = plugin.getEnchantConfig().getList(
                "custom-enchants." + type.name() + ".tier-required");
        if (reqs == null || level - 1 >= reqs.size()) return 1;
        Object val = reqs.get(level - 1);
        return val instanceof Number n ? n.intValue() : 1;
    }

    private long getCustomCost(CustomEnchantType type, int level) {
        List<?> costs = plugin.getEnchantConfig().getList(
                "custom-enchants." + type.name() + ".cost");
        if (costs == null || level - 1 >= costs.size()) return Long.MAX_VALUE;
        Object val = costs.get(level - 1);
        return val instanceof Number n ? n.longValue() : Long.MAX_VALUE;
    }

    private void appendPurchaseLine(ItemStack item, boolean canAfford, long cost) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        lore.add(CC.translate(canAfford
                ? "&a► Click to purchase"
                : "&c✗ Not enough Shards (◆ " + cost + " required)"));
        meta.setLore(lore);
        item.setItemMeta(meta);
    }

    private ItemStack blackPane() {
        return new ItemBuilder(Material.STAINED_GLASS_PANE)
                .name(" ").durability(GLASS_BLACK).build();
    }
}
