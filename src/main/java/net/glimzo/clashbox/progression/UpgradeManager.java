package net.glimzo.clashbox.progression;

import net.glimzo.clashbox.core.ClashBoxPlugin;
import net.glimzo.clashbox.economy.EconomyHook;
import net.glimzo.clashbox.player.ClashBoxProfile;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import me.pikashrey.glimzocore.utilities.chat.CC;

public class UpgradeManager implements Listener {

    private final ClashBoxPlugin plugin;
    private final EconomyHook economyHook;

    public UpgradeManager(ClashBoxPlugin plugin, EconomyHook economyHook) {
        this.plugin      = plugin;
        this.economyHook = economyHook;
    }

    public UpgradeResult purchaseUpgrade(Player player, UpgradeType type) {
        ClashBoxProfile profile = plugin.getProfileManager().getProfile(player);
        if (profile == null) return UpgradeResult.fail("Profile not loaded.");

        int currentLevel = profile.getUpgradeLevel(type);
        int maxLevel     = plugin.getCBConfig().getUpgradeMaxLevel(type.name());

        if (currentLevel >= maxLevel) {
            return UpgradeResult.fail("&cAlready at max level (" + maxLevel + ")!");
        }

        List<Long> costs = plugin.getCBConfig().getUpgradeCosts(type.name());
        if (costs == null || currentLevel >= costs.size()) {
            return UpgradeResult.fail("&cUpgrade cost not configured.");
        }

        long cost   = costs.get(currentLevel);
        long wallet = plugin.getShardEconomy().getBalance(player.getUniqueId());

        if (wallet < cost) {
            return UpgradeResult.fail(
                    "&cNot enough Shards. Need &b◆ " + cost +
                            " &c, have &b◆ " + wallet + "&c.");
        }

        plugin.getShardEconomy().removeShards(player.getUniqueId(), cost, "Upgrade: " + type.name());
        profile.setUpgradeLevel(type, currentLevel + 1);
        applyUpgradeEffect(player, type, currentLevel + 1, profile);

        return UpgradeResult.success(currentLevel + 1,
                type.displayName() + " &7upgraded to &e&lLevel " + (currentLevel + 1));
    }

    private void applyUpgradeEffect(Player player, UpgradeType type,
                                    int newLevel, ClashBoxProfile profile) {
        switch (type) {
            case SWORD_LEVEL, PICKAXE_LEVEL, ARMOR_LEVEL -> refreshArenaItems(player, profile);
            case BANK_CAPACITY -> {
                long extra = plugin.getCBConfig().getBankCapacityPerLevel();
                profile.setBankCapacity(profile.getBankCapacity() + extra);
                player.sendMessage(CC.translate(
                        "&a&lBank expanded! &7New savings capacity: &e◆ " + profile.getBankCapacity()));
            }
            case CARRY_PROTECTION ->
                    player.sendMessage(CC.translate(
                            "&e&lCarry Protection &7level " + newLevel +
                                    " - &aMid Zone death loss reduced by &e" + (newLevel * 10) + "%"));
        }
    }

    public void giveStarterKit(Player player) {
        ClashBoxProfile profile = plugin.getProfileManager().getProfile(player);
        int swordLevel = profile != null ? profile.getUpgradeLevel(UpgradeType.SWORD_LEVEL)   : 0;
        int pickLevel  = profile != null ? profile.getUpgradeLevel(UpgradeType.PICKAXE_LEVEL) : 0;
        int armorLevel = profile != null ? profile.getUpgradeLevel(UpgradeType.ARMOR_LEVEL)   : 0;

        player.getInventory().clear();
        player.getInventory().setItem(0, buildSword(swordLevel));
        player.getInventory().setItem(1, buildPickaxe(pickLevel));
        applyArmor(player, armorLevel);
    }

    public void refreshArenaItems(Player player, ClashBoxProfile profile) {
        player.getInventory().setItem(0, buildSword(profile.getUpgradeLevel(UpgradeType.SWORD_LEVEL)));
        player.getInventory().setItem(1, buildPickaxe(profile.getUpgradeLevel(UpgradeType.PICKAXE_LEVEL)));
        applyArmor(player, profile.getUpgradeLevel(UpgradeType.ARMOR_LEVEL));
    }

    private ItemStack buildSword(int level) {
        Material mat = switch (level) {
            case 0, 1 -> Material.STONE_SWORD;
            case 2, 3 -> Material.IRON_SWORD;
            default   -> Material.DIAMOND_SWORD;
        };
        ItemStack sword = new ItemStack(mat);
        ItemMeta  meta  = sword.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(CC.translate("&f&lCombat Sword &7[Lv." + level + "]"));
            if (level >= 2) meta.addEnchant(Enchantment.DAMAGE_ALL, level - 1, true);
            if (level >= 4) meta.addEnchant(Enchantment.DURABILITY, 3, true);
            sword.setItemMeta(meta);
        }
        return sword;
    }

    private ItemStack buildPickaxe(int level) {
        Material mat = switch (level) {
            case 0, 1 -> Material.STONE_PICKAXE;
            case 2, 3 -> Material.IRON_PICKAXE;
            default   -> Material.DIAMOND_PICKAXE;
        };
        ItemStack pick = new ItemStack(mat);
        ItemMeta  meta = pick.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(CC.translate("&6&lMining Pick &7[Lv." + level + "]"));
            if (level >= 1) meta.addEnchant(Enchantment.DIG_SPEED, level + 1, true);
            if (level >= 4) meta.addEnchant(Enchantment.DURABILITY, 3, true);
            pick.setItemMeta(meta);
        }
        return pick;
    }

    private void applyArmor(Player player, int level) {
        Material helmet, chest, legs, boots;
        if (level <= 1) {
            helmet = Material.LEATHER_HELMET;  chest = Material.LEATHER_CHESTPLATE;
            legs   = Material.LEATHER_LEGGINGS; boots = Material.LEATHER_BOOTS;
        } else if (level <= 3) {
            helmet = Material.IRON_HELMET;   chest = Material.IRON_CHESTPLATE;
            legs   = Material.IRON_LEGGINGS; boots = Material.IRON_BOOTS;
        } else {
            helmet = Material.DIAMOND_HELMET;   chest = Material.DIAMOND_CHESTPLATE;
            legs   = Material.DIAMOND_LEGGINGS; boots = Material.DIAMOND_BOOTS;
        }
        int prot = Math.min(level, 4);
        player.getInventory().setHelmet(enchantArmor(helmet, prot, "&b&lHelmet"));
        player.getInventory().setChestplate(enchantArmor(chest, prot, "&b&lChestplate"));
        player.getInventory().setLeggings(enchantArmor(legs, prot, "&b&lLeggings"));
        player.getInventory().setBoots(enchantArmor(boots, prot, "&b&lBoots"));
    }

    private ItemStack enchantArmor(Material mat, int protLevel, String name) {
        ItemStack item = new ItemStack(mat);
        ItemMeta  meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(CC.translate(name));
            if (protLevel > 0)
                meta.addEnchant(Enchantment.PROTECTION_ENVIRONMENTAL, protLevel, true);
            item.setItemMeta(meta);
        }
        return item;
    }

    public static class UpgradeResult {
        private final boolean success;
        private final int     newLevel;
        private final String  message;

        private UpgradeResult(boolean success, int newLevel, String message) {
            this.success  = success;
            this.newLevel = newLevel;
            this.message  = message;
        }

        public static UpgradeResult success(int level, String msg) {
            return new UpgradeResult(true, level, msg);
        }
        public static UpgradeResult fail(String msg) {
            return new UpgradeResult(false, -1, msg);
        }

        public boolean isSuccess()   { return success; }
        public int     getNewLevel() { return newLevel; }
        public String  getMessage()  { return message; }
    }
}
