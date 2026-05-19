package com.example.enchantforge.condition;

public final class NeverCondition implements EndCondition {

    public static final NeverCondition INSTANCE = new NeverCondition();

    private NeverCondition() {}

    @Override public boolean requiresTracking() { return false; }
    @Override public int getEffectDurationTicks() { return -1; }
    @Override public String getDisplayLabel() { return "∞"; }
}
