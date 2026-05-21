package com.example.enchantforge.effect;

import com.example.enchantforge.effect.visual.CuePoint;
import com.example.enchantforge.effect.visual.VisualSystem;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.Locale;

public final class VelocityImpulseEffect implements EnchantEffect {

    private final PlayerResourcePool pool;
    private final String direction;
    private final double powerPerLevel;
    private final double energyCost;
    private final VisualSystem visuals;

    public VelocityImpulseEffect(PlayerResourcePool pool, String direction, double powerPerLevel,
                                  double energyCost, VisualSystem visuals) {
        this.pool = pool;
        this.direction = direction;
        this.powerPerLevel = powerPerLevel;
        this.energyCost = energyCost;
        this.visuals = visuals;
    }

    @Override
    public String id() { return "velocity_impulse"; }

    public static VelocityImpulseEffect fromYaml(NamespacedKey key, ConfigurationSection section) {
        return new VelocityImpulseEffect(
                ResourcePoolRegistry.get(section.getString("resourcePool", "energy")),
                section.getString("direction", "forward"),
                section.getDouble("powerPerLevel", 1.0),
                section.getDouble("energyCost", 0.0),
                VisualSystem.fromYaml(section));
    }

    @Override
    public void apply(Player player, int level, int durationTicks) {
        apply(player, level, durationTicks, EnchantEffectContext.NONE);
    }

    @Override
    public void apply(Player player, int level, int durationTicks, EnchantEffectContext context) {
        double power = powerPerLevel * level;
        double cost  = energyCost;
        boolean maxCharge = false;

        Integer chargeTicks = context.get(EnchantEffectContext.CHARGE_TICKS);
        if (chargeTicks != null) {
            if (chargeTicks < 5)       { power *= 0.65; cost *= 0.45; }
            else if (chargeTicks >= 20) { power *= 1.85; cost *= 1.9; maxCharge = true; }
            // 5-19 ticks: default multipliers
        }

        if (cost > 0 && !pool.tryConsume(player, cost)) {
            player.playSound(player.getLocation(), Sound.BLOCK_DISPENSER_FAIL, 0.9f, 0.7f);
            visuals.play(CuePoint.ON_FAIL, player, context);
            return;
        }

        Vector impulse = computeDirection(player).multiply(power);
        double vy = Math.min(0.5 + power * 0.25, maxCharge ? 1.4 : 1.0);
        Vector current = player.getVelocity();
        player.setVelocity(new Vector(
                current.getX() + impulse.getX(),
                vy,
                current.getZ() + impulse.getZ()));
        player.setFallDistance(0);

        visuals.play(CuePoint.ON_FIRE, player, context);

        if (maxCharge) {
            Location feet = player.getLocation();
            // Structured shockwave ring at tier-3
            for (int i = 0; i < 20; i++) {
                double a = i * Math.PI * 2.0 / 20;
                Location rim = feet.clone().add(Math.cos(a) * 0.7, 0.05, Math.sin(a) * 0.7);
                player.getWorld().spawnParticle(Particle.DUST, rim, 1, 0, 0, 0, 0,
                        new Particle.DustOptions(Color.fromRGB(255, 240, 180), 2.0f));
                player.getWorld().spawnParticle(Particle.DUST, rim, 1, 0.04, 0.02, 0.04, 0,
                        new Particle.DustOptions(Color.fromRGB(255, 100, 0), 1.4f));
            }
            player.getWorld().spawnParticle(Particle.FLASH, feet, 3, 0.1, 0.05, 0.1, 0);
            player.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, feet, 2, 0.2, 0.1, 0.2, 0);
            player.getWorld().playSound(feet, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.6f, 1.6f);
            player.getWorld().playSound(feet, Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 1.6f);
        }
    }

    /**
     * Per-tick continuous thrust — called every tick while space is held mid-air.
     * Cost is energyCost/6 per tick; with regen=2/tick the net drain is ~1/tick,
     * so a full tank (500) lasts ~475 ticks (~24 s) before hitting the 5% reserve.
     * Returns false if the energy reserve is hit (caller should stop thrusting).
     */
    public boolean tickThrust(Player player, int level) {
        double tickCost = energyCost / 6.0;
        if (tickCost > 0 && !pool.tryConsume(player, tickCost)) {
            return false;
        }

        double tickPower = powerPerLevel * level * 0.06;
        Vector horiz = computeDirection(player).multiply(tickPower);
        Vector v = player.getVelocity();
        double vy = Math.min(v.getY() + 0.08, 0.65);
        player.setVelocity(new Vector(v.getX() + horiz.getX(), vy, v.getZ() + horiz.getZ()));
        player.setFallDistance(0);

        Location feet = player.getLocation();
        Vector look = feet.getDirection();
        Vector side = look.clone().crossProduct(new Vector(0, 1, 0));
        if (side.lengthSquared() < 0.001) side = look.clone().crossProduct(new Vector(1, 0, 0));
        side.normalize().multiply(0.22);
        int count = player.isSprinting() ? 5 : 2;
        for (Location foot : new Location[]{feet.clone().add(side), feet.clone().subtract(side)}) {
            player.getWorld().spawnParticle(Particle.DUST, foot, count, 0.06, 0.04, 0.06, 0,
                    new Particle.DustOptions(Color.fromRGB(255, 240, 180), 1.8f));
            player.getWorld().spawnParticle(Particle.DUST, foot, count, 0.08, 0.05, 0.08, 0,
                    new Particle.DustOptions(Color.fromRGB(255, 100, 0), 1.2f));
        }
        return true;
    }

    private Vector computeDirection(Player player) {
        Vector look = player.getLocation().getDirection();
        return switch (direction.toLowerCase(Locale.ROOT)) {
            case "up"             -> new Vector(0, 1, 0);
            case "backward"       -> horizontal(look).multiply(-1);
            case "look"           -> look.clone();
            case "away_from_look" -> look.clone().multiply(-1);
            case "wasd_or_up" -> {
                var input = player.getCurrentInput();
                boolean anyMove = input.isForward() || input.isBackward()
                        || input.isLeft() || input.isRight();
                if (!anyMove) yield new Vector(0, 1, 0);
                // Build a horizontal direction vector from WASD state relative to look direction.
                // right = (fwd.z, 0, -fwd.x) — 90° clockwise in the XZ plane.
                Vector fwd   = horizontal(look);
                Vector right = new Vector(fwd.getZ(), 0, -fwd.getX());
                Vector dir   = new Vector(0, 0, 0);
                if (input.isForward())  dir.add(fwd);
                if (input.isBackward()) dir.subtract(fwd);
                if (input.isRight())    dir.add(right);
                if (input.isLeft())     dir.subtract(right);
                yield dir.lengthSquared() > 0.001 ? dir.normalize() : new Vector(0, 1, 0);
            }
            default               -> horizontal(look); // forward
        };
    }

    private static Vector horizontal(Vector look) {
        Vector h = new Vector(look.getX(), 0, look.getZ());
        return h.lengthSquared() > 0.001 ? h.normalize() : new Vector(1, 0, 0);
    }

    @Override
    public void remove(Player player) {}
}
