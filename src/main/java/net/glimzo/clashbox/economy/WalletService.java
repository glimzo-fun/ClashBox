package net.glimzo.clashbox.economy;

import me.pikashrey.glimzocore.GlimzoCore;
import net.glimzo.clashbox.core.ClashBoxPlugin;

import java.util.UUID;

// thin wrapper - wallet IS GlimzoCore coins
public class WalletService {

    public WalletService(ClashBoxPlugin plugin) {}

    public long getBalance(UUID uuid) {
        return GlimzoCore.getInstance().getCoinManager().getCoins(uuid);
    }
}
