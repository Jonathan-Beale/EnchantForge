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

public class EyeLaserEffect implements EnchantEffect {

    private final double baseRange;
    private final double baseDamage;

    public EyeLaserEffect(double baseRange, double baseDamage) {
        this.baseRange = baseRange;
        this.baseDamage = baseDamage;
    }

    public static EyeLaserEffect fromYaml(ConfigurationSection section) {
        return new EyeLaserEffect(
                section.getDouble("range", 20.0),
                section.getDouble("damage", 4.0));
    }

    @Override
    public void apply(Player player, int level, int durationTicks) {
        Location eye = player.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        double range  = baseRange + 5.0 * (level - 1);
        double damage = baseDamage * level;

        // Perpendicular vectors for beam width
        Vector side = dir.clone().crossProduct(new Vector(0, 1, 0));
        if (side.lengthSquared() < 0.001) side = dir.clone().crossProduct(new Vector(1, 0, 0));
        side.normalize().multiply(0.12);
        Vector vert = dir.clone().crossProduct(side).normalize().multiply(0.12);

        // Muzzle flash
        player.getWorld().spawnParticle(Particle.FLASH, eye, 2, 0.05, 0.05, 0.05, 0);
        player.getWorld().spawnParticle(Particle.DUST, eye, 16, 0.2, 0.2, 0.2, 0,
                new Particle.DustOptions(Color.WHITE, 3.0f));
        player.getWorld().spawnParticle(Particle.FLAME, eye, 6, 0.1, 0.1, 0.1, 0.15);

        // Raycast
        RayTraceResult result = player.getWorld().rayTraceEntities(
                eye, dir, range, 0.5,
                e -> e instanceof LivingEntity && !e.equals(player));

        double beamLength = (result != null && result.getHitEntity() instanceof LivingEntity)
                ? result.getHitPosition().clone().subtract(eye.toVector()).length()
                : range;

        // Wide five-strand beam: white-hot core + red-orange ring + flame flickers
        for (double d = 1.5; d < beamLength; d += 0.18) {
            Location c = eye.clone().add(dir.clone().multiply(d));
            player.getWorld().spawnParticle(Particle.DUST, c, 1, 0, 0, 0, 0,
                    new Particle.DustOptions(Color.fromRGB(255, 240, 180), 1.8f));
            player.getWorld().spawnParticle(Particle.DUST, c.clone().add(side), 1, 0, 0, 0, 0,
                    new Particle.DustOptions(Color.fromRGB(255, 50, 0), 1.2f));
            player.getWorld().spawnParticle(Particle.DUST, c.clone().subtract(side), 1, 0, 0, 0, 0,
                    new Particle.DustOptions(Color.fromRGB(255, 50, 0), 1.2f));
            player.getWorld().spawnParticle(Particle.DUST, c.clone().add(vert), 1, 0, 0, 0, 0,
                    new Particle.DustOptions(Color.fromRGB(255, 100, 0), 1.2f));
            player.getWorld().spawnParticle(Particle.DUST, c.clone().subtract(vert), 1, 0, 0, 0, 0,
                    new Particle.DustOptions(Color.fromRGB(255, 100, 0), 1.2f));
            if ((int)(d / 0.18) % 3 == 0) {
                player.getWorld().spawnParticle(Particle.FLAME, c, 1, 0.04, 0.04, 0.04, 0.02);
            }
        }

        // Firing sounds (layered)
        player.getWorld().playSound(eye, Sound.ENTITY_GUARDIAN_ATTACK, 1.2f, 1.8f);
        player.getWorld().playSound(eye, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.8f, 1.6f);
        player.getWorld().playSound(eye, Sound.ENTITY_WITHER_SHOOT, 0.6f, 1.8f);

        // Impact
        if (result != null && result.getHitEntity() instanceof LivingEntity target) {
            target.damage(damage, player);
            target.setFireTicks(100);
            target.setVelocity(target.getVelocity().add(dir.clone().multiply(1.5).add(new Vector(0, 0.5, 0))));

            Location hit = result.getHitPosition().toLocation(player.getWorld());
            player.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, hit, 4, 0.4, 0.4, 0.4, 0);
            player.getWorld().spawnParticle(Particle.FLAME, hit, 60, 0.6, 0.6, 0.6, 0.25);
            player.getWorld().spawnParticle(Particle.LAVA, hit, 20, 0.3, 0.3, 0.3, 0);
            player.getWorld().spawnParticle(Particle.DUST, hit, 40, 0.7, 0.7, 0.7, 0,
                    new Particle.DustOptions(Color.fromRGB(255, 80, 0), 3.0f));
            player.getWorld().playSound(hit, Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 0.5f);
            player.getWorld().playSound(hit, Sound.ENTITY_DRAGON_FIREBALL_EXPLODE, 1.0f, 0.8f);
            player.getWorld().playSound(hit, Sound.ENTITY_BLAZE_DEATH, 0.9f, 1.0f);
        }
    }

    @Override
    public void remove(Player player) {}
}
