package net.glimzo.clashbox.sets;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public enum SetPiece {

    HELMET("Helmet", 5),
    CHESTPLATE("Chestplate", 6),
    LEGGINGS("Leggings", 7),
    BOOTS("Boots", 8);

    private final String displayName;
    private final int    armorSlotIndex;

    SetPiece(String displayName, int armorSlotIndex) {
        this.displayName    = displayName;
        this.armorSlotIndex = armorSlotIndex;
    }

    public String getDisplayName()    { return displayName; }
    public int    getArmorSlotIndex() { return armorSlotIndex; }

    public ItemStack getFromPlayer(Player player) {
        return switch (this) {
            case HELMET     -> player.getInventory().getHelmet();
            case CHESTPLATE -> player.getInventory().getChestplate();
            case LEGGINGS   -> player.getInventory().getLeggings();
            case BOOTS      -> player.getInventory().getBoots();
        };
    }

    public void setOnPlayer(Player player, ItemStack item) {
        switch (this) {
            case HELMET     -> player.getInventory().setHelmet(item);
            case CHESTPLATE -> player.getInventory().setChestplate(item);
            case LEGGINGS   -> player.getInventory().setLeggings(item);
            case BOOTS      -> player.getInventory().setBoots(item);
        }
    }

    public static SetPiece fromString(String name) {
        if (name == null) return null;
        try { return valueOf(name.toUpperCase()); }
        catch (IllegalArgumentException e) { return null; }
    }
}
