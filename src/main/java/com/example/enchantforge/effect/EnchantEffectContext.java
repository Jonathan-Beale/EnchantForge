package com.example.enchantforge.effect;

public record EnchantEffectContext(String triggerType, double dealtDamage) {

    public static final EnchantEffectContext NONE = new EnchantEffectContext("unknown", 0.0);

    public static EnchantEffectContext fromDealDamage(double dealtDamage) {
        return new EnchantEffectContext("on_deal_damage", Math.max(0.0, dealtDamage));
    }

    public boolean hasDealtDamage() {
        return dealtDamage > 0.0;
    }
}