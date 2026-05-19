package com.example.enchantforge.trigger;

/** Fired by SuitListener on double-jump (space mid-air) with charge-up support. */
public final class OnSuitJumpTrigger extends EnchantTrigger {
	@Override
	public String id() {
		return "on_suit_jump";
	}
}
