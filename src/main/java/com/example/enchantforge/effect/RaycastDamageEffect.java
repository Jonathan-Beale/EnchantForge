package com.example.enchantforge.effect;

import com.example.enchantforge.effect.visual.CuePoint;
import com.example.enchantforge.effect.visual.VisualSystem;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

public final class RaycastDamageEffect implements EnchantEffect {

    private final PlayerResourcePool pool;
    private final double range;
    private final double damagePerLevel;
    private final double knockback;
    private final double energyCost;
    private final VisualSystem visuals;

    public RaycastDamageEffect(PlayerResourcePool pool, double range, double damagePerLevel,
                                double knockback, double energyCost, VisualSystem visuals) {
        this.pool = pool;
        this.range = range;
        this.damagePerLevel = damagePerLevel;
        this.knockback = knockback;
        this.energyCost = energyCost;
        this.visuals = visuals;
    }

    @Override
    public String id() { return "raycast_damage"; }

    public static RaycastDamageEffect fromYaml(NamespacedKey key, ConfigurationSection section) {
        return new RaycastDamageEffect(
                ResourcePoolRegistry.get(section.getString("resourcePool", "energy")),
                section.getDouble("range", 20.0),
                section.getDouble("damagePerLevel", 3.0),
                section.getDouble("knockback", 0.5),
                section.getDouble("energyCost", 0.0),
                VisualSystem.fromYaml(section));
    }

    @Override
    public void apply(Player player, int level, int durationTicks) {
        apply(player, level, durationTicks, EnchantEffectContext.NONE);
    }

    @Override
    public void apply(Player player, int level, int durationTicks, EnchantEffectContext context) {
        if (energyCost > 0 && !pool.tryConsume(player, energyCost)) {
            player.playSound(player.getLocation(), Sound.BLOCK_DISPENSER_FAIL, 0.9f, 0.7f);
            visuals.play(CuePoint.ON_FAIL, player, context);
            return;
        }

        Location eye = player.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        double scaledRange = range + 5.0 * (level - 1);

        RayTraceResult result = player.getWorld().rayTraceEntities(
                eye, dir, scaledRange, 0.5,
                e -> e instanceof LivingEntity && !e.equals(player));

        visuals.play(CuePoint.ON_FIRE, player, context);

        if (result != null && result.getHitEntity() instanceof LivingEntity target) {
            target.damage(damagePerLevel * level, player);
            if (knockback > 0) {
                target.setVelocity(target.getVelocity()
                        .add(dir.clone().multiply(knockback).add(new Vector(0, 0.5, 0))));
            }
            Location hitLoc = result.getHitPosition().toLocation(player.getWorld());
            visuals.play(CuePoint.ON_HIT, player, context, hitLoc);
        } else {
            visuals.play(CuePoint.ON_MISS, player, context);
        }
    }

    @Override
    public void remove(Player player) {}
}
