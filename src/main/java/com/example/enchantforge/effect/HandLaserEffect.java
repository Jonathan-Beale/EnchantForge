package com.example.enchantforge.effect;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

public class HandLaserEffect implements EnchantEffect {

    private final double baseRange;
    private final double baseDamage;
    private final double energyCost;

    public HandLaserEffect(double baseRange, double baseDamage, double energyCost) {
        this.baseRange = baseRange;
        this.baseDamage = baseDamage;
        this.energyCost = energyCost;
    }

    public static HandLaserEffect fromYaml(ConfigurationSection section) {
        return new HandLaserEffect(
                section.getDouble("range", 25.0),
                section.getDouble("damage", 6.0),
                section.getDouble("energy_cost", 15.0));
    }

    @Override
    public void apply(Player player, int level, int durationTicks) {
        if (!EnergyManager.tryConsume(player, energyCost)) {
            player.playSound(player.getLocation(), Sound.BLOCK_DISPENSER_FAIL, 0.9f, 0.7f);
            return;
        }

        Location eye = player.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        double range  = baseRange + 5.0 * (level - 1);
        double damage = baseDamage * level;

        // Perpendicular vectors for beam width
        Vector side = dir.clone().crossProduct(new Vector(0, 1, 0));
        if (side.lengthSquared() < 0.001) side = dir.clone().crossProduct(new Vector(1, 0, 0));
        side.normalize().multiply(0.11);
        Vector vert = dir.clone().crossProduct(side).normalize().multiply(0.11);

        // Muzzle: roughly at hand height (slightly below eye, offset forward)
        Location muzzle = eye.clone().subtract(0, 0.25, 0).add(dir.clone().multiply(0.7));
        player.getWorld().spawnParticle(Particle.FLASH,   muzzle, 1, 0, 0, 0, 0);
        player.getWorld().spawnParticle(Particle.END_ROD, muzzle, 10, 0.08, 0.08, 0.08, 0.06);
        player.getWorld().spawnParticle(Particle.DUST,    muzzle, 14, 0.12, 0.12, 0.12, 0,
                new Particle.DustOptions(Color.fromRGB(180, 230, 255), 2.8f));

        // Raycast
        RayTraceResult result = player.getWorld().rayTraceEntities(
                eye, dir, range, 0.5,
                e -> e instanceof LivingEntity && !e.equals(player));

        double beamLength = (result != null && result.getHitEntity() instanceof LivingEntity)
                ? result.getHitPosition().clone().subtract(eye.toVector()).length()
                : range;

        // Repulsor beam: white-blue core + cyan ring + END_ROD glow
        for (double d = 1.0; d < beamLength; d += 0.18) {
            Location c = eye.clone().add(dir.clone().multiply(d));
            player.getWorld().spawnParticle(Particle.DUST, c, 1, 0, 0, 0, 0,
                    new Particle.DustOptions(Color.fromRGB(220, 245, 255), 1.7f));
            player.getWorld().spawnParticle(Particle.DUST, c.clone().add(side), 1, 0, 0, 0, 0,
                    new Particle.DustOptions(Color.fromRGB(0, 200, 255), 1.1f));
            player.getWorld().spawnParticle(Particle.DUST, c.clone().subtract(side), 1, 0, 0, 0, 0,
                    new Particle.DustOptions(Color.fromRGB(0, 200, 255), 1.1f));
            player.getWorld().spawnParticle(Particle.DUST, c.clone().add(vert), 1, 0, 0, 0, 0,
                    new Particle.DustOptions(Color.fromRGB(60, 180, 255), 1.1f));
            player.getWorld().spawnParticle(Particle.DUST, c.clone().subtract(vert), 1, 0, 0, 0, 0,
                    new Particle.DustOptions(Color.fromRGB(60, 180, 255), 1.1f));
            if ((int)(d / 0.18) % 3 == 0) {
                player.getWorld().spawnParticle(Particle.END_ROD, c, 1, 0.02, 0.02, 0.02, 0.0);
            }
        }

        // Firing sounds
        player.getWorld().playSound(eye, Sound.ENTITY_GUARDIAN_ATTACK,    1.1f, 2.0f);
        player.getWorld().playSound(eye, Sound.BLOCK_BEACON_ACTIVATE,     0.5f, 2.0f);
        player.getWorld().playSound(eye, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.4f, 2.0f);

        // Impact
        if (result != null && result.getHitEntity() instanceof LivingEntity target) {
            target.damage(damage, player);
            target.setVelocity(target.getVelocity().add(dir.clone().multiply(2.0).add(new Vector(0, 0.5, 0))));

            Location hit = result.getHitPosition().toLocation(player.getWorld());
            player.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, hit, 2, 0.2, 0.2, 0.2, 0);
            player.getWorld().spawnParticle(Particle.END_ROD,           hit, 35, 0.45, 0.45, 0.45, 0.35);
            player.getWorld().spawnParticle(Particle.ELECTRIC_SPARK,    hit, 25, 0.35, 0.35, 0.35, 0.12);
            player.getWorld().spawnParticle(Particle.DUST,              hit, 30, 0.5, 0.5, 0.5, 0,
                    new Particle.DustOptions(Color.fromRGB(120, 220, 255), 2.5f));
            player.getWorld().playSound(hit, Sound.ENTITY_GENERIC_EXPLODE,          0.9f, 1.8f);
            player.getWorld().playSound(hit, Sound.ENTITY_LIGHTNING_BOLT_IMPACT,    0.8f, 1.5f);
        }
    }

    @Override
    public void remove(Player player) {}
}
