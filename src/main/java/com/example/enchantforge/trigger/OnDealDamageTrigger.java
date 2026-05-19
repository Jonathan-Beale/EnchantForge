package com.example.enchantforge.trigger;

import com.example.enchantforge.WeaponTriggerSpec;
import com.example.enchantforge.effect.EnchantEffectContext;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

public final class OnDealDamageTrigger extends EnchantTrigger {

    public static final WeaponTriggerSpec<EntityDamageByEntityEvent> SPEC = new WeaponTriggerSpec<>(
            "on_deal_damage",
            EntityDamageByEntityEvent.class,
            e -> e.getDamager() instanceof Player && e.getFinalDamage() > 0,
            e -> (Player) e.getDamager(),
            e -> ((Player) e.getDamager()).getInventory().getItemInMainHand(),
            e -> EnchantEffectContext.fromDealDamage(e.getFinalDamage())
    );

    @Override
    public String id() { return "on_deal_damage"; }
}
