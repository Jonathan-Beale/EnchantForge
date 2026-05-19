package com.example.enchantforge;

import com.example.enchantforge.effect.EnchantEffectContext;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.inventory.ItemStack;

import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Declarative specification for a weapon-based enchant trigger.
 * Each trigger type that fires on a Bukkit weapon event declares a static {@code SPEC} field of
 * this type. {@link EnchantEventRouter} registers specs at startup and handles dispatch uniformly
 * — adding a new weapon trigger requires no new listener class.
 *
 * @param triggerId       matches {@link com.example.enchantforge.trigger.EnchantTrigger#id()}
 * @param eventClass      the Bukkit event this trigger fires on
 * @param guard           pre-check; if false the event is skipped entirely for this trigger
 * @param playerExtractor extracts the attacking/triggering player from the event
 * @param weaponExtractor extracts the held weapon ItemStack from the event
 * @param contextBuilder  builds an {@link EnchantEffectContext} from the event, or null if no
 *                        context is needed (effects receive the no-arg {@code apply} overload)
 */
public record WeaponTriggerSpec<E extends Event>(
        String triggerId,
        Class<E> eventClass,
        Predicate<E> guard,
        Function<E, Player> playerExtractor,
        Function<E, ItemStack> weaponExtractor,
        Function<E, EnchantEffectContext> contextBuilder
) {}
