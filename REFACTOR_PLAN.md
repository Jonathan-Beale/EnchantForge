# EnchantForge Refactor Plan

## Checklist

- [] Thruster Boots: Fix flight bug (player sometimes enters creative mode style flight rather than thrusters deactivating)
- [] Thruster Boots: Add a sprint-flight ability when the user holds sprint in flight, we want them to enter swim mode and move wherever they are looking (with thrusters)
- [] Thruster Boots: After boot activation and before landing a single jump should activate a low energy-use hover mode, another space should begin flying again
- [] AI Interface: Add outlines to nearby entities within a certain radius, even those obscured from normal vision
- [] AI Interface: Give a warning/indicator when hostile mobs are approaching from the back (out of line of sight)


- [x] [§A Effect Mechanic Generalization](#a--effect-mechanic-generalization) — `RaycastDamageEffect`, `VelocityImpulseEffect`; delete `HandLaserEffect`, `ThrusterEffect`
- [x] [§B Visual / Mechanical Separation](#b--visual--mechanical-separation) — `VisualSystem`, cue points, layer registry, YAML `visuals:` block
- [ ] [§C Optional](#c--optional--low-priority) — registry singletons, `StackBehavior.AVERAGE`

> All 23 items from the original plan (§1–§23) are complete and removed from this file.

---

## Planned

### §A — Effect Mechanic Generalization

**Goal:** Replace the hand-written `HandLaserEffect` and `ThrusterEffect` with generic parameterized
implementations. The new classes encode the mechanical logic (raycasting, velocity physics) but
carry zero particle/sound code — visuals are fully delegated to the visual system (§B).

#### `RaycastDamageEffect`

Replaces `HandLaserEffect`. YAML params:

```yaml
effect:
  style: raycast_damage
  range: 20.0            # blocks from player eye
  damagePerLevel: 3.0    # damage dealt per enchant level
  knockback: 0.5         # horizontal knockback applied to target (0 = none)
  energyCost: 15.0       # energy drained per activation (requires PlayerResourcePool)
```

Implementation:
- `fromYaml` reads all four fields; `energyCost` defaults to `0`
- `apply(Player, int, EnchantEffectContext)` traces a ray from the player's eye, hits the first
  non-spectator living entity within `range`, applies `damagePerLevel × level`, applies knockback
- If `energyCost > 0`, calls `energy.tryConsume(player, energyCost * level)`; cancels if
  insufficient
- After the mechanical action, fires `visualSystem.play("on_fire", player, context)`;
  fires `visualSystem.play("on_hit", player, context)` if an entity was struck, else `on_miss`
- Register as `"raycast_damage"` in `EnchantEffectTypeRegistry`

#### `VelocityImpulseEffect`

Replaces `ThrusterEffect`. YAML params:

```yaml
effect:
  style: velocity_impulse
  direction: forward        # forward | up | backward | look | away_from_look
  powerPerLevel: 1.2        # velocity magnitude per enchant level
  energyCost: 10.0
```

Implementation:
- `fromYaml` reads all three fields; `direction` defaults to `"forward"`
- `apply` computes a unit direction vector from `player.getLocation().getDirection()` per the
  `direction` field, scales by `powerPerLevel × level`, and calls `player.setVelocity()`
- Energy check same pattern as `RaycastDamageEffect`
- Fires `visualSystem.play("on_fire", player, context)` after applying
- Register as `"velocity_impulse"` in `EnchantEffectTypeRegistry`

#### Migration

- `hand_laser.yml` — change `style: hand_laser` → `style: raycast_damage`; keep numeric params
- `suit_thruster.yml` — change `style: thruster` → `style: velocity_impulse`; keep numeric params
- `HandLaserEffect.java`, `ThrusterEffect.java` — delete after YAML migration confirmed working
- Update Product Guide and AI Manual section text to use generic effect names

---

### §B — Visual / Mechanical Separation

**Goal:** Effects call the visual system by cue-point name; the visual layer is defined entirely
in YAML. Swapping from a purple particle beam to a blue one, adding a sound, or changing timing
requires no code changes.

#### Cue points

Four named moments an effect can announce to the visual system:

| Cue | When |
|-----|------|
| `on_fire` | Effect activates (always called) |
| `on_hit` | Hitscan/projectile struck a target |
| `on_miss` | Hitscan found no target within range |
| `on_fail` | Effect blocked (no energy, on cooldown — already blocked upstream) |

Effects call:
```java
visualSystem.play(CuePoint.ON_FIRE, player, context);
visualSystem.play(CuePoint.ON_HIT,  player, context);
```

#### Visual system architecture

```
VisualSystem                     — per-enchant instance; parses YAML; dispatches cues
  VisualCueBinding               — maps CuePoint → List<VisualLayer>
  VisualLayer                    — one composable effect: a particle burst, a beam, a sound
  VisualLayerType (registry)     — "beam" | "burst" | "trail" | "particle" | "sound"
  VisualLayerContext             — wraps Player + EnchantEffectContext for layer rendering
```

`VisualSystem.fromYaml(section)` is called inside each effect's `fromYaml` and returns a
`VisualSystem` (or `VisualSystem.NONE` if no `visuals:` block is present).

#### YAML schema

```yaml
effect:
  style: raycast_damage
  range: 20.0
  damagePerLevel: 3.0
  energyCost: 15.0

  visuals:
    on_fire:
      - type: sound
        sound: ENTITY_BLAZE_SHOOT
        volume: 1.0
        pitch: 1.4
      - type: particle
        particle: END_ROD
        count: 3
        spread: 0.1
    on_hit:
      - type: burst
        particle: CRIT
        count: 12
        radius: 0.5
      - type: sound
        sound: ENTITY_GENERIC_HURT
        volume: 0.8
        pitch: 1.2
    on_miss:
      - type: sound
        sound: ENTITY_ARROW_HIT_PLAYER
        volume: 0.6
        pitch: 0.9
```

`VisualSystem.NONE` — returned when `visuals:` is absent; `play()` is a no-op. Enchants without
visuals defined work exactly as before; the cost is one null-check.

#### Visual layer types (initial registry)

| Type | Description |
|------|-------------|
| `particle` | Spawns N particles at player location |
| `burst` | Spawns N particles in a sphere of radius R at player location |
| `beam` | Traces a line of particles from player eye to `range` blocks along look direction |
| `sound` | Plays a `Sound` at player location with `volume` and `pitch` |
| `trail` | Schedules N particle ticks along a vector over `durationTicks` — requires scheduler |

New visual layer types are added by implementing `VisualLayer` and registering in
`VisualLayerTypeRegistry` — no changes to effects or existing layers.

#### File scope

| File | Action |
|------|--------|
| `effect/visual/VisualSystem.java` | Create — cue dispatch, `fromYaml`, `NONE` |
| `effect/visual/VisualCueBinding.java` | Create — `Map<CuePoint, List<VisualLayer>>` |
| `effect/visual/VisualLayer.java` | Create — interface: `play(Player, VisualLayerContext)` |
| `effect/visual/VisualLayerContext.java` | Create — holds player + `EnchantEffectContext` |
| `effect/visual/VisualLayerTypeRegistry.java` | Create — `Map<String, Function<ConfigSection, VisualLayer>>` |
| `effect/visual/layers/ParticleLayer.java` | Create |
| `effect/visual/layers/BurstLayer.java` | Create |
| `effect/visual/layers/BeamLayer.java` | Create |
| `effect/visual/layers/SoundLayer.java` | Create |
| `effect/visual/layers/TrailLayer.java` | Create (requires `Plugin` for scheduler) |
| `effect/RaycastDamageEffect.java` | Create (replaces `HandLaserEffect`) |
| `effect/VelocityImpulseEffect.java` | Create (replaces `ThrusterEffect`) |
| `effect/HandLaserEffect.java` | Delete (after migration) |
| `effect/ThrusterEffect.java` | Delete (after migration) |
| `enchants/hand_laser.yml` | Edit (new effect style + visuals block) |
| `enchants/suit_thruster.yml` | Edit (new effect style + visuals block) |

#### Implementation order

1. `VisualLayer` interface + `VisualLayerContext`
2. `VisualLayerTypeRegistry` + `ParticleLayer`, `BurstLayer`, `SoundLayer`
3. `VisualSystem` + `VisualCueBinding` + `VisualSystem.NONE`
4. `RaycastDamageEffect` (mechanical only, calls `visualSystem.play(...)`)
5. `VelocityImpulseEffect`
6. Wire `visuals:` parsing into both new effects
7. Add `BeamLayer`, `TrailLayer`
8. Migrate `hand_laser.yml`, `suit_thruster.yml` to new schema with `visuals:` blocks
9. Delete `HandLaserEffect`, `ThrusterEffect` once migration is verified

---

### §C — Optional / Low Priority

- **§10 Registry singletons** — convert `EnchantTriggerTypeRegistry` and `EnchantEffectTypeRegistry`
  to instances; constructor-inject wherever used
- **§11 `StackBehavior.AVERAGE`** — averaging stack mode for enchants where multiple pieces should
  not stack additively
