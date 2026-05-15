package com.example.enchantforge.effect;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

public class ThrusterEffect implements EnchantEffect {

    private final double power;
    private final double energyCost;

    public ThrusterEffect(double power, double energyCost) {
        this.power = power;
        this.energyCost = energyCost;
    }

    public static ThrusterEffect fromYaml(ConfigurationSection section) {
        return new ThrusterEffect(
                section.getDouble("power", 0.7),
                section.getDouble("energy_cost", 18.0));
    }

    /** Called by SuitListener on double-jump release. chargeTicks = how long space was held. */
    public void fire(Player player, int enchantLevel, int chargeTicks) {
        int tier;
        double cost, thrustPower;
        if (chargeTicks < 5) {
            tier = 1; cost = energyCost * 0.45; thrustPower = power * 0.65;
        } else if (chargeTicks < 20) {
            tier = 2; cost = energyCost;         thrustPower = power;
        } else {
            tier = 3; cost = energyCost * 1.9;   thrustPower = power * 1.85;
        }

        if (!EnergyManager.tryConsume(player, cost)) {
            player.playSound(player.getLocation(), Sound.BLOCK_DISPENSER_FAIL, 0.9f, 0.7f);
            return;
        }

        double p = thrustPower + 0.08 * (enchantLevel - 1);
        Vector look = player.getLocation().getDirection();
        double vy   = Math.min(0.5 + p * 0.25, tier == 3 ? 1.4 : 1.0);
        player.setVelocity(player.getVelocity().add(new Vector(look.getX() * p, vy, look.getZ() * p)));
        player.setFallDistance(0);

        Location feet = player.getLocation();
        Vector side = look.clone().crossProduct(new Vector(0, 1, 0)).normalize().multiply(0.25);
        spawnExhaust(player, feet.clone().add(side),      tier);
        spawnExhaust(player, feet.clone().subtract(side), tier);

        if (tier == 3) {
            // Shockwave ring at max charge
            for (int i = 0; i < 16; i++) {
                double a = i * Math.PI * 2.0 / 16;
                Location rim = feet.clone().add(Math.cos(a) * 0.6, 0, Math.sin(a) * 0.6);
                player.getWorld().spawnParticle(Particle.FLAME, rim, 2, 0.04, 0.04, 0.04, 0.08);
            }
            player.getWorld().playSound(feet, Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 1.6f);
        }

        float vol   = 0.7f  + 0.15f * tier;
        float pitch = 1.15f + 0.15f * tier;
        player.getWorld().playSound(feet, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, vol, pitch);
        player.getWorld().playSound(feet, Sound.ENTITY_BLAZE_SHOOT, vol * 0.7f, pitch + 0.1f);
    }

    private static void spawnExhaust(Player player, Location foot, int tier) {
        player.getWorld().spawnParticle(Particle.FLAME, foot, 8 * tier, 0.06, 0.05, 0.06, 0.09f + 0.04f * tier);
        player.getWorld().spawnParticle(Particle.SMOKE, foot, 5 * tier, 0.08, 0.05, 0.08, 0.03f + 0.02f * tier);
        player.getWorld().spawnParticle(Particle.LAVA,  foot, tier,     0.04, 0.02, 0.04, 0.0);
        player.getWorld().spawnParticle(Particle.DUST,  foot, 4 * tier, 0.06, 0.04, 0.06, 0,
                new Particle.DustOptions(Color.fromRGB(255, 100 + 20 * tier, 0), 1.0f + 0.3f * tier));
    }

    /** Fallback for direct apply() calls (e.g. if trigger is still on_right_click). */
    @Override
    public void apply(Player player, int level, int durationTicks) {
        fire(player, level, 5); // tier 2 burst
    }

    @Override
    public void remove(Player player) {}
}
