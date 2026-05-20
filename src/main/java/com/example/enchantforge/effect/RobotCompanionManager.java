package com.example.enchantforge.effect;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Manages one Iron Golem companion per player. The golem uses its natural AI
 * to fight nearby hostile mobs; a periodic task nudges it back toward the
 * player when it strays and teleports it if too far away.
 */
public final class RobotCompanionManager {

    private static final TextColor NAME_COLOR      = TextColor.color(0x00CCFF);
    private static final String    COMPANION_NAME  = "R.O.B.O.T.";
    private static final double    FOLLOW_DIST     = 5.0;
    private static final double    TELEPORT_DIST   = 16.0;
    private static final double    RESPAWN_DELAY   = 200L; // ticks (~10 s)
    private static final double    NUDGE_SPEED     = 0.35;

    private static Plugin plugin;

    /** Player UUID → companion golem UUID */
    private static final Map<UUID, UUID> companions = new HashMap<>();
    /** Player UUID → pending respawn task */
    private static final Map<UUID, BukkitTask> respawnTasks = new HashMap<>();
    private static BukkitTask tickTask;

    private RobotCompanionManager() {}

    public static void init(Plugin p) {
        plugin = p;
        tickTask = p.getServer().getScheduler().runTaskTimer(p, RobotCompanionManager::tick, 2L, 2L);
    }

    public static void shutdown() {
        if (tickTask != null) tickTask.cancel();
        for (UUID playerId : new java.util.HashSet<>(companions.keySet())) {
            dismissGolem(playerId);
        }
        cancelAllRespawns();
    }

    public static void summon(Player player) {
        UUID id = player.getUniqueId();
        dismissGolem(id); // remove any existing golem first
        IronGolem golem = spawnGolem(player);
        companions.put(id, golem.getUniqueId());
    }

    public static void dismiss(Player player) {
        dismissGolem(player.getUniqueId());
        cancelRespawn(player.getUniqueId());
    }

    // ---- Internals ----

    private static IronGolem spawnGolem(Player player) {
        Location loc = player.getLocation().add(1.5, 0, 1.5);
        IronGolem golem = player.getWorld().spawn(loc, IronGolem.class, g -> {
            g.setPlayerCreated(true);
            g.customName(Component.text(COMPANION_NAME).color(NAME_COLOR));
            g.setCustomNameVisible(true);
            g.setPersistent(false);
            g.setRemoveWhenFarAway(false);
        });
        player.getWorld().playSound(loc, Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 1.4f);
        player.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, loc.add(0, 1, 0), 24, 0.4, 0.6, 0.4, 0.1);
        return golem;
    }

    private static void dismissGolem(UUID playerId) {
        UUID golemId = companions.remove(playerId);
        if (golemId == null) return;
        plugin.getServer().getWorlds().stream()
                .flatMap(w -> w.getEntitiesByClass(IronGolem.class).stream())
                .filter(g -> g.getUniqueId().equals(golemId))
                .findFirst()
                .ifPresent(g -> {
                    g.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, g.getLocation().add(0, 1, 0), 16, 0.4, 0.6, 0.4, 0.1);
                    g.remove();
                });
    }

    private static void cancelRespawn(UUID playerId) {
        BukkitTask t = respawnTasks.remove(playerId);
        if (t != null) t.cancel();
    }

    private static void cancelAllRespawns() {
        respawnTasks.values().forEach(BukkitTask::cancel);
        respawnTasks.clear();
    }

    private static void tick() {
        for (var entry : new java.util.HashMap<>(companions).entrySet()) {
            UUID playerId = entry.getKey();
            UUID golemId  = entry.getValue();

            Player player = plugin.getServer().getPlayer(playerId);
            if (player == null || !player.isOnline()) continue;

            IronGolem golem = findGolem(golemId);
            if (golem == null || !golem.isValid()) {
                // Golem died — schedule respawn
                companions.remove(playerId);
                if (!respawnTasks.containsKey(playerId)) {
                    BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                        respawnTasks.remove(playerId);
                        Player p2 = plugin.getServer().getPlayer(playerId);
                        if (p2 != null && p2.isOnline() && !companions.containsKey(playerId)) {
                            IronGolem newGolem = spawnGolem(p2);
                            companions.put(playerId, newGolem.getUniqueId());
                            p2.sendActionBar(Component.text("[ R.O.B.O.T. ] Unit online.").color(NAME_COLOR));
                        }
                    }, (long) RESPAWN_DELAY);
                    respawnTasks.put(playerId, task);
                }
                continue;
            }

            // Same world check
            if (!golem.getWorld().equals(player.getWorld())) {
                golem.teleport(player.getLocation());
                continue;
            }

            double dist = golem.getLocation().distance(player.getLocation());

            // Teleport if too far
            if (dist > TELEPORT_DIST) {
                Location behind = player.getLocation().clone().subtract(
                        player.getLocation().getDirection().multiply(2));
                behind.setY(player.getLocation().getY());
                golem.teleport(behind);
                continue;
            }

            // Nudge toward player when not in combat and straying
            if (dist > FOLLOW_DIST && ((Mob) golem).getTarget() == null) {
                org.bukkit.util.Vector dir = player.getLocation().toVector()
                        .subtract(golem.getLocation().toVector())
                        .setY(0).normalize().multiply(NUDGE_SPEED);
                dir.setY(golem.getVelocity().getY());
                golem.setVelocity(dir);
            }
        }
    }

    private static IronGolem findGolem(UUID golemId) {
        return plugin.getServer().getWorlds().stream()
                .flatMap(w -> w.getEntitiesByClass(IronGolem.class).stream())
                .filter(g -> g.getUniqueId().equals(golemId))
                .findFirst().orElse(null);
    }
}
