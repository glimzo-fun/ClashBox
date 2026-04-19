package net.glimzo.clashbox.arena;

import org.bukkit.Material;

public enum OreType {

    COAL     (Material.COAL_ORE,     Material.COAL_BLOCK,     "&8Coal"),
    IRON     (Material.IRON_ORE,     Material.IRON_BLOCK,     "&7Iron"),
    GOLD     (Material.GOLD_ORE,     Material.GOLD_BLOCK,     "&6Gold"),
    REDSTONE (Material.REDSTONE_ORE, Material.REDSTONE_BLOCK, "&cRedstone"),
    LAPIS    (Material.LAPIS_ORE,    Material.LAPIS_BLOCK,    "&9Lapis"),
    DIAMOND  (Material.DIAMOND_ORE,  Material.DIAMOND_BLOCK,  "&bDiamond"),
    EMERALD  (Material.EMERALD_ORE,  Material.EMERALD_BLOCK,  "&aEmerald"),
    DARK_OAK (Material.LOG_2,        Material.WOOD,           "&8Dark Oak"); // LOG_2 data=1 for dark oak

    private final Material oreMaterial;
    private final Material blockMaterial;
    private final String   displayName;

    OreType(Material ore, Material block, String display) {
        this.oreMaterial   = ore;
        this.blockMaterial = block;
        this.displayName   = display;
    }

    public Material getOreMaterial()   { return oreMaterial; }
    public Material getBlockMaterial() { return blockMaterial; }
    public String   getDisplayName()   { return displayName; }

    public boolean matches(Material mat) {
        return mat == oreMaterial || mat == blockMaterial;
    }

    public boolean isOreMaterial(Material mat) {
        return mat == oreMaterial;
    }

    public static OreType fromString(String name) {
        if (name == null) return null;
        try {
            return valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
