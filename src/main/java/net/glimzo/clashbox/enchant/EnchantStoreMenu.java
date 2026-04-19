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

import java.util.*;

public class EnchantStoreMenu extends GlimzoMenu {

    private final ClashBoxPlugin  plugin;
    private final EnchantManager  enchantManager;
    private       StoreCategory   currentCategory = StoreCategory.COMBAT;

    public enum StoreCategory {
        COMBAT("&c⚔ Combat", Material.DIAMOND_SWORD),
        MINING("&6⛏ Mining", Material.DIAMOND_PICKAXE),
        SURVIVAL("&a✦ Survival", Material.DIAMOND_CHESTPLATE),
        ZONE("&b⬡ Zone", Material.NETHER_STAR),
        VANILLA("&7⚗ Vanilla", Material.ENCHANTED_BOOK);

        final String   label;
        final Material icon;
        StoreCategory(String l, Material m) { label = l; icon = m; }
    }

    public EnchantStoreMenu(ClashBoxPlugin plugin, Player player) {
        super(GlimzoCore.getInstance(), player, "&8Enchant &5Store", 54);
        this.plugin         = plugin;
        this.enchantManager = plugin.getEnchantManager();
    }

    @Override
    protected void buildContent() {
        fillEmpty();
        buildCategoryTabs();
        buildEnchants();
        buildWalletInfo();
    }

    private void buildCategoryTabs() {
        StoreCategory[] cats    = StoreCategory.values();
        int[]           tabSlots = {1, 2, 3, 4, 5};
        for (int i = 0; i < cats.length; i++) {
            StoreCategory cat    = cats[i];
            boolean       active = cat == currentCategory;
            ItemStack icon = new ItemBuilder(cat.icon)
                    .name((active ? "&f&l" : "&7") + CC.strip(cat.label))
                    .lore(active ? "&eCurrently viewing" : "&7Click to view")
                    .durability((short)(active ? 0 : 8))
                    .build();
            int slot = tabSlots[i];
            set(new Slot(slot) {
                @Override public ItemStack getItem() { return icon; }
                @Override public void onClick(Player p, InventoryClickEvent e) {
                    currentCategory = cat;
                    build();
                }
            });
        }
    }

    private void buildEnchants() {
        ClashBoxProfile profile = plugin.getProfileManager().getProfile(player);
        if (profile == null) return;

        int startSlot = 9;
        int col       = 0;

        if (currentCategory == StoreCategory.VANILLA) {
            buildVanillaSection(profile, startSlot);
            return;
        }

        CustomEnchantType[] enchants = getEnchantsByCategory(currentCategory);

        for (CustomEnchantType type : enchants) {
            int displayLevel = getNextAvailableLevel(profile, type);
            if (displayLevel < 0) continue;

            int slot = startSlot + (col % 7) + ((col / 7) * 9);
            if (slot >= 54) break;

            int     tierReq   = getTierRequirement(type, displayLevel);
            long    cost      = getCost(type, displayLevel);
            boolean canAfford = plugin.getShardEconomy().has(player.getUniqueId(), cost);
            boolean tierMet   = plugin.getTierManager().getTier(player).getNumber() >= tierReq;

            ItemStack book = enchantManager.buildEnchantBook(type, Math.max(1, displayLevel));

            final int  finalLevel = displayLevel;
            final long finalCost  = cost;

            set(new Slot(slot) {
                @Override public ItemStack getItem() {
                    if (!tierMet) {
                        return new ItemBuilder(Material.BARRIER)
                                .name("&c" + type.getDisplayName())
                                .lore("&cRequires " + TierLevel.fromNumber(tierReq).display())
                                .build();
                    }
                    return book;
                }
                @Override public void onClick(Player p, InventoryClickEvent e) {
                    if (!tierMet) {
                        p.sendMessage(CC.translate("&cRequires " +
                                TierLevel.fromNumber(tierReq).display() + " &cto purchase."));
                        return;
                    }
                    if (!canAfford) {
                        p.sendMessage(CC.translate("&cNot enough Shards. Need &b◆ " + finalCost));
                        return;
                    }
                    plugin.getShardEconomy().removeShards(p.getUniqueId(), finalCost,
                            "Enchant book purchase: " + type.name() + " " + finalLevel);
                    ItemStack giveBook = enchantManager.buildEnchantBook(type, finalLevel);
                    p.getInventory().addItem(giveBook);
                    p.sendMessage(CC.translate("&a✦ Purchased " + type.getDisplayName() +
                            " " + EnchantManager.toRoman(finalLevel) + " &7for &b◆ " + finalCost));
                    p.playSound(p.getLocation(), Sound.LEVEL_UP, 0.7f, 1.3f);
                    build();
                }
            });

            col++;
        }
    }

    private void buildVanillaSection(ClashBoxProfile profile, int startSlot) {
        var enchantCfg = plugin.getEnchantConfig();
        var section    = enchantCfg.getConfigurationSection("vanilla-extensions.enchants");
        if (section == null) return;

        int col = 0;
        for (String key : section.getKeys(false)) {
            String name      = section.getString(key + ".base-name", key);
            int    maxLevel  = section.getInt(key + ".extended-max", 5);
            int    ownedLevel = profile.getVanillaEnchantLevel(key);
            int    buyLevel  = ownedLevel + 1;
            if (buyLevel > maxLevel) {
                int slot = startSlot + (col % 7) + ((col / 7) * 9);
                if (slot < 54) {
                    set(new Slot(slot) {
                        @Override public ItemStack getItem() {
                            return new ItemBuilder(Material.ENCHANTED_BOOK)
                                    .name("&a&l" + name + " &7(Maxed)")
                                    .lore("&7You own Level " + ownedLevel).build();
                        }
                    });
                }
                col++;
                continue;
            }

            List<?> costs    = section.getList(key + ".cost-per-level");
            List<?> tierReqs = section.getList(key + ".tier-required");
            long cost = costs != null && buyLevel - 1 < costs.size()
                    ? ((Number) costs.get(buyLevel - 1)).longValue() : 99999;
            int tierReq = tierReqs != null && buyLevel - 1 < tierReqs.size()
                    ? ((Number) tierReqs.get(buyLevel - 1)).intValue() : 1;

            boolean canAfford = plugin.getShardEconomy().has(player.getUniqueId(), cost);
            boolean tierMet   = plugin.getTierManager().getTier(player).getNumber() >= tierReq;

            final String finalKey      = key;
            final String finalName     = name;
            final int    finalBuyLevel = buyLevel;
            final long   finalCost     = cost;
            final int    finalTierReq  = tierReq;

            int slot = startSlot + (col % 7) + ((col / 7) * 9);
            if (slot >= 54) break;

            set(new Slot(slot) {
                @Override public ItemStack getItem() {
                    ItemBuilder b = new ItemBuilder(Material.ENCHANTED_BOOK)
                            .name("&e" + finalName + " " + EnchantManager.toRoman(finalBuyLevel));
                    if (!tierMet) {
                        b.lore("&cRequires " + TierLevel.fromNumber(finalTierReq).display(),
                               "&7Cost: &b◆ " + finalCost);
                    } else {
                        b.lore("&7Level: &f" + EnchantManager.toRoman(finalBuyLevel),
                               "&7Cost: &b◆ " + finalCost,
                               canAfford ? "&aClick to purchase" : "&cNot enough Shards");
                    }
                    return b.build();
                }
                @Override public void onClick(Player p, InventoryClickEvent e) {
                    if (!tierMet) {
                        p.sendMessage(CC.translate("&cRequires " +
                                TierLevel.fromNumber(finalTierReq).display())); return;
                    }
                    if (!canAfford) {
                        p.sendMessage(CC.translate("&cNeed &b◆ " + finalCost)); return;
                    }
                    plugin.getShardEconomy().removeShards(p.getUniqueId(), finalCost,
                            "Vanilla enchant: " + finalKey + " L" + finalBuyLevel);

                    ClashBoxProfile prof = plugin.getProfileManager().getProfile(p);
                    if (prof != null) {
                        prof.setVanillaEnchantLevel(finalKey, finalBuyLevel);
                        prof.markDirty();
                    }

                    ItemStack book = new ItemBuilder(Material.ENCHANTED_BOOK)
                            .name("&e" + finalName + " " + EnchantManager.toRoman(finalBuyLevel))
                            .lore("&8||VANILLA:" + finalKey + ":" + finalBuyLevel + "||")
                            .build();
                    p.getInventory().addItem(book);
                    p.sendMessage(CC.translate("&aPurchased " + finalName + " " +
                            EnchantManager.toRoman(finalBuyLevel)));
                    p.playSound(p.getLocation(), Sound.LEVEL_UP, 0.7f, 1.3f);
                    build();
                }
            });
            col++;
        }
    }

    private void buildWalletInfo() {
        long balance  = plugin.getShardEconomy().getBalance(player.getUniqueId());
        ItemStack info = new ItemBuilder(Material.QUARTZ)
                .name("&b&l◆ Your Wallet")
                .lore("&7Balance: &b" + plugin.getShardEconomy().formatShort(balance),
                      "&7Tier: " + plugin.getTierManager().getTier(player).display())
                .build();
        set(new Slot(49) { @Override public ItemStack getItem() { return info; } });
    }

    private CustomEnchantType[] getEnchantsByCategory(StoreCategory cat) {
        return switch (cat) {
            case COMBAT   -> new CustomEnchantType[]{
                    CustomEnchantType.BLOODTHIRST, CustomEnchantType.EXECUTE,
                    CustomEnchantType.BERSERKER, CustomEnchantType.NEMESIS,
                    CustomEnchantType.REBOUND};
            case MINING   -> new CustomEnchantType[]{
                    CustomEnchantType.MAGNETISM, CustomEnchantType.FORTUNE,
                    CustomEnchantType.VEIN_SENSE, CustomEnchantType.EXCAVATOR};
            case SURVIVAL -> new CustomEnchantType[]{
                    CustomEnchantType.ADRENALINE, CustomEnchantType.LAST_STAND,
                    CustomEnchantType.PHANTOM};
            case ZONE     -> new CustomEnchantType[]{
                    CustomEnchantType.CORE_FORGED, CustomEnchantType.OUTER_SHELL};
            default       -> new CustomEnchantType[]{};
        };
    }

    private int getNextAvailableLevel(ClashBoxProfile profile, CustomEnchantType type) {
        int maxOwned = 0;
        for (GearSlot slot : type.getApplicableSlots()) {
            for (AppliedEnchant e : profile.getEnchants(slot)) {
                if (e.getType() == type) maxOwned = Math.max(maxOwned, e.getLevel());
            }
        }
        int maxLevel = plugin.getEnchantConfig().getInt(
                "custom-enchants." + type.name() + ".max-level", 3);
        int next = maxOwned + 1;
        return next > maxLevel ? -1 : next;
    }

    private int getTierRequirement(CustomEnchantType type, int level) {
        List<?> reqs = plugin.getEnchantConfig().getList(
                "custom-enchants." + type.name() + ".tier-required");
        if (reqs == null || level - 1 >= reqs.size()) return 1;
        Object val = reqs.get(level - 1);
        return val instanceof Number n ? n.intValue() : 1;
    }

    private long getCost(CustomEnchantType type, int level) {
        List<?> costs = plugin.getEnchantConfig().getList(
                "custom-enchants." + type.name() + ".cost");
        if (costs == null || level - 1 >= costs.size()) return Long.MAX_VALUE;
        Object val = costs.get(level - 1);
        return val instanceof Number n ? n.longValue() : Long.MAX_VALUE;
    }
}
