package com.example.enchantforge.condition;

public final class TimeCondition implements EndCondition {

    private final int ticks;

    public TimeCondition(int ticks) {
        this.ticks = ticks;
    }

    @Override public boolean requiresTracking() { return false; }
    @Override public int getEffectDurationTicks() { return ticks; }
    @Override public String getDisplayLabel() { return (ticks / 20) + "s"; }
}
