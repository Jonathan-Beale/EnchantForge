package com.example.enchantforge.effect;

import com.example.enchantforge.effect.visual.CuePoint;
import com.example.enchantforge.effect.visual.VisualSystem;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * Effect style: {@code suit_flight}
 *
 * Drives the thruster-boot flight system. All physics parameters come from YAML;
 * {@link com.example.enchantforge.SuitListener} delegates flight ticks here instead of
 * using hardcoded constants.
 *
 * YAML fields:
 * <pre>
 * effect:
 *   style: suit_flight
 *   resourcePool: energy          # pool to drain during flight (default: energy)
 *   flightSpeed: 0.25             # horizontal speed (blocks/tick)
 *   sprintSpeed: 0.6              # speed in sprint-fly mode (pitch-aware, blocks/tick)
 *   verticalSpeed: 0.25           # ascend/descend speed (blocks/tick)
 *   flightEnergyPerTick: 40.0     # energy drained per tick while moving
 *   hoverEnergyPerTick: 10.0      # energy drained per tick while hovering (no inputs)
 *   doubleJumpWindowMs: 400       # milliseconds between two jump presses to engage flight
 *   visuals:
 *     on_fire:                    # played once when flight engages (double-jump)
 *       - ...
 * </pre>
 *
 * {@code apply()} is a no-op — {@link com.example.enchantforge.SuitListener} manages the
 * flight lifecycle directly. The effect is YAML-driven; no code changes are needed to tune
 * flight feel.
 */
public final class SuitFlightEffect implements EnchantEffect {

    private final PlayerResourcePool pool;
    private final double flightSpeed;
    private final double sprintSpeed;
    private final double verticalSpeed;
    private final double flightEnergyPerTick;
    private final double hoverEnergyPerTick;
    private final long doubleJumpWindowMs;
    private final VisualSystem visuals;

    public SuitFlightEffect(PlayerResourcePool pool,
                            double flightSpeed, double sprintSpeed, double verticalSpeed,
                            double flightEnergyPerTick, double hoverEnergyPerTick,
                            long doubleJumpWindowMs, VisualSystem visuals) {
        this.pool = pool;
        this.flightSpeed = flightSpeed;
        this.sprintSpeed = sprintSpeed;
        this.verticalSpeed = verticalSpeed;
        this.flightEnergyPerTick = flightEnergyPerTick;
        this.hoverEnergyPerTick = hoverEnergyPerTick;
        this.doubleJumpWindowMs = doubleJumpWindowMs;
        this.visuals = visuals;
    }

    public static SuitFlightEffect fromYaml(NamespacedKey key, ConfigurationSection section) {
        PlayerResourcePool pool = ResourcePoolRegistry.get(section.getString("resourcePool", "energy"));
        return new SuitFlightEffect(
                pool,
                section.getDouble("flightSpeed",           0.25),
                section.getDouble("sprintSpeed",           0.6),
                section.getDouble("verticalSpeed",         0.25),
                section.getDouble("flightEnergyPerTick",   40.0),
                section.getDouble("hoverEnergyPerTick",    10.0),
                section.getLong(  "doubleJumpWindowMs",    400L),
                VisualSystem.fromYaml(section));
    }

    // ---- Accessors for SuitListener ----

    public PlayerResourcePool getPool() { return pool; }

    public long getDoubleJumpWindowMs() { return doubleJumpWindowMs; }

    /** Play the on_fire visual cue when flight engages. */
    public void playEngageVisuals(Player player) {
        visuals.play(CuePoint.ON_FIRE, player, EnchantEffectContext.NONE);
    }

    // ---- Per-tick flight physics ----

    /**
     * Applies one tick of flight physics: energy drain, velocity, and exhaust particles.
     * Called by {@link com.example.enchantforge.SuitListener} every tick while flight is active.
     *
     * @param player      the flying player
     * @param wasSprintFly whether the player was in sprint-fly mode last tick (for hysteresis)
     * @return result encoding whether flight should continue and the new sprint-fly state
     */
    public TickResult tickFlight(Player player, boolean wasSprintFly) {
        var input = player.getCurrentInput();

        boolean hovering = !player.isSprinting()
                && !input.isForward() && !input.isBackward()
                && !input.isLeft()    && !input.isRight()
                && !input.isJump()    && !input.isSneak();

        double cost = hovering ? hoverEnergyPerTick : flightEnergyPerTick;
        if (!pool.tryConsume(player, cost)) {
            return new TickResult(false, false);
        }

        // Sprint-fly: enter when sprinting + forward, exit when forward released.
        // Never re-check isSprinting() while already active — setGliding(true) clears the sprint
        // flag server-side, which would otherwise create a jitter loop.
        boolean isSprintFly = wasSprintFly ? input.isForward()
                                           : (player.isSprinting() && input.isForward());

        double vx, vy, vz;
        double yawRad   = Math.toRadians(player.getLocation().getYaw());
        double pitchRad = Math.toRadians(player.getLocation().getPitch());

        if (isSprintFly) {
            double s = sprintSpeed;
            vx = -Math.sin(yawRad) * Math.cos(pitchRad) * s;
            vy = -Math.sin(pitchRad) * s;
            vz =  Math.cos(yawRad)  * Math.cos(pitchRad) * s;
        } else {
            double fx = -Math.sin(yawRad), fz =  Math.cos(yawRad);
            double rx = -Math.cos(yawRad), rz = -Math.sin(yawRad);
            double hx = 0, hz = 0;
            if (input.isForward())  { hx += fx; hz += fz; }
            if (input.isBackward()) { hx -= fx; hz -= fz; }
            if (input.isRight())    { hx += rx; hz += rz; }
            if (input.isLeft())     { hx -= rx; hz -= rz; }
            double len = Math.sqrt(hx * hx + hz * hz);
            if (len > 0.001) { hx = hx / len * flightSpeed; hz = hz / len * flightSpeed; }
            vx = hx;
            vz = hz;
            if (input.isJump())       vy =  verticalSpeed;
            else if (input.isSneak()) vy = -verticalSpeed;
            else                      vy =  0.0;
        }

        player.setVelocity(new Vector(vx, vy, vz));
        player.setFallDistance(0);

        // Per-boot exhaust trail every 3 ticks, denser in sprint-fly
        if (player.getServer().getCurrentTick() % 3 == 0) {
            spawnExhaustTrail(player, isSprintFly);
        }

        return new TickResult(true, isSprintFly);
    }

    private static void spawnExhaustTrail(Player player, boolean sprintFly) {
        Location feet = player.getLocation();
        Vector look = feet.getDirection();
        Vector side = look.clone().crossProduct(new Vector(0, 1, 0));
        if (side.lengthSquared() < 0.001) side = look.clone().crossProduct(new Vector(1, 0, 0));
        side.normalize().multiply(0.22);
        int count = sprintFly ? 6 : 2;
        for (Location foot : new Location[]{feet.clone().add(side), feet.clone().subtract(side)}) {
            player.getWorld().spawnParticle(Particle.DUST, foot, count, 0.06, 0.04, 0.06, 0,
                    new Particle.DustOptions(Color.fromRGB(255, 240, 180), 1.8f));
            player.getWorld().spawnParticle(Particle.DUST, foot, count, 0.08, 0.05, 0.08, 0,
                    new Particle.DustOptions(Color.fromRGB(255, 100, 0), 1.2f));
        }
    }

    // ---- EnchantEffect lifecycle ----

    /** Flight is managed by SuitListener; apply() is intentionally a no-op. */
    @Override
    public void apply(Player player, int level, int durationTicks) {}

    @Override
    public void remove(Player player) {}

    @Override
    public String id() { return "suit_flight"; }

    // ---- Result type ----

    public record TickResult(boolean alive, boolean sprintFly) {}
}
