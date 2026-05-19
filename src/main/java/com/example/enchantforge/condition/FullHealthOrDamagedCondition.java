package com.example.enchantforge.condition;

public final class FullHealthOrDamagedCondition implements EndCondition {

    public static final FullHealthOrDamagedCondition INSTANCE = new FullHealthOrDamagedCondition();

    private FullHealthOrDamagedCondition() {}

    @Override public boolean requiresTracking() { return true; }
    @Override public int getEffectDurationTicks() { return -1; }
    @Override public String getDisplayLabel() { return "until full hp or hit"; }
}
