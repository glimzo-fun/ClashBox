package net.glimzo.clashbox.sell;

import me.pikashrey.glimzocore.utilities.chat.CC;
import net.glimzo.clashbox.bank.UnlockableFeature;
import net.glimzo.clashbox.core.ClashBoxPlugin;
import net.glimzo.clashbox.player.ClashBoxProfile;
import net.glimzo.clashbox.tier.NotorietySource;
import net.glimzo.clashbox.zone.ZoneType;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class SellManager implements Listener {

    private final ClashBoxPlugin plugin;

    private static final Map<Material, Material> ORE_TO_DROP = new EnumMap<>(Material.class);
    private static final Map<Material, Long> BASE_PRICES = new EnumMap<>(Material.class);

    static {
        ORE_TO_DROP.put(Material.COAL_ORE,      Material.COAL);
        ORE_TO_DROP.put(Material.IRON_ORE,      Material.IRON_ORE);
        ORE_TO_DROP.put(Material.GOLD_ORE,      Material.GOLD_ORE);
        ORE_TO_DROP.put(Material.REDSTONE_ORE,  Material.REDSTONE);
        ORE_TO_DROP.put(Material.LAPIS_ORE,     Material.INK_SACK);
        ORE_TO_DROP.put(Material.DIAMOND_ORE,   Material.DIAMOND);
        ORE_TO_DROP.put(Material.EMERALD_ORE,   Material.EMERALD);
        ORE_TO_DROP.put(Material.LOG,           Material.LOG);
        ORE_TO_DROP.put(Material.LOG_2,         Material.LOG_2);
    }

    public SellManager(ClashBoxPlugin plugin) {
        this.plugin = plugin;
        loadPrices();
    }

    private void loadPrices() {
        var cfg = plugin.getConfig();
        BASE_PRICES.put(Material.COAL,     cfg.getLong("sell-prices.COAL",    8L));
        BASE_PRICES.put(Material.IRON_ORE, cfg.getLong("sell-prices.IRON",   25L));
        BASE_PRICES.put(Material.GOLD_ORE, cfg.getLong("sell-prices.GOLD",   60L));
        BASE_PRICES.put(Material.REDSTONE, cfg.getLong("sell-prices.REDSTONE",45L));
        BASE_PRICES.put(Material.INK_SACK, cfg.getLong("sell-prices.LAPIS",  50L));
        BASE_PRICES.put(Material.DIAMOND,  cfg.getLong("sell-prices.DIAMOND",250L));
        BASE_PRICES.put(Material.EMERALD,  cfg.getLong("sell-prices.EMERALD",400L));
        BASE_PRICES.put(Material.LOG,      cfg.getLong("sell-prices.LOG",     5L));
        BASE_PRICES.put(Material.LOG_2,    cfg.getLong("sell-prices.LOG",     5L));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getPlayerStateManager().isAliveInArena(player)) return;

        Material type = event.getBlock().getType();
        if (!ORE_TO_DROP.containsKey(type) && type != Material.LOG && type != Material.LOG_2) return;

        if (!plugin.getTierManager().hasUnlocked(player, UnlockableFeature.ORE_SELLING)) {
            event.setCancelled(true);
            player.sendMessage(CC.translate("&cReach &7Tier II &cto unlock ore selling."));
            return;
        }

        event.setExpToDrop(0);
        // 1.8 has no setDropItems() - cancel to suppress vanilla drops, then remove block manually.
        // OreRegenerationManager listens at MONITOR with ignoreCancelled=false so it still fires.
        event.setCancelled(true);
        event.getBlock().setType(Material.AIR);

        ZoneType zone = plugin.getZoneManager().detectZone(event.getBlock().getLocation());
        Material drop = ORE_TO_DROP.getOrDefault(type, type);

        ItemStack item = buildOreItem(drop, type, zone);
        Location loc = event.getBlock().getLocation().add(0.5, 0.5, 0.5);
        Item dropped = player.getWorld().dropItem(loc, item);
        dropped.setPickupDelay(0);

        applyEnchantEffects(player, drop, zone);
    }

    private ItemStack buildOreItem(Material drop, Material oreMaterial, ZoneType zone) {
        long basePrice = BASE_PRICES.getOrDefault(drop, 10L);
        long zonePrice = applyZoneMultiplierCalc(basePrice, zone);

        ItemStack item = new ItemStack(drop == Material.INK_SACK
                ? Material.INK_SACK : drop, 1);

        if (drop == Material.INK_SACK) {
            item.setDurability((short) 4);
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.setDisplayName(CC.translate(zoneColor(zone) + oreName(oreMaterial) +
                " &8[&b◆ " + zonePrice + "&8]"));

        List<String> lore = new ArrayList<>();
        lore.add(CC.translate("&7Zone: " + zone.displayName()));
        lore.add(CC.translate("&7Value: &b◆ " + zonePrice));
        lore.add(CC.translate("&8Sell at the hub sell area."));
        lore.add(CC.translate("&8||ORE:" + oreMaterial.name() + ":" + zone.name() + ":" + zonePrice + "||"));
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    public void sellAllOres(Player player) {
        if (!plugin.getTierManager().hasUnlocked(player, UnlockableFeature.ORE_SELLING)) {
            player.sendMessage(CC.translate("&cOre selling not yet unlocked."));
            return;
        }

        long totalEarned = 0;
        int  itemsSold   = 0;
        Map<String, Long> breakdown = new LinkedHashMap<>();

        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item == null || item.getType() == Material.AIR) continue;
            if (!item.hasItemMeta() || !item.getItemMeta().hasLore()) continue;

            List<String> lore = item.getItemMeta().getLore();
            String tag = extractOreTag(lore);
            if (tag == null) continue;

            String[] parts = tag.split(":");
            if (parts.length < 4) continue;

            long priceEach = Long.parseLong(parts[3]);
            int  qty = item.getAmount();
            long rawEarned = priceEach * qty;

            // update session sold value before the softcap check so the running
            // total is accurate for this entire batch
            ClashBoxProfile profile = plugin.getProfileManager().getProfile(player);
            if (profile != null) {
                profile.setSessionSoldValue(profile.getSessionSoldValue() + rawEarned);
            }

            long earned = plugin.getShardEconomy().applySoftcap(player.getUniqueId(), rawEarned);

            totalEarned += earned;
            itemsSold   += qty;
            breakdown.merge(parts[1], earned, Long::sum);

            player.getInventory().setItem(i, null);
        }

        if (itemsSold == 0) {
            player.sendMessage(CC.translate("&7You have no ores to sell."));
            return;
        }

        plugin.getShardEconomy().addShards(player.getUniqueId(), totalEarned, "Ore sell");

        long notorietyGained = totalEarned / 1000;
        if (notorietyGained > 0) {
            plugin.getTierManager().addNotoriety(player,
                    notorietyGained * NotorietySource.ORE_SELL_1000.getAmount(),
                    NotorietySource.ORE_SELL_1000);
        }

        player.sendMessage(CC.translate("&8&m                                        "));
        player.sendMessage(CC.translate("  &b&lSALE RECEIPT"));
        breakdown.forEach((ore, val) ->
                player.sendMessage(CC.translate("  &7" + ore + ": &b◆ " + val)));
        player.sendMessage(CC.translate("  &7Total: &b&l◆ " + totalEarned));
        player.sendMessage(CC.translate("&8&m                                        "));
        player.playSound(player.getLocation(), Sound.LEVEL_UP, 0.7f, 1.3f);
    }

    private void applyEnchantEffects(Player player, Material drop, ZoneType zone) {
        var enchantManager = plugin.getEnchantEffectManager();
        if (enchantManager == null) return;
        enchantManager.onOreMined(player, drop, zone);
    }

    private long applyZoneMultiplierCalc(long base, ZoneType zone) {
        return (long)(base * getZoneMultiplier(zone));
    }

    private String extractOreTag(List<String> lore) {
        if (lore == null) return null;
        for (String line : lore) {
            String stripped = CC.strip(line);
            if (stripped.startsWith("||ORE:") && stripped.endsWith("||")) {
                String inner = stripped.substring(2, stripped.length() - 2);
                return inner.substring(4);
            }
        }
        return null;
    }

    private String oreName(Material mat) {
        return switch (mat) {
            case COAL_ORE     -> "Coal";
            case IRON_ORE     -> "Iron";
            case GOLD_ORE     -> "Gold";
            case REDSTONE_ORE -> "Redstone";
            case LAPIS_ORE    -> "Lapis";
            case DIAMOND_ORE  -> "Diamond";
            case EMERALD_ORE  -> "Emerald";
            case LOG, LOG_2   -> "Wood";
            default           -> mat.name();
        };
    }

    private String zoneColor(ZoneType zone) {
        return switch (zone) {
            case CORE  -> "&c";
            case MID   -> "&e";
            default    -> "&7";
        };
    }

    public long getBasePrice(Material drop) {
        return BASE_PRICES.getOrDefault(drop, 10L);
    }

    public double getZoneMultiplier(ZoneType zone) {
        return switch (zone) {
            case MID  -> plugin.getConfig().getDouble("zone-multipliers.mid",  1.8);
            case CORE -> plugin.getConfig().getDouble("zone-multipliers.core", 4.0);
            default   -> 1.0;
        };
    }
}
