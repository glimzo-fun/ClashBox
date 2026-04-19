package net.glimzo.clashbox.progression;

public enum UpgradeType {
    SWORD_LEVEL,
    PICKAXE_LEVEL,
    ARMOR_LEVEL,
    BANK_CAPACITY,
    CARRY_PROTECTION;

    public String displayName() {
        return switch (this) {
            case SWORD_LEVEL      -> "&cSword Upgrade";
            case PICKAXE_LEVEL    -> "&6Pickaxe Upgrade";
            case ARMOR_LEVEL      -> "&bArmor Upgrade";
            case BANK_CAPACITY    -> "&aBank Expansion";
            case CARRY_PROTECTION -> "&eCarry Protection";
        };
    }

    public String description() {
        return switch (this) {
            case SWORD_LEVEL      -> "Upgrades your sword sharpness level";
            case PICKAXE_LEVEL    -> "Upgrades your pickaxe efficiency level";
            case ARMOR_LEVEL      -> "Upgrades your armor protection level";
            case BANK_CAPACITY    -> "Increases your maximum bank storage";
            case CARRY_PROTECTION -> "Reduces ore loss on death in Mid Zone";
        };
    }
}
