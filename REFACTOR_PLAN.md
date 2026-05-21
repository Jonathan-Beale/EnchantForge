# EnchantForge Refactor Plan

## Checklist

- [ ] Thruster Boots: Fix flight bug (player sometimes enters creative-mode-style flight rather than thrusters deactivating)
- [ ] Thruster Boots: Add a sprint-flight ability when the user holds sprint in flight, we want them to enter swim mode and move wherever they are looking (with thrusters)
- [ ] Thruster Boots: After boot activation and before landing, a single jump should activate a low energy-use hover mode; another space should begin flying again
- [ ] AI Interface: Add outlines to nearby entities within a certain radius, even those obscured from normal vision
- [ ] AI Interface: Give a warning/indicator when hostile mobs are approaching from the back (out of line of sight)

- [x] [§A Effect Mechanic Generalization](#a--effect-mechanic-generalization) — `RaycastDamageEffect`, `VelocityImpulseEffect` created and registered; YAMLs migrated
- [x] [§B Visual / Mechanical Separation](#b--visual--mechanical-separation) — `VisualSystem`, cue points, layer registry, YAML `visuals:` block
- [ ] [§C Optional](#c--optional--low-priority) — registry singletons, `StackBehavior.AVERAGE`

> All 23 items from the original plan (§1–§23) are complete and removed from this file.

---

## Status Notes

### §A — Effect Mechanic Generalization (partially complete)

`RaycastDamageEffect` and `VelocityImpulseEffect` are implemented and registered. The bundled YAMLs
have been migrated:

- `hand_laser.yml` — uses `style: raycast_damage`
- `thruster_boots.yml` — uses `style: velocity_impulse`

**However, `HandLaserEffect.java` and `ThrusterEffect.java` still exist and are still registered**
in `EnchantEffectTypeRegistry` (`hand_laser` and `thruster` style strings). They have not been
deleted. The migration plan called for deleting them after confirming the new YAMLs work; that
deletion step is still pending.

Additionally, the `on_suit_jump` trigger is currently a guard-only trigger: `SuitListener`
checks `enchantIndex.getByTrigger(uid, "on_suit_jump").isEmpty()` to know whether to run flight
logic, but does not dispatch the enchant's `apply()` method. The `velocity_impulse` effect in
`thruster_boots.yml` is therefore never called — `SuitListener` applies flight physics directly
via `setVelocity` and `setAllowFlight`. If the intent is to drive thruster behavior through the
effect system (energy cost from YAML, direction from YAML), the dispatch path needs to be wired.

---

## Planned

### §C — Optional / Low Priority

- **Registry singletons** — convert `EnchantTriggerTypeRegistry` and `EnchantEffectTypeRegistry`
  to instances; constructor-inject wherever used
- **`StackBehavior.AVERAGE`** — averaging stack mode for enchants where multiple pieces should
  not stack additively
