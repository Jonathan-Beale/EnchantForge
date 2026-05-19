package com.example.enchantforge.effect.visual;

import com.example.enchantforge.effect.EnchantEffectContext;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public final class VisualLayerContext {

    private final Player player;
    private final EnchantEffectContext enchantContext;
    private final Location hitLocation;

    public VisualLayerContext(Player player, EnchantEffectContext enchantContext) {
        this.player = player;
        this.enchantContext = enchantContext;
        this.hitLocation = null;
    }

    private VisualLayerContext(Player player, EnchantEffectContext enchantContext, Location hitLocation) {
        this.player = player;
        this.enchantContext = enchantContext;
        this.hitLocation = hitLocation;
    }

    public VisualLayerContext withHitLocation(Location loc) {
        return new VisualLayerContext(player, enchantContext, loc);
    }

    public Player player() { return player; }
    public EnchantEffectContext enchantContext() { return enchantContext; }
    public Location hitLocation() { return hitLocation; }

    /** Returns hitLocation if set, otherwise player feet location. */
    public Location effectiveLocation() {
        return hitLocation != null ? hitLocation : player.getLocation();
    }
}
