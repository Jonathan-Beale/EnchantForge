package com.example.enchantforge.condition;

public final class AbsorptionDepletedCondition implements EndCondition {

    public static final AbsorptionDepletedCondition INSTANCE = new AbsorptionDepletedCondition();

    private AbsorptionDepletedCondition() {}

    @Override public boolean requiresTracking() { return true; }
    @Override public int getEffectDurationTicks() { return -1; }
    @Override public String getDisplayLabel() { return "until depleted"; }
}
