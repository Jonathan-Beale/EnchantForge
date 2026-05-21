package com.example.enchantforge.effect;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Effect style: {@code resource_pool_modifier}
 *
 * Increases a named resource pool's effective max and/or regen rate while the enchant is
 * equipped. Bonuses scale linearly with enchant level. Removed cleanly on unequip.
 *
 * YAML example:
 * <pre>
 * effect:
 *   style: resource_pool_modifier
 *   pool: energy
 *   maxBonusPerLevel: 500.0     # extra pool capacity per enchant level
 *   regenBonusPerLevel: 2.0     # extra regen per tick per enchant level
 * </pre>
 *
 * Multiple enchants targeting the same pool stack additively.
 */
public final class ResourcePoolModifierEffect implements EnchantEffect {

    private final String poolName;
    private final double maxBonusPerLevel;
    private final double regenBonusPerLevel;

    /** Tracks the last-applied bonus per player so remove() can undo the exact amount. */
    private final Map<UUID, double[]> applied = new ConcurrentHashMap<>();

    public ResourcePoolModifierEffect(String poolName, double maxBonusPerLevel, double regenBonusPerLevel) {
        this.poolName = poolName;
        this.maxBonusPerLevel = maxBonusPerLevel;
        this.regenBonusPerLevel = regenBonusPerLevel;
    }

    public static ResourcePoolModifierEffect fromYaml(ConfigurationSection section) {
        return new ResourcePoolModifierEffect(
                section.getString("pool", "energy"),
                section.getDouble("maxBonusPerLevel", 0.0),
                section.getDouble("regenBonusPerLevel", 0.0));
    }

    @Override
    public void apply(Player player, int level, int durationTicks) {
        double maxBonus   = maxBonusPerLevel   * level;
        double regenBonus = regenBonusPerLevel * level;
        PlayerResourcePool pool = ResourcePoolRegistry.get(poolName);
        pool.addModifier(player.getUniqueId(), maxBonus, regenBonus);
        applied.put(player.getUniqueId(), new double[]{maxBonus, regenBonus});
    }

    @Override
    public void remove(Player player) {
        double[] bonus = applied.remove(player.getUniqueId());
        if (bonus == null) return;
        ResourcePoolRegistry.get(poolName).removeModifier(player.getUniqueId(), bonus[0], bonus[1]);
    }

    @Override
    public String id() { return "resource_pool_modifier"; }
}
