package net.glimzo.clashbox.enchant;

import org.bukkit.Material;
import java.util.List;

public enum EnchantNpcCategory {

    SWORD(
            "&c⚔ Sword Enchanter",
            "&7Master of blade arts.",
            Material.DIAMOND_SWORD,
            List.of(
                    CustomEnchantType.BLOODTHIRST,
                    CustomEnchantType.EXECUTE,
                    CustomEnchantType.BERSERKER,
                    CustomEnchantType.NEMESIS,
                    CustomEnchantType.CORE_FORGED
            ),
            List.of("DAMAGE_ALL", "KNOCKBACK", "FIRE_ASPECT", "LOOT_BONUS_MOBS", "DURABILITY")
    ),

    ARMOUR(
            "&b\uD83D\uDEE1 Armour Enchanter",
            "&7Forge your defences.",
            Material.DIAMOND_CHESTPLATE,
            List.of(
                    CustomEnchantType.REBOUND,
                    CustomEnchantType.LAST_STAND,
                    CustomEnchantType.PHANTOM,
                    CustomEnchantType.OUTER_SHELL,
                    CustomEnchantType.ADRENALINE
            ),
            List.of("PROTECTION_ENVIRONMENTAL", "THORNS", "FEATHER_FALLING", "DURABILITY")
    ),

    TOOLS(
            "&6\u26CF Tools Enchanter",
            "&7Speed up your harvest.",
            Material.DIAMOND_PICKAXE,
            List.of(
                    CustomEnchantType.MAGNETISM,
                    CustomEnchantType.FORTUNE,
                    CustomEnchantType.VEIN_SENSE,
                    CustomEnchantType.EXCAVATOR
            ),
            List.of("DIG_SPEED", "DURABILITY")
    ),

    PROJECTILE(
            "&a\uD83C\uDFF9 Projectile Enchanter",
            "&7Unleash ranged supremacy.",
            Material.ARROW,
            List.of(),
            List.of("ARROW_DAMAGE", "ARROW_KNOCKBACK", "ARROW_FIRE", "ARROW_INFINITE", "DURABILITY")
    );

    private final String                  title;
    private final String                  subtitle;
    private final Material                icon;
    private final List<CustomEnchantType> customEnchants;
    private final List<String>            vanillaKeys;

    EnchantNpcCategory(String title, String subtitle, Material icon,
                       List<CustomEnchantType> custom, List<String> vanilla) {
        this.title          = title;
        this.subtitle       = subtitle;
        this.icon           = icon;
        this.customEnchants = custom;
        this.vanillaKeys    = vanilla;
    }

    public String                  getTitle()          { return title; }
    public String                  getSubtitle()       { return subtitle; }
    public Material                getIcon()           { return icon; }
    public List<CustomEnchantType> getCustomEnchants() { return customEnchants; }
    public List<String>            getVanillaKeys()    { return vanillaKeys; }

    public String getAnvilHint() {
        return "&8[&5\u2726 Enchanter&8] &7After purchasing a book, &ecombine your gear piece " +
               "&7+ the &erespective enchant book &7at the &banvil in front of me &7to apply it!";
    }

    public static EnchantNpcCategory fromString(String name) {
        if (name == null) return null;
        try { return valueOf(name.toUpperCase()); }
        catch (IllegalArgumentException e) { return null; }
    }
}
