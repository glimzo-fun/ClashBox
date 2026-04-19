package net.glimzo.clashbox.enchant;

import me.pikashrey.glimzocore.utilities.chat.CC;
import me.pikashrey.glimzocore.utilities.item.ItemBuilder;
import net.glimzo.clashbox.bank.UnlockableFeature;
import net.glimzo.clashbox.core.ClashBoxPlugin;
import net.glimzo.clashbox.player.ClashBoxProfile;
import net.glimzo.clashbox.tier.TierLevel;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class EnchantManager implements Listener {

    private final ClashBoxPlugin plugin;

    private static final int MAX_CUSTOM_ENCHANTS_SWORD   = 3;
    private static final int MAX_CUSTOM_ENCHANTS_PICKAXE = 2;
    private static final int MAX_CUSTOM_ENCHANTS_ARMOUR  = 1;

    public EnchantManager(ClashBoxPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onRightClickWithBook(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack inHand = player.getItemInHand();
        if (inHand == null || inHand.getType() == Material.AIR) return;
        if (!isEnchantBook(inHand)) return;

        event.setCancelled(true);

        EnchantBookData book = parseBook(inHand);
        if (book == null) return;

        ItemStack target = getTargetGear(player, book.slot());
        if (target == null) {
            player.sendMessage(CC.translate("&cEquip the target " + book.slot().name().toLowerCase() +
                    " to apply this enchant."));
            return;
        }

        applyBookToGear(player, book, target, inHand);
    }

    private void applyBookToGear(Player player, EnchantBookData book,
                                  ItemStack gear, ItemStack bookItem) {

        UnlockableFeature feature = book.tierRequired() >= 5
                ? UnlockableFeature.ENCHANT_ADVANCED
                : UnlockableFeature.ENCHANT_BASIC;

        if (!plugin.getTierManager().hasUnlocked(player, feature)) {
            TierLevel required = plugin.getTierManager().getRequiredTier(feature);
            player.sendMessage(CC.translate("&cReach " + required.display() +
                    " &cto use advanced enchants."));
            return;
        }

        ClashBoxProfile profile = plugin.getProfileManager().getProfile(player);
        if (profile == null) return;

        List<AppliedEnchant> existing = profile.getEnchants(book.slot());

        int max = switch (book.slot()) {
            case SWORD   -> MAX_CUSTOM_ENCHANTS_SWORD;
            case PICKAXE -> MAX_CUSTOM_ENCHANTS_PICKAXE;
            default      -> MAX_CUSTOM_ENCHANTS_ARMOUR;
        };

        AppliedEnchant current = null;
        for (AppliedEnchant e : existing) {
            if (e.getType() == book.type()) { current = e; break; }
        }

        if (current == null && existing.size() >= max) {
            player.sendMessage(CC.translate("&cMax " + max + " custom enchants on this item."));
            return;
        }

        int maxLevel = plugin.getEnchantConfig().getInt(
                "custom-enchants." + book.type().name() + ".max-level", 3);

        if (current != null && current.getLevel() >= maxLevel) {
            player.sendMessage(CC.translate("&c" + book.type().getDisplayName() +
                    " is already at max level (" + maxLevel + ")."));
            return;
        }

        int newLevel = current == null ? book.level() : current.getLevel() + 1;

        if (current != null) existing.remove(current);
        existing.add(new AppliedEnchant(book.type(), newLevel, book.slot()));
        profile.setEnchants(book.slot(), existing);
        profile.markDirty();

        updateItemLore(gear, profile, book.slot());

        if (bookItem.getAmount() > 1) {
            bookItem.setAmount(bookItem.getAmount() - 1);
        } else {
            player.setItemInHand(new ItemStack(Material.AIR));
        }

        player.sendMessage(CC.translate("&a✦ Applied " + book.type().getDisplayName() +
                " " + toRoman(newLevel) + " &7to your " + book.slot().name().toLowerCase() + "!"));
        player.playSound(player.getLocation(), Sound.ANVIL_USE, 0.8f, 1.2f);
        player.getWorld().playEffect(player.getLocation(), org.bukkit.Effect.MAGIC_CRIT, 0);
    }

    public ItemStack buildEnchantBook(CustomEnchantType type, int level) {
        String configPath = "custom-enchants." + type.name();
        var enchantCfg = plugin.getEnchantConfig();

        int tierReq = 1;
        List<?> tierReqs = enchantCfg.getList(configPath + ".tier-required");
        if (tierReqs != null && level - 1 < tierReqs.size()) {
            Object val = tierReqs.get(level - 1);
            if (val instanceof Number n) tierReq = n.intValue();
        }

        String description = enchantCfg.getString(configPath + ".description", "");
        List<?> costList   = enchantCfg.getList(configPath + ".cost");
        long cost = costList != null && level - 1 < costList.size()
                ? ((Number) costList.get(level - 1)).longValue() : 0;

        String applicableTo = type.getApplicableSlots().stream()
                .map(s -> s.name().toLowerCase())
                .reduce((a, b) -> a + ", " + b).orElse("?");

        ItemStack book = new ItemStack(Material.BOOK, 1);
        ItemMeta  meta = book.getItemMeta();
        if (meta == null) return book;

        meta.setDisplayName(CC.translate("&5✦ " + type.getDisplayName() + " " + toRoman(level)));
        meta.setLore(Arrays.asList(
                CC.translate("&8--------------------"),
                CC.translate("&7" + description),
                CC.translate(""),
                CC.translate("&7Applies to: &f" + applicableTo),
                CC.translate("&7Tier required: &e" + TierLevel.fromNumber(tierReq).display()),
                CC.translate("&7Cost: &b◆ " + cost),
                CC.translate("&8--------------------"),
                CC.translate("&8||ENCHANTBOOK:" + type.name() + ":" + level + ":" +
                        type.getApplicableSlots().get(0).name() + ":" + tierReq + "||")
        ));

        meta.addEnchant(org.bukkit.enchantments.Enchantment.DURABILITY, 1, true);
        meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
        book.setItemMeta(meta);
        return book;
    }

    private void updateItemLore(ItemStack item, ClashBoxProfile profile, GearSlot slot) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        lore.removeIf(line -> CC.strip(line).startsWith("✦ ") || CC.strip(line).startsWith("⚡ "));

        for (AppliedEnchant enchant : profile.getEnchants(slot)) {
            lore.add(CC.translate("&5✦ " + enchant.getType().getDisplayName() +
                    " " + toRoman(enchant.getLevel())));
        }

        meta.setLore(lore);
        item.setItemMeta(meta);
    }

    public boolean isEnchantBook(ItemStack item) {
        if (item == null || !item.hasItemMeta() || !item.getItemMeta().hasLore()) return false;
        return item.getItemMeta().getLore().stream()
                .anyMatch(line -> CC.strip(line).contains("||ENCHANTBOOK:"));
    }

    public EnchantBookData parseBook(ItemStack item) {
        if (!isEnchantBook(item)) return null;
        for (String line : item.getItemMeta().getLore()) {
            String stripped = CC.strip(line);
            if (!stripped.contains("||ENCHANTBOOK:")) continue;
            String tag   = stripped.replace("||", "").replace("ENCHANTBOOK:", "");
            String[] parts = tag.split(":");
            if (parts.length < 4) return null;
            try {
                CustomEnchantType type   = CustomEnchantType.valueOf(parts[0]);
                int               level  = Integer.parseInt(parts[1]);
                GearSlot          slot   = GearSlot.valueOf(parts[2]);
                int               tierReq = parts.length > 3 ? Integer.parseInt(parts[3]) : 1;
                return new EnchantBookData(type, level, slot, tierReq);
            } catch (Exception e) { return null; }
        }
        return null;
    }

    private ItemStack getTargetGear(Player player, GearSlot slot) {
        return switch (slot) {
            case SWORD, PICKAXE -> player.getItemInHand();
            case HELMET         -> player.getInventory().getHelmet();
            case CHESTPLATE     -> player.getInventory().getChestplate();
            case LEGGINGS       -> player.getInventory().getLeggings();
            case BOOTS          -> player.getInventory().getBoots();
        };
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.getEnchantEffectManager().onPlayerQuit(event.getPlayer().getUniqueId());
    }

    public static String toRoman(int n) {
        return switch (n) {
            case 1 -> "I"; case 2 -> "II"; case 3 -> "III";
            case 4 -> "IV"; case 5 -> "V"; case 6 -> "VI";
            case 7 -> "VII"; case 8 -> "VIII"; default -> String.valueOf(n);
        };
    }

    public record EnchantBookData(CustomEnchantType type, int level,
                                   GearSlot slot, int tierRequired) {}
}
