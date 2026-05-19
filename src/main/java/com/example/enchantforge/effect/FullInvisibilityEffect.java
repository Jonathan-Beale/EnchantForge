package com.example.enchantforge.effect;

import com.example.enchantforge.VisibilityUtil;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class FullInvisibilityEffect implements EnchantEffect {

    public static final FullInvisibilityEffect INSTANCE = new FullInvisibilityEffect();

    private FullInvisibilityEffect() {}

    @Override
    public String id() { return "full_invisibility"; }

    @Override
    public void apply(Player player, int enchantLevel, int durationTicks) {
        VisibilityUtil.hide(player);
        // Invisibility potion suppresses vanilla mob line-of-sight checks as a secondary layer
        int dur = durationTicks > 0 ? durationTicks : Integer.MAX_VALUE / 20;
        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, dur, 0, false, false, false));
        // Drop existing mob targets so mobs already chasing the player stop immediately
        player.getWorld().getNearbyEntities(player.getLocation(), 64, 64, 64,
                        e -> e instanceof Mob m && player.equals(m.getTarget()))
                .forEach(e -> ((Mob) e).setTarget(null));
    }

    @Override
    public void remove(Player player) {
        VisibilityUtil.show(player);
        player.removePotionEffect(PotionEffectType.INVISIBILITY);
    }
}
