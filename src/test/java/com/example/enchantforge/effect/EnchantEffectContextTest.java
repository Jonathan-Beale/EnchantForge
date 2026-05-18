package com.example.enchantforge.effect;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EnchantEffectContextTest {

    @Test
    void getReturnsNullWhenAbsent() {
        assertNull(EnchantEffectContext.NONE.get(EnchantEffectContext.DEALT_DAMAGE));
    }

    @Test
    void getOrDefaultReturnsFallback() {
        assertEquals(0.0, EnchantEffectContext.NONE.getOrDefault(EnchantEffectContext.DEALT_DAMAGE, 0.0));
    }

    @Test
    void hasReflectsPresence() {
        assertFalse(EnchantEffectContext.NONE.has(EnchantEffectContext.DEALT_DAMAGE));

        var ctx = EnchantEffectContext.builder()
                .set(EnchantEffectContext.DEALT_DAMAGE, 5.0)
                .build();
        assertTrue(ctx.has(EnchantEffectContext.DEALT_DAMAGE));
    }

    @Test
    void noneHasNoKeys() {
        assertFalse(EnchantEffectContext.NONE.hasDealtDamage());
    }

    @Test
    void fromDealDamageRoundTrips() {
        var ctx = EnchantEffectContext.fromDealDamage(7.5);
        assertTrue(ctx.hasDealtDamage());
        assertEquals(7.5, ctx.dealtDamage(), 0.0001);
    }

    @Test
    void fromDealDamageClampNegative() {
        var ctx = EnchantEffectContext.fromDealDamage(-3.0);
        assertFalse(ctx.hasDealtDamage());
        assertEquals(0.0, ctx.dealtDamage(), 0.0001);
    }

    @Test
    void builderMultipleKeys() {
        var ctx = EnchantEffectContext.builder()
                .set(EnchantEffectContext.DEALT_DAMAGE, 10.0)
                .set(EnchantEffectContext.TRIGGER_ID, "on_deal_damage")
                .build();
        assertEquals(10.0, ctx.dealtDamage(), 0.0001);
        assertEquals("on_deal_damage", ctx.get(EnchantEffectContext.TRIGGER_ID));
    }
}
