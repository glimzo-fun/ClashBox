package net.glimzo.clashbox.enchant;

import java.util.List;

public enum CustomEnchantType {

    REBOUND      ("Rebound",      List.of(GearSlot.CHESTPLATE)),
    BLOODTHIRST  ("Bloodthirst",  List.of(GearSlot.SWORD)),
    EXECUTE      ("Execute",      List.of(GearSlot.SWORD)),
    BERSERKER    ("Berserker",    List.of(GearSlot.SWORD)),
    NEMESIS      ("Nemesis",      List.of(GearSlot.SWORD)),
    MAGNETISM    ("Magnetism",    List.of(GearSlot.PICKAXE)),
    FORTUNE      ("Fortune",      List.of(GearSlot.PICKAXE)),
    VEIN_SENSE   ("Vein Sense",   List.of(GearSlot.PICKAXE)),
    EXCAVATOR    ("Excavator",    List.of(GearSlot.PICKAXE)),
    ADRENALINE   ("Adrenaline",   List.of(GearSlot.BOOTS)),
    LAST_STAND   ("Last Stand",   List.of(GearSlot.CHESTPLATE, GearSlot.LEGGINGS)),
    PHANTOM      ("Phantom",      List.of(GearSlot.HELMET)),
    CORE_FORGED  ("Core Forged",  List.of(GearSlot.SWORD)),
    OUTER_SHELL  ("Outer Shell",  List.of(GearSlot.HELMET));

    private final String         displayName;
    private final List<GearSlot> applicableSlots;

    CustomEnchantType(String displayName, List<GearSlot> slots) {
        this.displayName     = displayName;
        this.applicableSlots = slots;
    }

    public String          getDisplayName()     { return displayName; }
    public List<GearSlot>  getApplicableSlots() { return applicableSlots; }

    public boolean isApplicableTo(GearSlot slot) {
        return applicableSlots.contains(slot);
    }
}
