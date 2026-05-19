package com.example.enchantforge.effect.visual.layers;

import com.example.enchantforge.effect.visual.VisualLayer;
import com.example.enchantforge.effect.visual.VisualLayerContext;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;

import java.util.Map;

public final class ParticleLayer implements VisualLayer {

    private final Particle particle;
    private final int count;
    private final double spread;
    private final double speed;
    private final Color color;
    private final float dustSize;
    private final boolean useHitLocation;

    private ParticleLayer(Particle particle, int count, double spread, double speed,
                          Color color, float dustSize, boolean useHitLocation) {
        this.particle = particle;
        this.count = count;
        this.spread = spread;
        this.speed = speed;
        this.color = color;
        this.dustSize = dustSize;
        this.useHitLocation = useHitLocation;
    }

    @Override
    public void play(VisualLayerContext ctx) {
        Location loc = useHitLocation ? ctx.effectiveLocation() : ctx.player().getEyeLocation();
        if (particle == Particle.DUST && color != null) {
            ctx.player().getWorld().spawnParticle(particle, loc, count,
                    spread, spread, spread, speed, new Particle.DustOptions(color, dustSize));
        } else {
            ctx.player().getWorld().spawnParticle(particle, loc, count, spread, spread, spread, speed);
        }
    }

    public static ParticleLayer fromMap(Map<?, ?> map) {
        Particle particle = parseParticle(map, "END_ROD");
        if (particle == null) return null;
        int count = LayerUtils.intVal(map, "count", 1);
        double spread = LayerUtils.doubleVal(map, "spread", 0.1);
        double speed = LayerUtils.doubleVal(map, "speed", 0.0);
        Object useHitRaw = map.get("use_hit_location");
        boolean useHit = useHitRaw != null && Boolean.parseBoolean(String.valueOf(useHitRaw));
        Color color = LayerUtils.parseColor(map.get("color"));
        float dustSize = (float) LayerUtils.doubleVal(map, "size", 1.5);
        return new ParticleLayer(particle, count, spread, speed, color, dustSize, useHit);
    }

    static Particle parseParticle(Map<?, ?> map, String defaultName) {
        try {
            Object raw = map.get("particle");
            String name = raw != null ? String.valueOf(raw) : defaultName;
            return Particle.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
