package com.example.enchantforge.effect.visual.layers;

import com.example.enchantforge.effect.visual.VisualLayer;
import com.example.enchantforge.effect.visual.VisualLayerContext;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;

import java.util.Map;

public final class BurstLayer implements VisualLayer {

    private final Particle particle;
    private final int count;
    private final double radius;
    private final double speed;
    private final Color color;
    private final float dustSize;

    private BurstLayer(Particle particle, int count, double radius, double speed,
                       Color color, float dustSize) {
        this.particle = particle;
        this.count = count;
        this.radius = radius;
        this.speed = speed;
        this.color = color;
        this.dustSize = dustSize;
    }

    @Override
    public void play(VisualLayerContext ctx) {
        Location loc = ctx.effectiveLocation();
        if (particle == Particle.DUST && color != null) {
            ctx.player().getWorld().spawnParticle(particle, loc, count,
                    radius, radius, radius, speed, new Particle.DustOptions(color, dustSize));
        } else {
            ctx.player().getWorld().spawnParticle(particle, loc, count, radius, radius, radius, speed);
        }
    }

    public static BurstLayer fromMap(Map<?, ?> map) {
        Particle particle = ParticleLayer.parseParticle(map, "CRIT");
        if (particle == null) return null;
        int count = LayerUtils.intVal(map, "count", 8);
        double radius = LayerUtils.doubleVal(map, "radius", 0.4);
        double speed = LayerUtils.doubleVal(map, "speed", 0.1);
        Color color = LayerUtils.parseColor(map.get("color"));
        float dustSize = (float) LayerUtils.doubleVal(map, "size", 1.5);
        return new BurstLayer(particle, count, radius, speed, color, dustSize);
    }
}
