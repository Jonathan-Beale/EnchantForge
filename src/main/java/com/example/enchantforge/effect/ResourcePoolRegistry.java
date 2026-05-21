package com.example.enchantforge.effect;

import org.bukkit.configuration.ConfigurationSection;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Registry of named resource pools (energy, mana, etc.) loaded from config.yml.
 *
 * Config format:
 * <pre>
 * resource-pools:
 *   energy:
 *     max: 5000
 *     regenPerTick: 10
 * </pre>
 *
 * Effects reference a pool by name via {@code resourcePool: energy} in their YAML.
 * If no {@code resource-pools} section is present, a default "energy" pool is created.
 */
public final class ResourcePoolRegistry {

    private static final Map<String, PlayerResourcePool> POOLS = new LinkedHashMap<>();

    private ResourcePoolRegistry() {}

    public static void init(ConfigurationSection section) {
        POOLS.clear();
        if (section == null) {
            POOLS.put("energy", new PlayerResourcePool(5000.0, 10.0));
            return;
        }
        for (String name : section.getKeys(false)) {
            ConfigurationSection cfg = section.getConfigurationSection(name);
            if (cfg == null) continue;
            double max   = cfg.getDouble("max", 1000.0);
            double regen = cfg.getDouble("regenPerTick", 1.0);
            POOLS.put(name, new PlayerResourcePool(max, regen));
        }
        if (POOLS.isEmpty()) {
            POOLS.put("energy", new PlayerResourcePool(5000.0, 10.0));
        }
    }

    /**
     * Returns the named pool. Falls back to "energy" if the name is null/blank.
     * Throws if the resolved name is not registered.
     */
    public static PlayerResourcePool get(String name) {
        String key = (name == null || name.isBlank()) ? "energy" : name;
        PlayerResourcePool pool = POOLS.get(key);
        if (pool == null) throw new IllegalArgumentException("Unknown resource pool: " + key);
        return pool;
    }

    public static Collection<PlayerResourcePool> all() {
        return Collections.unmodifiableCollection(POOLS.values());
    }

    public static void cleanupPlayer(UUID id) {
        POOLS.values().forEach(p -> p.cleanup(id));
    }
}
