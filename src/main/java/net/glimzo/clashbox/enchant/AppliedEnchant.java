package net.glimzo.clashbox.enchant;

public class AppliedEnchant {

    private final CustomEnchantType type;
    private final int               level;
    private final GearSlot          slot;

    public AppliedEnchant(CustomEnchantType type, int level, GearSlot slot) {
        this.type  = type;
        this.level = level;
        this.slot  = slot;
    }

    public CustomEnchantType getType()  { return type; }
    public int               getLevel() { return level; }
    public GearSlot          getSlot()  { return slot; }

    public String toTag() {
        return type.name() + ":" + level + ":" + slot.name();
    }

    public static AppliedEnchant fromTag(String tag) {
        String[] parts = tag.split(":");
        if (parts.length < 3) return null;
        try {
            CustomEnchantType type  = CustomEnchantType.valueOf(parts[0]);
            int               level = Integer.parseInt(parts[1]);
            GearSlot          slot  = GearSlot.valueOf(parts[2]);
            return new AppliedEnchant(type, level, slot);
        } catch (Exception e) {
            return null;
        }
    }
}
