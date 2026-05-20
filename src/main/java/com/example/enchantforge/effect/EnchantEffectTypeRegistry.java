package com.example.enchantforge.effect;

import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

public final class EnchantEffectTypeRegistry {

    private static final Map<String, BiFunction<NamespacedKey, ConfigurationSection, EnchantEffect>> FACTORIES = new HashMap<>();

    static {
        register("attribute", AttributeStyleEffect::fromYaml);
        register("potion", (key, section) -> PotionStyleEffect.fromYaml(section));
        register("absorption", (key, section) -> new AbsorptionEffect(new NamespacedKey(key.getNamespace(), key.getKey() + "_abs")));
        register("hunger", (key, section) -> HungerEffect.fromYaml(section));
        register("heal", HealEffect::fromYaml);
        register("full_invisibility", (key, section) -> FullInvisibilityEffect.INSTANCE);
        register("wolf_form", (key, section) -> WolfFormEffect.INSTANCE);
        register("morph_form", (key, section) -> MorphFormEffect.fromYaml(section));
        register("eye_laser", (key, section) -> EyeLaserEffect.fromYaml(section));
        register("thruster", (key, section) -> ThrusterEffect.fromYaml(section));
        register("hand_laser", (key, section) -> HandLaserEffect.fromYaml(section));
        register("friday_ai", (key, section) -> FridayAiEffect.fromYaml(section));
        register("raycast_damage", RaycastDamageEffect::fromYaml);
        register("velocity_impulse", VelocityImpulseEffect::fromYaml);
        register("robot_companion", (key, section) -> RobotCompanionEffect.fromYaml(section));
    }

    private EnchantEffectTypeRegistry() {}

    public static void register(String style, BiFunction<NamespacedKey, ConfigurationSection, EnchantEffect> factory) {
        if (style == null || style.isBlank() || factory == null) {
            throw new IllegalArgumentException("Effect style and factory must be provided");
        }
        FACTORIES.put(style.toLowerCase(Locale.ROOT), factory);
    }

    public static Set<String> registeredIds() {
        return Collections.unmodifiableSet(FACTORIES.keySet());
    }

    public static EnchantEffect fromYaml(NamespacedKey key, ConfigurationSection section) {
        if (section == null) throw new IllegalArgumentException("Missing effect section");
        String style = section.getString("style", "").toLowerCase(Locale.ROOT);
        BiFunction<NamespacedKey, ConfigurationSection, EnchantEffect> factory = FACTORIES.get(style);
        if (factory == null) {
            throw new IllegalArgumentException("Unknown effect style: " + style);
        }
        return factory.apply(key, section);
    }
}