package com.example.enchantforge.effect;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Shared energy pool used by Thruster and Repulsor enchants.
 * Regenerates passively; visual display is handled by SuitListener (BossBar).
 */
public final class EnergyManager {
    private EnergyManager() {}

    public static final double MAX = 100.0;
    // 0.4/tick × 20 ticks/sec = 8/sec → full recharge from 0 in ~12.5 seconds
    private static final double REGEN_PER_TICK = 0.4;

    private static final Map<UUID, Double> energy = new ConcurrentHashMap<>();
    private static Consumer<Player> onEnergyChanged = p -> {};

    public static void init(Plugin plugin) {
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Map.Entry<UUID, Double> e : energy.entrySet()) {
                if (e.getValue() < MAX) e.setValue(Math.min(MAX, e.getValue() + REGEN_PER_TICK));
            }
        }, 1L, 1L);
    }

    /** Register a callback invoked whenever a player's energy is consumed. */
    public static void registerCallback(Consumer<Player> callback) {
        onEnergyChanged = callback;
    }

    /** Returns true and deducts energy on success. */
    public static boolean tryConsume(Player player, double cost) {
        double e = energy.getOrDefault(player.getUniqueId(), MAX);
        if (e < cost) return false;
        energy.put(player.getUniqueId(), e - cost);
        onEnergyChanged.accept(player);
        return true;
    }

    public static double get(Player player) {
        return energy.getOrDefault(player.getUniqueId(), MAX);
    }

    public static void cleanup(UUID id) {
        energy.remove(id);
    }
}
