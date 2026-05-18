package com.example.enchantforge.effect;

import java.util.HashMap;
import java.util.Map;

public final class EnchantEffectContext {

    public record Key<T>(String id, Class<T> type) {}

    public static final Key<Double> DEALT_DAMAGE = new Key<>("dealt_damage", Double.class);
    public static final Key<String> TRIGGER_ID   = new Key<>("trigger_id",   String.class);
    public static final EnchantEffectContext NONE = new EnchantEffectContext(Map.of());

    private final Map<String, Object> data;

    private EnchantEffectContext(Map<String, Object> data) {
        this.data = Map.copyOf(data);
    }

    @SuppressWarnings("unchecked")
    public <T> T get(Key<T> key) {
        return (T) data.get(key.id());
    }

    public <T> T getOrDefault(Key<T> key, T defaultValue) {
        T value = get(key);
        return value != null ? value : defaultValue;
    }

    public <T> boolean has(Key<T> key) {
        return data.containsKey(key.id());
    }

    public boolean hasDealtDamage() {
        Double d = get(DEALT_DAMAGE);
        return d != null && d > 0;
    }

    public double dealtDamage() {
        return getOrDefault(DEALT_DAMAGE, 0.0);
    }

    public static EnchantEffectContext fromDealDamage(double damage) {
        return builder().set(DEALT_DAMAGE, Math.max(0.0, damage)).build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final Map<String, Object> data = new HashMap<>();

        public <T> Builder set(Key<T> key, T value) {
            data.put(key.id(), value);
            return this;
        }

        public EnchantEffectContext build() {
            return new EnchantEffectContext(data);
        }
    }
}
