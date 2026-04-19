package net.glimzo.clashbox.enchant;

import me.pikashrey.glimzocore.utilities.chat.CC;
import net.glimzo.clashbox.core.ClashBoxPlugin;
import net.glimzo.clashbox.player.ClashBoxProfile;
import net.glimzo.clashbox.zone.ZoneType;
import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class EnchantEffectManager implements Listener {

    private final ClashBoxPlugin plugin;

    private final Map<UUID, Integer>   berserkerStacks    = new ConcurrentHashMap<>();
    private final Map<UUID, UUID>      lastHitTarget      = new ConcurrentHashMap<>();
    private final Map<UUID, Long>      adrenalineCooldown = new ConcurrentHashMap<>();
    private final Map<UUID, Long>      phantomCooldown    = new ConcurrentHashMap<>();
    private final Map<UUID, Set<UUID>> sessionKillers     = new ConcurrentHashMap<>();
    private final Map<UUID, Integer>   excavatorStacks    = new ConcurrentHashMap<>();

    public EnchantEffectManager(ClashBoxPlugin plugin) {
        this.plugin = plugin;
    }

    private List<AppliedEnchant> getEnchants(Player p, GearSlot slot) {
        ClashBoxProfile profile = plugin.getProfileManager().getProfile(p);
        if (profile == null) return Collections.emptyList();
        return profile.getEnchants(slot);
    }

    private AppliedEnchant getEnchant(Player p, GearSlot slot, CustomEnchantType type) {
        for (AppliedEnchant e : getEnchants(p, slot)) {
            if (e.getType() == type) return e;
        }
        return null;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        Player attacker = null;
        Player victim   = null;

        if (event.getEntity() instanceof Player p) victim = p;
        if (event.getDamager() instanceof Player p) attacker = p;
        else if (event.getDamager() instanceof Arrow a && a.getShooter() instanceof Player p)
            attacker = p;

        if (victim == null) return;
        if (!plugin.getPlayerStateManager().isAliveInArena(victim)) return;

        if (attacker != null && plugin.getTeamManager().areTeammates(attacker, victim)) {
            event.setCancelled(true);
            return;
        }

        double damage = event.getDamage();

        if (attacker != null) {
            damage = applyAttackerEnchants(attacker, victim, damage);
        }

        damage = applyDefenderEnchants(attacker, victim, damage);

        if (attacker != null) {
            applyAdrenalineOnHit(attacker);
            applyPhantomNearDeath(victim, damage);
            updateBerserker(attacker, victim);
        }

        event.setDamage(damage);
    }

    private double applyAttackerEnchants(Player attacker, Player victim, double damage) {
        ZoneType zone = plugin.getZoneManager().detectZone(attacker.getLocation());

        AppliedEnchant execute = getEnchant(attacker, GearSlot.SWORD, CustomEnchantType.EXECUTE);
        if (execute != null) {
            double hpPercent  = victim.getHealth() / victim.getMaxHealth();
            double threshold  = plugin.getEnchantConfig().getDouble(
                    "custom-enchants.EXECUTE.threshold-hp-percent", 0.30);
            if (hpPercent <= threshold) {
                List<?> bonuses = plugin.getEnchantConfig().getList(
                        "custom-enchants.EXECUTE.bonus-damage-percent");
                double bonus = getFromList(bonuses, execute.getLevel() - 1, 0.20);
                damage *= (1.0 + bonus);
            }
        }

        AppliedEnchant berserker = getEnchant(attacker, GearSlot.SWORD, CustomEnchantType.BERSERKER);
        if (berserker != null) {
            int stacks = berserkerStacks.getOrDefault(attacker.getUniqueId(), 0);
            List<?> perHit = plugin.getEnchantConfig().getList(
                    "custom-enchants.BERSERKER.damage-per-hit-stack");
            double perStack = getFromList(perHit, berserker.getLevel() - 1, 0.05);
            damage *= (1.0 + stacks * perStack);
        }

        AppliedEnchant nemesis = getEnchant(attacker, GearSlot.SWORD, CustomEnchantType.NEMESIS);
        if (nemesis != null) {
            Set<UUID> killers = sessionKillers.getOrDefault(
                    attacker.getUniqueId(), Collections.emptySet());
            if (killers.contains(victim.getUniqueId())) {
                List<?> bonuses = plugin.getEnchantConfig().getList(
                        "custom-enchants.NEMESIS.bonus-damage-percent");
                double bonus = getFromList(bonuses, nemesis.getLevel() - 1, 0.15);
                damage *= (1.0 + bonus);
            }
        }

        AppliedEnchant coreForged = getEnchant(attacker, GearSlot.SWORD, CustomEnchantType.CORE_FORGED);
        if (coreForged != null && zone == ZoneType.CORE) {
            List<?> bonuses = plugin.getEnchantConfig().getList(
                    "custom-enchants.CORE_FORGED.bonus-damage-percent");
            double bonus = getFromList(bonuses, coreForged.getLevel() - 1, 0.12);
            damage *= (1.0 + bonus);
        }

        return damage;
    }

    private double applyDefenderEnchants(Player attacker, Player victim, double damage) {
        ZoneType zone = plugin.getZoneManager().detectZone(victim.getLocation());

        AppliedEnchant rebound = getEnchant(victim, GearSlot.CHESTPLATE, CustomEnchantType.REBOUND);
        if (rebound != null) {
            List<?> percents = plugin.getEnchantConfig().getList(
                    "custom-enchants.REBOUND.reflect-percent");
            double reflect   = getFromList(percents, rebound.getLevel() - 1, 0.10);
            double reflected = damage * reflect;
            final Player reboundTarget = attacker;
            // only reflect to arena players, skip negligible amounts to avoid spam
            if (reboundTarget != null
                    && plugin.getPlayerStateManager().isAliveInArena(reboundTarget)
                    && reflected >= 0.5) {
                plugin.getServer().getScheduler().runTask(plugin, () ->
                        reboundTarget.damage(reflected));
            }
        }

        AppliedEnchant lastStand = getEnchant(victim, GearSlot.CHESTPLATE, CustomEnchantType.LAST_STAND);
        if (lastStand == null)
            lastStand = getEnchant(victim, GearSlot.LEGGINGS, CustomEnchantType.LAST_STAND);
        if (lastStand != null) {
            double hpPercent = victim.getHealth() / victim.getMaxHealth();
            double threshold = plugin.getEnchantConfig().getDouble(
                    "custom-enchants.LAST_STAND.hp-threshold-percent", 0.20);
            if (hpPercent <= threshold) {
                List<?> amps = plugin.getEnchantConfig().getList(
                        "custom-enchants.LAST_STAND.resistance-amplifier");
                int amp = (int) getFromList(amps, lastStand.getLevel() - 1, 0.0);
                victim.addPotionEffect(new PotionEffect(
                        PotionEffectType.DAMAGE_RESISTANCE, 40, amp, false, false));
            }
        }

        AppliedEnchant outerShell = getEnchant(victim, GearSlot.HELMET, CustomEnchantType.OUTER_SHELL);
        if (outerShell != null && zone == ZoneType.OUTER) {
            List<?> reductions = plugin.getEnchantConfig().getList(
                    "custom-enchants.OUTER_SHELL.damage-reduction-percent");
            double reduction = getFromList(reductions, outerShell.getLevel() - 1, 0.10);
            damage *= (1.0 - reduction);
        }

        return damage;
    }

    private void applyAdrenalineOnHit(Player player) {
        AppliedEnchant adrenaline = getEnchant(player, GearSlot.BOOTS, CustomEnchantType.ADRENALINE);
        if (adrenaline == null) return;

        long now      = System.currentTimeMillis();
        long lastProc = adrenalineCooldown.getOrDefault(player.getUniqueId(), 0L);
        List<?> cooldowns = plugin.getEnchantConfig().getList(
                "custom-enchants.ADRENALINE.cooldown-ticks");
        int cooldownTicks = (int) getFromList(cooldowns, adrenaline.getLevel() - 1, 200.0);
        if ((now - lastProc) < (cooldownTicks * 50L)) return;

        adrenalineCooldown.put(player.getUniqueId(), now);

        List<?> amps = plugin.getEnchantConfig().getList("custom-enchants.ADRENALINE.speed-amplifier");
        List<?> durs = plugin.getEnchantConfig().getList("custom-enchants.ADRENALINE.duration-ticks");
        int amp = (int) getFromList(amps, adrenaline.getLevel() - 1, 0.0);
        int dur = (int) getFromList(durs, adrenaline.getLevel() - 1, 40.0);

        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, dur, amp, false, false));
    }

    private void applyPhantomNearDeath(Player player, double incomingDamage) {
        if (player.getHealth() - incomingDamage > 2.0) return;

        AppliedEnchant phantom = getEnchant(player, GearSlot.HELMET, CustomEnchantType.PHANTOM);
        if (phantom == null) return;

        long now      = System.currentTimeMillis();
        long lastProc = phantomCooldown.getOrDefault(player.getUniqueId(), 0L);
        List<?> cooldowns = plugin.getEnchantConfig().getList("custom-enchants.PHANTOM.cooldown-seconds");
        double cooldownSecs = getFromList(cooldowns, phantom.getLevel() - 1, 120.0);
        if ((now - lastProc) < (cooldownSecs * 1000L)) return;

        phantomCooldown.put(player.getUniqueId(), now);

        List<?> durations = plugin.getEnchantConfig().getList("custom-enchants.PHANTOM.invisible-ticks");
        int dur = (int) getFromList(durations, phantom.getLevel() - 1, 20.0);

        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, dur, 0, false, false));
        player.playSound(player.getLocation(), Sound.ENDERMAN_TELEPORT, 0.5f, 1.5f);
    }

    private void updateBerserker(Player attacker, Player victim) {
        UUID aUUID     = attacker.getUniqueId();
        UUID lastTarget = lastHitTarget.get(aUUID);

        if (victim.getUniqueId().equals(lastTarget)) {
            AppliedEnchant berserkerEnchant = getEnchant(attacker, GearSlot.SWORD, CustomEnchantType.BERSERKER);
            List<?> maxStacksList = plugin.getEnchantConfig().getList("custom-enchants.BERSERKER.max-stacks");
            int max = berserkerEnchant != null
                    ? (int) getFromList(maxStacksList, berserkerEnchant.getLevel() - 1, 8.0)
                    : 8;
            berserkerStacks.merge(aUUID, 1, (a, b) -> Math.min(a + b, max));
        } else {
            berserkerStacks.put(aUUID, 1);
            lastHitTarget.put(aUUID, victim.getUniqueId());
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();

        resetExcavatorOnDeath(victim.getUniqueId());

        if (killer == null) return;
        if (!plugin.getPlayerStateManager().isAliveInArena(killer)) return;

        sessionKillers.computeIfAbsent(victim.getUniqueId(), k -> new HashSet<>())
                .add(killer.getUniqueId());

        AppliedEnchant bloodthirst = getEnchant(killer, GearSlot.SWORD, CustomEnchantType.BLOODTHIRST);
        if (bloodthirst != null) {
            List<?> heals = plugin.getEnchantConfig().getList("custom-enchants.BLOODTHIRST.heal-percent");
            double healPct = getFromList(heals, bloodthirst.getLevel() - 1, 0.06);
            double heal    = victim.getMaxHealth() * healPct;
            double newHp   = Math.min(killer.getMaxHealth(), killer.getHealth() + heal);
            killer.setHealth(newHp);
            killer.getWorld().playEffect(killer.getLocation(), Effect.MOBSPAWNER_FLAMES, 0);
        }

        berserkerStacks.remove(killer.getUniqueId());
        lastHitTarget.remove(killer.getUniqueId());
    }

    public void onOreMined(Player player, Material drop, ZoneType zone) {
        AppliedEnchant fortune = getEnchant(player, GearSlot.PICKAXE, CustomEnchantType.FORTUNE);
        if (fortune != null) {
            List<?> chances = plugin.getEnchantConfig().getList("custom-enchants.FORTUNE.double-chance");
            double chance   = getFromList(chances, fortune.getLevel() - 1, 0.20);
            if (Math.random() < chance) {
                long bonus = plugin.getSellManager().getBasePrice(drop);
                bonus = (long)(bonus * plugin.getSellManager().getZoneMultiplier(zone));
                plugin.getShardEconomy().addShards(player.getUniqueId(), bonus, "Fortune enchant");
                player.sendMessage(CC.translate("&d✦ Fortune! &7+" +
                        plugin.getShardEconomy().formatShort(bonus) + " &7bonus."));
            }
        }

        AppliedEnchant excavator = getEnchant(player, GearSlot.PICKAXE, CustomEnchantType.EXCAVATOR);
        if (excavator != null) {
            List<?> perOre = plugin.getEnchantConfig().getList(
                    "custom-enchants.EXCAVATOR.value-increase-per-ore");
            double increase = getFromList(perOre, excavator.getLevel() - 1, 0.02);
            int stacks      = excavatorStacks.merge(player.getUniqueId(), 1, Integer::sum);
            double capMult  = plugin.getEnchantConfig().getDouble(
                    "custom-enchants.EXCAVATOR.cap-multiplier", 2.0);
            int maxStacks   = (int)((capMult - 1.0) / increase);
            if (stacks > maxStacks) excavatorStacks.put(player.getUniqueId(), maxStacks);
        }
    }

    public double getExcavatorMultiplier(Player player) {
        AppliedEnchant excavator = getEnchant(player, GearSlot.PICKAXE, CustomEnchantType.EXCAVATOR);
        if (excavator == null) return 1.0;
        List<?> perOre = plugin.getEnchantConfig().getList(
                "custom-enchants.EXCAVATOR.value-increase-per-ore");
        double increase = getFromList(perOre, excavator.getLevel() - 1, 0.02);
        int stacks      = excavatorStacks.getOrDefault(player.getUniqueId(), 0);
        return 1.0 + (stacks * increase);
    }

    public void resetExcavatorOnDeath(UUID uuid) {
        excavatorStacks.remove(uuid);
        berserkerStacks.remove(uuid);
        lastHitTarget.remove(uuid);
    }

    public void startVeinSenseTask() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                if (!plugin.getPlayerStateManager().isAliveInArena(player)) continue;
                AppliedEnchant veinSense = getEnchant(player, GearSlot.PICKAXE,
                        CustomEnchantType.VEIN_SENSE);
                if (veinSense == null) continue;

                List<?> radii  = plugin.getEnchantConfig().getList("custom-enchants.VEIN_SENSE.scan-radius");
                int radius     = (int) getFromList(radii, veinSense.getLevel() - 1, 12.0);

                var oreManager = plugin.getOreRegenManager();
                Location nearest  = null;
                double nearestDistSq = Double.MAX_VALUE;

                for (var node : oreManager.getNodes()) {
                    if (node.isMined()) continue;
                    Location nodeLoc = node.getLocation();
                    if (!nodeLoc.getWorld().equals(player.getWorld())) continue;
                    double distSq = nodeLoc.distanceSquared(player.getLocation());
                    if (distSq <= radius * radius && distSq < nearestDistSq) {
                        nearestDistSq = distSq;
                        nearest = nodeLoc;
                    }
                }

                if (nearest != null) {
                    Location direction = nearest.clone().add(0.5, 0.5, 0.5);
                    player.getWorld().playEffect(direction, Effect.HAPPY_VILLAGER, 0);
                }
            }
        }, 20L, 20L);
    }

    public void onPlayerQuit(UUID uuid) {
        berserkerStacks.remove(uuid);
        lastHitTarget.remove(uuid);
        adrenalineCooldown.remove(uuid);
        phantomCooldown.remove(uuid);
        sessionKillers.remove(uuid);
        excavatorStacks.remove(uuid);
    }

    private double getFromList(List<?> list, int index, double def) {
        if (list == null || index >= list.size()) return def;
        Object val = list.get(index);
        if (val instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(val.toString()); }
        catch (Exception e) { return def; }
    }
}
