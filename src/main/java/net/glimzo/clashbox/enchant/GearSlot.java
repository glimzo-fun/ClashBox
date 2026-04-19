package net.glimzo.clashbox.enchant;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public enum GearSlot {
    SWORD, PICKAXE, HELMET, CHESTPLATE, LEGGINGS, BOOTS;

    public boolean matches(ItemStack item) {
        if (item == null) return false;
        Material m = item.getType();
        return switch (this) {
            case SWORD      -> m == Material.WOOD_SWORD || m == Material.STONE_SWORD ||
                               m == Material.IRON_SWORD || m == Material.GOLD_SWORD ||
                               m == Material.DIAMOND_SWORD;
            case PICKAXE    -> m == Material.WOOD_PICKAXE || m == Material.STONE_PICKAXE ||
                               m == Material.IRON_PICKAXE || m == Material.GOLD_PICKAXE ||
                               m == Material.DIAMOND_PICKAXE;
            case HELMET     -> m.name().endsWith("_HELMET");
            case CHESTPLATE -> m.name().endsWith("_CHESTPLATE");
            case LEGGINGS   -> m.name().endsWith("_LEGGINGS");
            case BOOTS      -> m.name().endsWith("_BOOTS");
        };
    }

    public static GearSlot fromItem(ItemStack item) {
        if (item == null) return null;
        for (GearSlot s : values()) {
            if (s.matches(item)) return s;
        }
        return null;
    }
}
