package net.glimzo.clashbox.events;

import net.glimzo.clashbox.core.ClashBoxPlugin;
import net.glimzo.clashbox.economy.EconomyHook;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import net.glimzo.clashbox.ui.ParticleUtil;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import me.pikashrey.glimzocore.utilities.chat.CC;

public class EventManager {

    private final ClashBoxPlugin plugin;
    private final EconomyHook economyHook;

    private final List<BukkitTask> scheduledTasks = new ArrayList<>();
    private boolean running = false;

    private Location activeSupplyDrop = null;
    private int supplyDropCountdown   = 0;
    private final List<Location> activeVeinBlocks = new ArrayList<>();

    public EventManager(ClashBoxPlugin plugin, EconomyHook economyHook) {
        this.plugin      = plugin;
        this.economyHook = economyHook;
    }

    public void startScheduler() {
        running = true;
        scheduleNextSupplyDrop();
        scheduleNextVeinBurst();
        scheduleNextBankRaid();
    }

    public void shutdown() {
        running = false;
        for (BukkitTask t : scheduledTasks) t.cancel();
        scheduledTasks.clear();
        cleanupVein();
    }

    private boolean hasEnoughPlayers() {
        int required = plugin.getCBConfig().getEventMinPlayers();
        long arenaPlayers = plugin.getServer().getOnlinePlayers().stream()
                .filter(p -> plugin.getPlayerStateManager().isAliveInArena(p))
                .count();
        return arenaPlayers >= required;
    }

    private void scheduleNextSupplyDrop() {
        if (!running) return;
        if (!plugin.getCBConfig().isEventEnabled("supply-drop")) return;

        int minDelay = plugin.getCBConfig().getEventMinDelay("supply-drop");
        int maxDelay = plugin.getCBConfig().getEventMaxDelay("supply-drop");
        int delay    = ThreadLocalRandom.current().nextInt(minDelay, maxDelay + 1);

        BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!hasEnoughPlayers()) {
                scheduleNextSupplyDrop();
                return;
            }
            fireSupplyDropCountdown();
        }, delay * 20L);
        scheduledTasks.add(task);
    }

    private void fireSupplyDropCountdown() {
        Location dropLoc = pickEventLocation(true);
        if (dropLoc == null) { scheduleNextSupplyDrop(); return; }

        broadcastAll(CC.translate("&8━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
        broadcastAll(CC.translate("&6&l  ✦ SUPPLY DROP INCOMING!"));
        broadcastAll(String.format("&7  Landing at &eX:%d Y:%d Z:%d &7in &c60 seconds&7!",
                dropLoc.getBlockX(), dropLoc.getBlockY(), dropLoc.getBlockZ()));
        broadcastAll(CC.translate("&8━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
        broadcastSound(Sound.WITHER_SPAWN, 0.4f, 0.8f);

        activeSupplyDrop    = dropLoc;
        supplyDropCountdown = 60;

        int[] id = {0};
        id[0] = plugin.getServer().getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            supplyDropCountdown--;

            ParticleUtil.flame(dropLoc.clone().add(0.5, supplyDropCountdown * 0.5, 0.5), 5);

            if (supplyDropCountdown <= 10 && supplyDropCountdown > 0) {
                broadcastAll(CC.translate("&6⚠ Supply Drop landing in &c&l" + supplyDropCountdown + "s&6!"));
            }

            if (supplyDropCountdown <= 0) {
                plugin.getServer().getScheduler().cancelTask(id[0]);
                landSupplyDrop(dropLoc);
            }
        }, 20L, 20L);
    }

    private void landSupplyDrop(Location loc) {
        ParticleUtil.explosionHuge(loc);
        ParticleUtil.fireworkSpark(loc.clone().add(0.5, 0, 0.5), 15);
        loc.getWorld().playSound(loc, Sound.EXPLODE, 1.0f, 0.6f);

        broadcastAll(CC.translate("&6&l  ✦ SUPPLY DROP HAS LANDED!"));
        broadcastAll(String.format("&7  Located at &eX:%d Y:%d Z:%d - &cfirst to claim wins!",
                loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()));

        boolean[] claimed = {false};

        int[] taskRef = {0};
        taskRef[0] = plugin.getServer().getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            if (claimed[0]) {
                plugin.getServer().getScheduler().cancelTask(taskRef[0]);
                return;
            }
            double claimRadius = plugin.getConfig().getDouble("events.supply-drop.claim-radius", 3.0);
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                if (!plugin.getPlayerStateManager().isAliveInArena(player)) continue;
                if (player.getLocation().distanceSquared(loc) <= claimRadius * claimRadius) {
                    claimed[0] = true;
                    plugin.getServer().getScheduler().cancelTask(taskRef[0]);
                    claimSupplyDrop(player, loc);
                    return;
                }
            }
            ParticleUtil.endSignal(loc.clone().add(0.5, 0, 0.5), 5);
        }, 5L, 5L);

        int durationSeconds = plugin.getConfig().getInt("events.supply-drop.duration-seconds", 120);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (claimed[0]) return;
            claimed[0] = true;
            plugin.getServer().getScheduler().cancelTask(taskRef[0]);
            if (activeSupplyDrop != null) {
                activeSupplyDrop = null;
                broadcastAll(CC.translate("&7The supply drop at &eX:" + loc.getBlockX() +
                        " Z:" + loc.getBlockZ() + " &7expired unclaimed."));
            }
            scheduleNextSupplyDrop();
        }, durationSeconds * 20L);
    }

    private void claimSupplyDrop(Player claimer, Location loc) {
        activeSupplyDrop = null;

        long reward = ThreadLocalRandom.current().nextLong(2000, 5000);
        plugin.getShardEconomy().addShards(claimer.getUniqueId(), reward, "Claimed supply drop");

        broadcastAll(CC.translate("&6&l  ✦ " + claimer.getName() +
                " &r&eclaimed the supply drop! &a+◆ " + reward + "&7!"));
        broadcastSound(Sound.FIREWORK_BLAST, 0.8f, 1.2f);

        plugin.getTitleService().sendTitle(claimer,
                "&6&lSUPPLY DROP!", "&7+◆ " + reward + " Shards claimed!", 5, 50, 15);
        claimer.playSound(claimer.getLocation(), Sound.LEVEL_UP, 1.0f, 1.3f);

        scheduleNextSupplyDrop();
    }

    private void scheduleNextVeinBurst() {
        if (!running) return;
        if (!plugin.getCBConfig().isEventEnabled("vein-burst")) return;

        int minDelay = plugin.getCBConfig().getEventMinDelay("vein-burst");
        int maxDelay = plugin.getCBConfig().getEventMaxDelay("vein-burst");
        int delay    = ThreadLocalRandom.current().nextInt(minDelay, maxDelay + 1);

        BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!hasEnoughPlayers()) { scheduleNextVeinBurst(); return; }
            fireVeinBurst();
        }, delay * 20L);
        scheduledTasks.add(task);
    }

    private void fireVeinBurst() {
        Location veinCenter = pickEventLocation(false);
        if (veinCenter == null) { scheduleNextVeinBurst(); return; }

        int oreCount = plugin.getConfig().getInt("events.vein-burst.ore-count", 20);

        cleanupVein();
        List<Material> veinOres = Arrays.asList(Material.DIAMOND_ORE, Material.EMERALD_ORE);

        for (int i = 0; i < oreCount; i++) {
            int dx = ThreadLocalRandom.current().nextInt(-4, 5);
            int dy = ThreadLocalRandom.current().nextInt(-2, 3);
            int dz = ThreadLocalRandom.current().nextInt(-4, 5);
            Location oreLoc = veinCenter.clone().add(dx, dy, dz);

            if (oreLoc.getBlock().getType() == Material.STONE) {
                Material ore = veinOres.get(ThreadLocalRandom.current().nextInt(veinOres.size()));
                oreLoc.getBlock().setType(ore);
                activeVeinBlocks.add(oreLoc);
            }
        }

        broadcastAll(CC.translate("&8━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
        broadcastAll(CC.translate("&a&l  ✦ VEIN BURST!"));
        broadcastAll(String.format("&7  Rich ore cluster at &eX:%d Y:%d Z:%d&7!",
                veinCenter.getBlockX(), veinCenter.getBlockY(), veinCenter.getBlockZ()));
        broadcastAll(CC.translate("&7  It will disappear in &c45 seconds&7!"));
        broadcastAll(CC.translate("&8━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
        broadcastSound(Sound.ENDERDRAGON_HIT, 0.5f, 1.4f);

        int duration = plugin.getConfig().getInt("events.vein-burst.duration-seconds", 50);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            cleanupVein();
            scheduleNextVeinBurst();
        }, duration * 20L);
    }

    private void cleanupVein() {
        for (Location loc : activeVeinBlocks) {
            if (loc.getBlock().getType() == Material.DIAMOND_ORE
                    || loc.getBlock().getType() == Material.EMERALD_ORE) {
                loc.getBlock().setType(Material.STONE);
            }
        }
        activeVeinBlocks.clear();
    }

    private void scheduleNextBankRaid() {
        if (!running) return;
        if (!plugin.getCBConfig().isEventEnabled("bank-raid")) return;

        int minDelay = plugin.getCBConfig().getEventMinDelay("bank-raid");
        int maxDelay = plugin.getCBConfig().getEventMaxDelay("bank-raid");
        int delay    = ThreadLocalRandom.current().nextInt(minDelay, maxDelay + 1);

        BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!hasEnoughPlayers()) { scheduleNextBankRaid(); return; }
            fireBankRaid();
        }, delay * 20L);
        scheduledTasks.add(task);
    }

    private void fireBankRaid() {
        broadcastAll(CC.translate("&8━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
        broadcastAll(CC.translate("&c&l  ✦ BANK RAID EVENT!"));
        broadcastAll(CC.translate("&7  Hold the bank zone for &e60 seconds &7to steal"));
        broadcastAll(CC.translate("&7  &c3% &7of the top 5 richest players' wallets!"));
        broadcastAll(CC.translate("&8━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
        broadcastSound(Sound.WITHER_DEATH, 0.5f, 0.7f);

        Map<UUID, Integer> holdTicks = new HashMap<>();
        int requiredTicks = plugin.getConfig()
                .getInt("events.bank-raid.hold-duration-seconds", 60) * 20;

        org.bukkit.World arenaWorld = plugin.getServer().getWorld(plugin.getCBConfig().getWorldName());
        Location bankLoc = arenaWorld != null
                ? new Location(arenaWorld, plugin.getCBConfig().getCenterX(), 64,
                plugin.getCBConfig().getCenterZ())
                : plugin.getCBConfig().getLobbySpawn();

        int[] taskRef = {0};
        taskRef[0] = plugin.getServer().getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            for (Player p : plugin.getServer().getOnlinePlayers()) {
                if (!plugin.getPlayerStateManager().isAliveInArena(p)) {
                    holdTicks.remove(p.getUniqueId());
                    continue;
                }
                if (p.getLocation().distanceSquared(bankLoc) <= 10 * 10) {
                    int ticks = holdTicks.merge(p.getUniqueId(), 1, Integer::sum);
                    if (ticks >= requiredTicks) {
                        plugin.getServer().getScheduler().cancelTask(taskRef[0]);
                        executeBankRaid(p);
                        scheduleNextBankRaid();
                        return;
                    }
                    if (ticks % 40 == 0) {
                        int pct = (int)((ticks * 100.0) / requiredTicks);
                        p.sendMessage(CC.translate("&c[Bank Raid] &7Holding... &e" + pct + "% &7complete."));
                    }
                } else {
                    holdTicks.remove(p.getUniqueId());
                }
            }
        }, 20L, 20L);

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            plugin.getServer().getScheduler().cancelTask(taskRef[0]);
            broadcastAll(CC.translate("&7The bank raid was not captured. Event expired."));
            scheduleNextBankRaid();
        }, 3600L);
    }

    private void executeBankRaid(Player raider) {
        double stealPercent = plugin.getConfig()
                .getDouble("events.bank-raid.wallet-steal-percent", 0.03);
        int topTargets = plugin.getConfig()
                .getInt("events.bank-raid.top-targets", 5);

        List<Player> sorted = new ArrayList<>(plugin.getServer().getOnlinePlayers());
        sorted.sort((a, b) -> Long.compare(
                plugin.getShardEconomy().getBalance(b.getUniqueId()),
                plugin.getShardEconomy().getBalance(a.getUniqueId())));

        long totalStolen = 0;
        int count = 0;
        for (Player target : sorted) {
            if (count >= topTargets) break;
            if (target.equals(raider)) continue;
            long walletVal = plugin.getShardEconomy().getBalance(target.getUniqueId());
            long stolen    = (long)(walletVal * stealPercent);
            if (stolen <= 0) continue;
            plugin.getShardEconomy().removeShards(target.getUniqueId(), stolen, "Bank raid by " + raider.getName());
            target.sendMessage(CC.translate("&c&l[Bank Raid] &r&c" + raider.getName() +
                    " raided the bank and stole &e◆ " + stolen + " &cfrom your wallet!"));
            totalStolen += stolen;
            count++;
        }

        plugin.getShardEconomy().addShards(raider.getUniqueId(), totalStolen, "Successful bank raid");

        broadcastAll(CC.translate("&c&l  ✦ " + raider.getName() + " &r&7successfully raided the bank!"));
        broadcastAll(CC.translate("&7  Total stolen: &c&l◆ " + totalStolen));
        plugin.getTitleService().sendTitle(raider,
                "&c&lBANK RAIDED!", "&7+◆ " + totalStolen + " Shards stolen!", 5, 60, 15);
        raider.playSound(raider.getLocation(), Sound.LEVEL_UP, 1.0f, 0.8f);
    }

    private Location pickEventLocation(boolean preferCore) {
        org.bukkit.World world = plugin.getServer().getWorld(plugin.getCBConfig().getWorldName());
        if (world == null) return null;

        double cx    = plugin.getCBConfig().getCenterX();
        double cz    = plugin.getCBConfig().getCenterZ();
        double range = preferCore ? 20 : 45;
        double x     = cx + ThreadLocalRandom.current().nextDouble(-range, range);
        double z     = cz + ThreadLocalRandom.current().nextDouble(-range, range);
        double y     = preferCore ? 25 : 50;

        return new Location(world, x, y, z);
    }

    private void broadcastAll(String msg) {
        for (Player p : plugin.getServer().getOnlinePlayers()) p.sendMessage(msg);
    }

    private void broadcastSound(Sound sound, float volume, float pitch) {
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            p.playSound(p.getLocation(), sound, volume, pitch);
        }
    }
}
