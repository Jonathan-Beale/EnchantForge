package com.example.enchantforge.condition;

public final class DamagedCondition implements EndCondition {

    public static final DamagedCondition INSTANCE = new DamagedCondition();

    private DamagedCondition() {}

    @Override public boolean requiresTracking() { return true; }
    @Override public int getEffectDurationTicks() { return -1; }
    @Override public String getDisplayLabel() { return "until hit"; }
}
