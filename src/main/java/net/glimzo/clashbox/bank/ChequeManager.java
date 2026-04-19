package net.glimzo.clashbox.bank;

import me.pikashrey.glimzocore.utilities.chat.CC;
import net.glimzo.clashbox.core.ClashBoxPlugin;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class ChequeManager {

    // tag format stored in lore: ||CHEQUE:<amount>:<issuerUUID>:<chequeId>||
    private static final String TAG_PREFIX = "||CHEQUE:";
    private static final String TAG_SUFFIX = "||";

    private final ClashBoxPlugin plugin;
    private final ShardEconomy   shards;
    private final SavingsManager savings;

    public ChequeManager(ClashBoxPlugin plugin, ShardEconomy shards, SavingsManager savings) {
        this.plugin  = plugin;
        this.shards  = shards;
        this.savings = savings;
    }

    public ItemStack createCheque(UUID issuer, long amount) {
        String issuerName = resolvePlayerName(issuer);
        String chequeId   = UUID.randomUUID().toString().substring(0, 8);

        ItemStack item = new ItemStack(Material.PAPER, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.setDisplayName(CC.translate("&f&lCheque &8- &b" + shards.formatShort(amount)));
        List<String> lore = new ArrayList<>(Arrays.asList(
            CC.translate("&8--------------------"),
            CC.translate("&7Amount: " + shards.format(amount)),
            CC.translate("&7Issued by: &f" + issuerName),
            CC.translate(""),
            CC.translate("&7Hand this to anyone - they can"),
            CC.translate("&7deposit it using &e/deposit&7."),
            CC.translate("&8--------------------"),
            CC.translate("&8" + TAG_PREFIX + amount + ":" + issuer + ":" + chequeId + TAG_SUFFIX)
        ));
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isCheque(ItemStack item) {
        if (item == null || item.getType() != Material.PAPER) return false;
        if (!item.hasItemMeta() || !item.getItemMeta().hasLore()) return false;
        return extractTag(item.getItemMeta().getLore()) != null;
    }

    public long getChequeAmount(ItemStack item) {
        if (item == null || !item.hasItemMeta() || !item.getItemMeta().hasLore()) return -1;
        String tag = extractTag(item.getItemMeta().getLore());
        if (tag == null) return -1;
        String[] parts = tag.split(":");
        if (parts.length < 3) return -1;
        try { return Long.parseLong(parts[0]); }
        catch (NumberFormatException e) { return -1; }
    }

    public boolean writeCheque(Player player, long amount) {
        if (amount <= 0) return false;
        if (!shards.has(player.getUniqueId(), amount)) return false;

        shards.removeShards(player.getUniqueId(), amount, "cheque_written");
        ItemStack cheque = createCheque(player.getUniqueId(), amount);
        player.getInventory().addItem(cheque);
        return true;
    }

    public String depositCheque(Player player, String account) {
        ItemStack held = player.getInventory().getItemInHand();

        if (!isCheque(held)) {
            return CC.translate("&cYou must be holding a cheque (special paper) in your hand.");
        }

        long amount = getChequeAmount(held);
        if (amount <= 0) {
            return CC.translate("&cThis cheque has no value.");
        }

        boolean toSavings = account.equalsIgnoreCase("savings") || account.equalsIgnoreCase("save");

        if (toSavings) {
            shards.addShards(player.getUniqueId(), amount, "cheque_deposit_transit");
            var result = savings.deposit(player.getUniqueId(), amount);
            if (!result.isSuccess()) {
                shards.removeShards(player.getUniqueId(), amount, "cheque_deposit_rollback");
                return CC.translate(result.getMessage());
            }
        } else {
            shards.addShards(player.getUniqueId(), amount, "cheque_deposit_wallet");
        }

        if (held.getAmount() > 1) {
            held.setAmount(held.getAmount() - 1);
        } else {
            player.getInventory().setItemInHand(new ItemStack(Material.AIR));
        }

        String dest = toSavings ? "&asavings" : "&bwallet";
        return CC.translate("&aCheque deposited! " + shards.format(amount) +
                " &aadded to your " + dest + "&a.");
    }

    public void adminIssueCheque(Player target, long amount, UUID adminUuid) {
        ItemStack cheque = createCheque(adminUuid, amount);
        target.getInventory().addItem(cheque);
    }

    private String extractTag(List<String> lore) {
        if (lore == null) return null;
        for (String line : lore) {
            String stripped = me.pikashrey.glimzocore.utilities.chat.CC.strip(line);
            if (stripped.startsWith("||CHEQUE:") && stripped.endsWith("||")) {
                return stripped.substring(TAG_PREFIX.length(),
                        stripped.length() - TAG_SUFFIX.length());
            }
        }
        return null;
    }

    private String resolvePlayerName(UUID uuid) {
        var player = plugin.getServer().getPlayer(uuid);
        if (player != null) return player.getName();
        var offline = plugin.getServer().getOfflinePlayer(uuid);
        String name = offline.getName();
        return name != null ? name : uuid.toString().substring(0, 8);
    }
}
