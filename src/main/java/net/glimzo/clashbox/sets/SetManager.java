package net.glimzo.clashbox.sets;

import me.pikashrey.glimzocore.utilities.chat.CC;
import net.glimzo.clashbox.core.ClashBoxPlugin;
import net.glimzo.clashbox.player.ClashBoxProfile;
import net.glimzo.clashbox.utilities.ConfigFile;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.*;

public class SetManager {

    private static final String TAG_PREFIX_SET     = "||SET:";
    private static final String TAG_PREFIX_GLASS   = "||GLASS:";
    private static final String TAG_PREFIX_CRAFTED = "||GLASS_CRAFTED:";
    private static final int    BONUS_CHECK_TICKS  = 80;

    private final ClashBoxPlugin        plugin;
    private final Map<String, ArmorSet> sets        = new LinkedHashMap<>();
    private final Map<UUID, String>     activeBonus = new HashMap<>();
    private BukkitTask                  bonusTask;
    private FileConfiguration           setsCfg;

    private final Map<String, Location> armorStandLocations = new HashMap<>();
    private final Map<String, Location> npcLocations        = new HashMap<>();

    public SetManager(ClashBoxPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        setsCfg = ConfigFile.load(plugin, "sets.yml");
        sets.clear();
        ConfigurationSection section = setsCfg.getConfigurationSection("sets");
        if (section == null) return;
        for (String id : section.getKeys(false)) {
            ArmorSet set = parseSet(id, section.getConfigurationSection(id));
            if (set != null) sets.put(id, set);
        }
        loadLocations();
        plugin.getLogger().info("[ClashBox] Loaded " + sets.size() + " armor set(s).");
    }

    private ArmorSet parseSet(String id, ConfigurationSection s) {
        if (s == null) return null;

        String  name      = s.getString("name", id);
        int     starNum   = s.getInt("star", 1);
        String  color     = s.getString("color", "&7");
        int     tierReq   = s.getInt("tier-required", 1);
        boolean starter   = s.getBoolean("starter", false);
        long    shardCost = s.getLong("shard-cost", 0L);

        Material glassMat = Material.AIR;
        short    glassDmg = 0;
        if (s.contains("glass-material")) {
            String[] parts = s.getString("glass-material", "STAINED_GLASS:0").split(":");
            try {
                glassMat = Material.valueOf(parts[0]);
                if (parts.length > 1) glassDmg = Short.parseShort(parts[1]);
            } catch (Exception ignored) {}
        }

        List<String> zoneList  = s.getStringList("glass-drop-zone");
        String[]     dropZones = zoneList.toArray(new String[0]);

        int    intermediateGlass = s.getInt("intermediate-glass-cost", 12);
        int    intermediatePiece = s.getInt("intermediate-piece-cost", 3);
        String upgradeFrom       = s.getString("upgrade-from", null);

        PotionEffectType bonusEffect = null;
        int    bonusAmp  = 0;
        String bonusDesc = "";
        ConfigurationSection bonus = s.getConfigurationSection("set-bonus");
        if (bonus != null) {
            try { bonusEffect = PotionEffectType.getByName(bonus.getString("effect", "")); }
            catch (Exception ignored) {}
            bonusAmp  = bonus.getInt("amplifier", 0);
            bonusDesc = bonus.getString("description", "");
        }

        ArmorSet set = new ArmorSet(id, name, SetTier.fromNumber(starNum), color,
                tierReq, starter, shardCost, glassMat, glassDmg, dropZones,
                intermediateGlass, intermediatePiece, upgradeFrom,
                bonusEffect, bonusAmp, bonusDesc);

        ConfigurationSection pieces = s.getConfigurationSection("pieces");
        if (pieces != null) {
            for (SetPiece piece : SetPiece.values()) {
                String key = piece.name().toLowerCase();
                ConfigurationSection pc = pieces.getConfigurationSection(key);
                if (pc == null) continue;

                // all sets use leather armor so colors can be applied via LeatherArmorMeta
                set.setPieceBaseMaterial(piece, leatherMaterial(piece));

                Map<Enchantment, Integer> enchants = new LinkedHashMap<>();
                ConfigurationSection enc = pc.getConfigurationSection("enchants");
                if (enc != null) {
                    for (String encKey : enc.getKeys(false)) {
                        try {
                            Enchantment e = Enchantment.getByName(encKey);
                            if (e != null) enchants.put(e, enc.getInt(encKey));
                        } catch (Exception ignored) {}
                    }
                }
                set.setPieceEnchants(piece, enchants);
            }
        }
        return set;
    }

    private Material leatherMaterial(SetPiece piece) {
        return switch (piece) {
            case HELMET     -> Material.LEATHER_HELMET;
            case CHESTPLATE -> Material.LEATHER_CHESTPLATE;
            case LEGGINGS   -> Material.LEATHER_LEGGINGS;
            case BOOTS      -> Material.LEATHER_BOOTS;
        };
    }

    private void loadLocations() {
        loadLocationMap("armor-stands", armorStandLocations);
        loadLocationMap("npc-locations", npcLocations);
    }

    private void loadLocationMap(String key, Map<String, Location> target) {
        target.clear();
        ConfigurationSection sec = setsCfg.getConfigurationSection(key);
        if (sec == null) return;
        for (String id : sec.getKeys(false)) {
            Location loc = readLocation(sec.getConfigurationSection(id));
            if (loc != null) target.put(id, loc);
        }
    }

    private Location readLocation(ConfigurationSection s) {
        if (s == null) return null;
        String worldName = s.getString("world", "box");
        org.bukkit.World w = plugin.getServer().getWorld(worldName);
        if (w == null) return null;
        return new Location(w, s.getDouble("x"), s.getDouble("y"),
                s.getDouble("z"), (float) s.getDouble("yaw", 0), 0);
    }

    public void saveLocation(String type, String setId, Location loc) {
        String path = type + "." + setId;
        setsCfg.set(path + ".world", loc.getWorld().getName());
        setsCfg.set(path + ".x",     loc.getX());
        setsCfg.set(path + ".y",     loc.getY());
        setsCfg.set(path + ".z",     loc.getZ());
        setsCfg.set(path + ".yaw",   (double) loc.getYaw());
        File file = new File(plugin.getDataFolder(), "sets.yml");
        try { setsCfg.save(file); } catch (Exception e) {
            plugin.getLogger().warning("[ClashBox] Could not save sets.yml: " + e.getMessage());
        }
        if (type.equals("armor-stands")) armorStandLocations.put(setId, loc);
        else npcLocations.put(setId, loc);
    }

    public void startBonusTask() {
        bonusTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : plugin.getServer().getOnlinePlayers()) {
                checkAndApplyBonus(p);
            }
        }, BONUS_CHECK_TICKS, BONUS_CHECK_TICKS);
    }

    public void stopBonusTask() {
        if (bonusTask != null) bonusTask.cancel();
    }

    private void checkAndApplyBonus(Player player) {
        UUID uuid = player.getUniqueId();
        ClashBoxProfile profile = plugin.getProfileManager().getProfile(player);
        if (profile == null) return;

        for (ArmorSet set : sets.values()) {
            if (isWearingFullSet(player, set, profile)) {
                String current = activeBonus.get(uuid);
                if (!set.getId().equals(current)) {
                    removeSetBonus(player);
                    applySetBonus(player, set);
                    activeBonus.put(uuid, set.getId());
                }
                return;
            }
        }
        if (activeBonus.containsKey(uuid)) {
            removeSetBonus(player);
            activeBonus.remove(uuid);
        }
    }

    public boolean isWearingFullSet(Player player, ArmorSet set, ClashBoxProfile profile) {
        for (SetPiece piece : SetPiece.values()) {
            String owned = profile.getOwnedSetPiece(piece);
            if (!set.getId().equals(owned)) return false;
            if (!isPieceItem(piece.getFromPlayer(player), set, piece)) return false;
        }
        return true;
    }

    public void applySetBonus(Player player, ArmorSet set) {
        if (set.getSetBonusEffect() == null) return;
        player.addPotionEffect(new PotionEffect(
                set.getSetBonusEffect(), Integer.MAX_VALUE,
                set.getSetBonusAmplifier(), false, false), true);
    }

    public void removeSetBonus(Player player) {
        for (ArmorSet set : sets.values()) {
            if (set.getSetBonusEffect() != null) {
                player.removePotionEffect(set.getSetBonusEffect());
            }
        }
    }

    public ItemStack buildGlassItem(ArmorSet set) {
        ItemStack item = new ItemStack(set.getGlassMaterial(), 1);
        item.setDurability(set.getGlassDamage());
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        meta.setDisplayName(CC.translate(set.getColor() + set.getName() + " Glass"));
        meta.setLore(Arrays.asList(
                CC.translate("&8--------------"),
                CC.translate("&7Used to craft &e" + set.getName() + " Glass"),
                CC.translate("&7Trade " + set.getIntermediateGlassCost() + "x at the &bOffers &7NPC."),
                CC.translate("&8" + set.glassItemTag())
        ));
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack buildIntermediateItem(ArmorSet set) {
        ItemStack item = new ItemStack(set.getGlassMaterial(), 1);
        item.setDurability(set.getGlassDamage());
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        meta.setDisplayName(CC.translate(set.getColor() + "&l" + set.getName() + " Glass"));
        meta.setLore(Arrays.asList(
                CC.translate("&8--------------"),
                CC.translate("&7Crafted from " + set.getIntermediateGlassCost() + "x raw glass."),
                CC.translate("&7Trade " + set.getIntermediatePieceCost() + "x + previous piece"),
                CC.translate("&7at the &bOffers &7NPC to upgrade."),
                CC.translate("&8" + set.intermediateItemTag())
        ));
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack buildPieceItem(ArmorSet set, SetPiece piece) {
        Material base = leatherMaterial(piece);
        ItemStack item = new ItemStack(base, 1);
        LeatherArmorMeta meta = (LeatherArmorMeta) item.getItemMeta();
        if (meta == null) return item;

        meta.setColor(tintColor(set.getColor()));
        meta.setDisplayName(CC.translate(
                set.getColor() + "&l" + set.getName() + "'s " + piece.getDisplayName()));

        List<String> lore = new ArrayList<>();
        lore.add(CC.translate("&8--------------"));
        lore.add(CC.translate("&7Set: " + set.getTier().display()));
        lore.add(CC.translate("&8--------------"));
        lore.add(CC.translate("&7Set Bonus: &e" + set.getSetBonusDescription()));
        lore.add(CC.translate("&8--------------"));
        lore.add(CC.translate("&8" + set.pieceItemTag(piece)));
        meta.setLore(lore);

        // unsafe=true bypasses vanilla level cap - supports levels 1-200+ as configured
        for (Map.Entry<Enchantment, Integer> e : set.getEnchants(piece).entrySet()) {
            meta.addEnchant(e.getKey(), e.getValue(), true);
        }
        meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);

        item.setItemMeta(meta);
        return item;
    }

    public boolean isPieceItem(ItemStack item, ArmorSet set, SetPiece piece) {
        if (item == null || !item.hasItemMeta() || !item.getItemMeta().hasLore()) return false;
        String target = set.pieceItemTag(piece).replace("||", "");
        return item.getItemMeta().getLore().stream()
                .anyMatch(l -> CC.strip(l).contains(target));
    }

    public String extractSetId(ItemStack item) {
        if (item == null || !item.hasItemMeta() || !item.getItemMeta().hasLore()) return null;
        for (String line : item.getItemMeta().getLore()) {
            String s = CC.strip(line);
            if (s.startsWith("||SET:")) {
                String[] parts = s.replace("||", "").split(":");
                return parts.length >= 2 ? parts[1] : null;
            }
        }
        return null;
    }

    public SetPiece extractSetPiece(ItemStack item) {
        if (item == null || !item.hasItemMeta() || !item.getItemMeta().hasLore()) return null;
        for (String line : item.getItemMeta().getLore()) {
            String s = CC.strip(line);
            if (s.startsWith("||SET:")) {
                String[] parts = s.replace("||", "").split(":");
                return parts.length >= 3 ? SetPiece.fromString(parts[2]) : null;
            }
        }
        return null;
    }

    public String extractGlassSetId(ItemStack item) {
        if (item == null || !item.hasItemMeta() || !item.getItemMeta().hasLore()) return null;
        for (String line : item.getItemMeta().getLore()) {
            String s = CC.strip(line);
            if (s.startsWith("||GLASS:"))
                return s.replace("||GLASS:", "").replace("||", "");
            if (s.startsWith("||GLASS_CRAFTED:"))
                return s.replace("||GLASS_CRAFTED:", "").replace("||", "");
        }
        return null;
    }

    public boolean isIntermediateGlass(ItemStack item) {
        if (item == null || !item.hasItemMeta() || !item.getItemMeta().hasLore()) return false;
        return item.getItemMeta().getLore().stream()
                .anyMatch(l -> CC.strip(l).startsWith("||GLASS_CRAFTED:"));
    }

    public boolean isRawGlass(ItemStack item) {
        if (item == null || !item.hasItemMeta() || !item.getItemMeta().hasLore()) return false;
        return item.getItemMeta().getLore().stream()
                .anyMatch(l -> CC.strip(l).startsWith("||GLASS:"));
    }

    public int countItem(Player player, String tag) {
        int count = 0;
        for (ItemStack i : player.getInventory().getContents()) {
            if (i == null || !i.hasItemMeta() || !i.getItemMeta().hasLore()) continue;
            if (i.getItemMeta().getLore().stream()
                    .anyMatch(l -> CC.strip(l).contains(tag))) count += i.getAmount();
        }
        return count;
    }

    public void removeItems(Player player, String tag, int amount) {
        int remaining = amount;
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack item = contents[i];
            if (item == null || !item.hasItemMeta() || !item.getItemMeta().hasLore()) continue;
            if (!item.getItemMeta().getLore().stream()
                    .anyMatch(l -> CC.strip(l).contains(tag))) continue;
            if (item.getAmount() <= remaining) {
                remaining -= item.getAmount();
                player.getInventory().setItem(i, null);
            } else {
                item.setAmount(item.getAmount() - remaining);
                remaining = 0;
            }
        }
    }

    public boolean removePieceFromInventory(Player player, ArmorSet set, SetPiece piece) {
        String tag = set.pieceItemTag(piece).replace("||", "");
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item == null || !item.hasItemMeta() || !item.getItemMeta().hasLore()) continue;
            if (item.getItemMeta().getLore().stream()
                    .anyMatch(l -> CC.strip(l).contains(tag))) {
                player.getInventory().setItem(i, null);
                return true;
            }
        }
        ItemStack worn = piece.getFromPlayer(player);
        if (worn != null && worn.hasItemMeta() && worn.getItemMeta().hasLore()
                && worn.getItemMeta().getLore().stream()
                .anyMatch(l -> CC.strip(l).contains(tag))) {
            piece.setOnPlayer(player, null);
            return true;
        }
        return false;
    }

    private Color tintColor(String colorCode) {
        return switch (colorCode) {
            case "&c" -> Color.fromRGB(255, 50,  50);
            case "&e" -> Color.fromRGB(255, 220, 0);
            case "&d" -> Color.fromRGB(230, 70,  230);
            case "&9" -> Color.fromRGB(50,  80,  220);
            case "&8" -> Color.fromRGB(40,  40,  40);
            case "&b" -> Color.fromRGB(50,  220, 230);
            case "&a" -> Color.fromRGB(50,  200, 50);
            case "&6" -> Color.fromRGB(220, 140, 0);
            case "&5" -> Color.fromRGB(130, 30,  180);
            default   -> Color.WHITE;
        };
    }

    public Map<String, ArmorSet> getSets()                  { return sets; }
    public ArmorSet              getSet(String id)          { return sets.get(id); }
    public Map<String, Location> getArmorStandLocations()   { return armorStandLocations; }
    public Map<String, Location> getNpcLocations()          { return npcLocations; }
    public FileConfiguration     getSetsCfg()               { return setsCfg; }
}
