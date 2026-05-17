package com.example.enchantforge.effect;

import com.example.enchantforge.SuitListener;
import org.bukkit.entity.Player;

public class FridayAiEffect implements EnchantEffect {

    public static final FridayAiEffect INSTANCE = new FridayAiEffect();
    private FridayAiEffect() {}

    @Override
    public String id() { return "friday_ai"; }

    @Override
    public void apply(Player player, int level, int durationTicks) {
        SuitListener suit = SuitListener.getInstance();
        if (suit != null) suit.activateSuit(player);
    }

    @Override
    public void remove(Player player) {
        SuitListener suit = SuitListener.getInstance();
        if (suit != null) suit.deactivateSuit(player);
    }
}
