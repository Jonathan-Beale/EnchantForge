package com.example.enchantforge.effect.visual.layers;

import com.example.enchantforge.effect.visual.VisualLayer;
import com.example.enchantforge.effect.visual.VisualLayerContext;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.util.Vector;

import java.util.Map;

public final class BeamLayer implements VisualLayer {

    private final Particle particle;
    private final double spacing;
    private final double range;
    private final double offsetX;
    private final double offsetY;
    private final double offsetZ;
    private final double speed;
    private final int count;
    private final Color color;
    private final float dustSize;

    private BeamLayer(Particle particle, double spacing, double range,
                      double offsetX, double offsetY, double offsetZ, double speed,
                      int count, Color color, float dustSize) {
        this.particle = particle;
        this.spacing = spacing;
        this.range = range;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.offsetZ = offsetZ;
        this.speed = speed;
        this.count = count;
        this.color = color;
        this.dustSize = dustSize;
    }

    @Override
    public void play(VisualLayerContext ctx) {
        Location eye = ctx.player().getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        double beamLength = range;
        if (ctx.hitLocation() != null) {
            beamLength = Math.min(range, ctx.hitLocation().toVector().distance(eye.toVector()));
        }
        for (double d = 0.5; d < beamLength; d += spacing) {
            Location point = eye.clone().add(dir.clone().multiply(d));
            if (particle == Particle.DUST && color != null) {
                ctx.player().getWorld().spawnParticle(particle, point, count,
                        offsetX, offsetY, offsetZ, speed, new Particle.DustOptions(color, dustSize));
            } else {
                ctx.player().getWorld().spawnParticle(particle, point, count,
                        offsetX, offsetY, offsetZ, speed);
            }
        }
    }

    public static BeamLayer fromMap(Map<?, ?> map) {
        Particle particle = ParticleLayer.parseParticle(map, "END_ROD");
        if (particle == null) return null;
        double spacing = LayerUtils.doubleVal(map, "spacing", 0.3);
        double range   = LayerUtils.doubleVal(map, "range",   20.0);
        double oX      = LayerUtils.doubleVal(map, "offset_x", 0.02);
        double oY      = LayerUtils.doubleVal(map, "offset_y", 0.02);
        double oZ      = LayerUtils.doubleVal(map, "offset_z", 0.02);
        double speed   = LayerUtils.doubleVal(map, "speed",    0.0);
        int count      = LayerUtils.intVal(map,    "count",    1);
        Color color    = LayerUtils.parseColor(map.get("color"));
        float dustSize = (float) LayerUtils.doubleVal(map, "size", 1.5);
        return new BeamLayer(particle, spacing, range, oX, oY, oZ, speed, count, color, dustSize);
    }
}
