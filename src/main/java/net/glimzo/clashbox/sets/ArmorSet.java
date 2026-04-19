package net.glimzo.clashbox.sets;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.potion.PotionEffectType;

import java.util.EnumMap;
import java.util.Map;

public class ArmorSet {

    private final String  id;
    private final String  name;
    private final SetTier tier;
    private final String  color;
    private final int     tierRequired;
    private final boolean starter;
    private final long    shardCost;

    private final Material glassMaterial;
    private final short    glassDamage;
    private final String[] glassDropZones;

    private final int    intermediateGlassCost;
    private final int    intermediatePieceCost;
    private final String upgradeFromSetId;

    private final PotionEffectType setBonusEffect;
    private final int              setBonusAmplifier;
    private final String           setBonusDescription;

    private final Map<SetPiece, Map<Enchantment, Integer>> pieceEnchants =
            new EnumMap<>(SetPiece.class);
    private final Map<SetPiece, Material> pieceBaseMaterial =
            new EnumMap<>(SetPiece.class);

    public ArmorSet(String id, String name, SetTier tier, String color,
                    int tierRequired, boolean starter, long shardCost,
                    Material glassMaterial, short glassDamage, String[] glassDropZones,
                    int intermediateGlassCost, int intermediatePieceCost,
                    String upgradeFromSetId,
                    PotionEffectType setBonusEffect, int setBonusAmplifier,
                    String setBonusDescription) {
        this.id                    = id;
        this.name                  = name;
        this.tier                  = tier;
        this.color                 = color;
        this.tierRequired          = tierRequired;
        this.starter               = starter;
        this.shardCost             = shardCost;
        this.glassMaterial         = glassMaterial;
        this.glassDamage           = glassDamage;
        this.glassDropZones        = glassDropZones;
        this.intermediateGlassCost = intermediateGlassCost;
        this.intermediatePieceCost = intermediatePieceCost;
        this.upgradeFromSetId      = upgradeFromSetId;
        this.setBonusEffect        = setBonusEffect;
        this.setBonusAmplifier     = setBonusAmplifier;
        this.setBonusDescription   = setBonusDescription;
    }

    public void setPieceEnchants(SetPiece piece, Map<Enchantment, Integer> enchants) {
        pieceEnchants.put(piece, enchants);
    }

    public void setPieceBaseMaterial(SetPiece piece, Material material) {
        pieceBaseMaterial.put(piece, material);
    }

    public Map<Enchantment, Integer> getEnchants(SetPiece piece) {
        return pieceEnchants.getOrDefault(piece, new java.util.HashMap<>());
    }

    public Material getBaseMaterial(SetPiece piece) {
        return pieceBaseMaterial.getOrDefault(piece, Material.IRON_HELMET);
    }

    public String           getId()                    { return id; }
    public String           getName()                  { return name; }
    public SetTier          getTier()                  { return tier; }
    public String           getColor()                 { return color; }
    public int              getTierRequired()          { return tierRequired; }
    public boolean          isStarter()                { return starter; }
    public long             getShardCost()             { return shardCost; }
    public Material         getGlassMaterial()         { return glassMaterial; }
    public short            getGlassDamage()           { return glassDamage; }
    public String[]         getGlassDropZones()        { return glassDropZones; }
    public int              getIntermediateGlassCost() { return intermediateGlassCost; }
    public int              getIntermediatePieceCost() { return intermediatePieceCost; }
    public String           getUpgradeFromSetId()      { return upgradeFromSetId; }
    public PotionEffectType getSetBonusEffect()        { return setBonusEffect; }
    public int              getSetBonusAmplifier()     { return setBonusAmplifier; }
    public String           getSetBonusDescription()   { return setBonusDescription; }

    public String displayName()       { return color + name + "'s Set"; }
    public String glassItemTag()      { return "||GLASS:" + id + "||"; }
    public String intermediateItemTag() { return "||GLASS_CRAFTED:" + id + "||"; }
    public String pieceItemTag(SetPiece piece) { return "||SET:" + id + ":" + piece.name() + "||"; }
}
