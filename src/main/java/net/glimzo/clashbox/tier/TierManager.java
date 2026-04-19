package net.glimzo.clashbox.tier;

import me.pikashrey.glimzocore.utilities.chat.CC;
import net.glimzo.clashbox.bank.UnlockableFeature;
import net.glimzo.clashbox.core.ClashBoxPlugin;
import net.glimzo.clashbox.player.ClashBoxProfile;
import org.bukkit.entity.Player;

import java.util.UUID;

public class TierManager {

    private final ClashBoxPlugin plugin;

    public TierManager(ClashBoxPlugin plugin) {
        this.plugin = plugin;
    }

    public TierLevel getTier(UUID uuid) {
        ClashBoxProfile p = plugin.getProfileManager().getProfile(uuid);
        return p == null ? TierLevel.TIER_1 : TierLevel.forNotoriety(p.getNotoriety());
    }

    public TierLevel getTier(Player player) {
        return getTier(player.getUniqueId());
    }

    public long getNotoriety(UUID uuid) {
        ClashBoxProfile p = plugin.getProfileManager().getProfile(uuid);
        return p == null ? 0 : p.getNotoriety();
    }

    public void addNotoriety(Player player, long amount, NotorietySource source) {
        if (amount <= 0) return;
        ClashBoxProfile profile = plugin.getProfileManager().getProfile(player);
        if (profile == null) return;

        TierLevel before = TierLevel.forNotoriety(profile.getNotoriety());
        profile.addNotoriety(amount);
        TierLevel after = TierLevel.forNotoriety(profile.getNotoriety());

        if (after.getNumber() > before.getNumber()) {
            handlePromotion(player, profile, before, after);
        }
    }

    public boolean hasUnlocked(Player player, UnlockableFeature feature) {
        return hasUnlocked(player.getUniqueId(), feature);
    }

    public boolean hasUnlocked(UUID uuid, UnlockableFeature feature) {
        TierLevel tier = getTier(uuid);
        for (TierLevel t : TierLevel.values()) {
            if (t.getUnlocks().contains(feature)) {
                return tier.getNumber() >= t.getNumber();
            }
        }
        return true;
    }

    public TierLevel getRequiredTier(UnlockableFeature feature) {
        for (TierLevel t : TierLevel.values()) {
            if (t.getUnlocks().contains(feature)) return t;
        }
        return TierLevel.TIER_1;
    }

    private void handlePromotion(Player player, ClashBoxProfile profile,
                                  TierLevel before, TierLevel after) {
        profile.setTierNumber(after.getNumber());
        profile.markDirty();

        plugin.getTitleService().sendTitle(player,
                after.getColor() + "&lTIER " + after.getRoman(),
                "&7You have advanced to " + after.display(),
                10, 80, 20);

        player.playSound(player.getLocation(), org.bukkit.Sound.LEVEL_UP, 1.0f, 1.2f);

        String broadcast = CC.translate(
                "&8[&bClashBox&8] " + after.getColor() + "&l" + player.getName() +
                " &r&7advanced to " + after.display() + "&7!");
        plugin.getServer().getOnlinePlayers().forEach(p -> p.sendMessage(broadcast));

        if (!after.getUnlocks().isEmpty()) {
            after.getUnlocks().forEach(f ->
                    player.sendMessage(CC.translate(
                            "&8[&bClashBox&8] &aUnlocked: &f" + formatFeature(f))));
        }
    }

    private String formatFeature(UnlockableFeature f) {
        return switch (f) {
            case SAVINGS_BANK      -> "Savings Bank";
            case INVESTMENT_BASIC  -> "Investment Vault (2h/8h)";
            case INVESTMENT_FULL   -> "Investment Vault (all tiers)";
            case LOAN_BASIC        -> "Loans (basic)";
            case LOAN_FULL         -> "Full Loan System";
            case ENCHANT_BASIC     -> "Enchanting (basic enchants)";
            case ENCHANT_ADVANCED  -> "Enchanting (advanced enchants)";
            default                -> f.name();
        };
    }
}
