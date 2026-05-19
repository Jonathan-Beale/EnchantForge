package com.example.enchantforge.effect.visual.layers;

import com.example.enchantforge.effect.visual.VisualLayer;
import com.example.enchantforge.effect.visual.VisualLayerContext;
import org.bukkit.Sound;

import java.util.Locale;
import java.util.Map;

public final class SoundLayer implements VisualLayer {

    private final Sound sound;
    private final float volume;
    private final float pitch;

    private SoundLayer(Sound sound, float volume, float pitch) {
        this.sound = sound;
        this.volume = volume;
        this.pitch = pitch;
    }

    @Override
    public void play(VisualLayerContext ctx) {
        ctx.player().getWorld().playSound(ctx.player().getLocation(), sound, volume, pitch);
    }

    public static SoundLayer fromMap(Map<?, ?> map) {
        Object raw = map.get("sound");
        if (raw == null) return null;
        try {
            Sound sound = Sound.valueOf(String.valueOf(raw).toUpperCase(Locale.ROOT));
            float volume = (float) LayerUtils.doubleVal(map, "volume", 1.0);
            float pitch  = (float) LayerUtils.doubleVal(map, "pitch",  1.0);
            return new SoundLayer(sound, volume, pitch);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
