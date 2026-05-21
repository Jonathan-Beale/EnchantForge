# EnchantForge — Claude Reference

## What this is

A Paper 1.21.4 plugin that adds data-driven custom enchantments defined entirely in YAML files.
No code changes are needed to add new enchantments. Each `.yml` file in the `enchants/` folder is
one enchantment. The plugin ships default enchants bundled in the jar and copies them to the data
folder on startup if they are missing or differ from the bundled version.

**Package:** `com.example.enchantforge`
**Build:** Gradle (`.\gradlew.bat build`), output at `build/libs/EnchantForge-1.0-SNAPSHOT.jar`
**Deploy:** `VibeCraftServer/build-EnchantForge.sh` builds and copies the jar into `VibeCraftServer/plugins/`
**Reload in-game:** `/cenchant reload` (re-reads all YAML files and config without restart)
**Cooldown diagnostic command:** `/ctestcooldown` (applies a short cooldown to the held item)

---

## Project layout

```
src/main/java/com/example/enchantforge/
  EnchantForge.java                — plugin entry point; wires all managers, listeners, registries
  CustomEnchant.java               — the core data object, loaded from YAML
  EnchantmentRegistry.java         — holds all loaded enchants; reads PersistentDataContainer on ItemStacks
  EnchantDebug.java                — per-enchant debug logging utility (see Debug section)
  CooldownManager.java             — cooldown store; save/load to cooldowns.yml
  ActiveEffectTracker.java         — player × enchant → effectiveLevel for tracked effects
  CombatTracker.java               — player → last-hit timestamp for out-of-combat regen
  StackBehavior.java               — enum: SUM / HIGHEST / EXCLUSIVE
  PlayerEnchantIndex.java          — per-player slot → enchant index; rebuilt on armor equip/change
  StackingDispatcher.java          — static util: collect-stack-dispatch pattern for trigger routing
  PlayerLifecycleRegistry.java     — declarative join/quit handler registry
  EnchantEventRouter.java          — registers weapon/armor specs; dispatches events generically
  DamageTakenListener.java         — handles on_damage_taken, stat_threshold triggers, end-conditions
  EquipmentEnchantListener.java    — handles armor equip/unequip, passive reapply, OOC regen
  RightClickListener.java          — handles on_right_click trigger dispatch
  SuitListener.java                — suit jump (double-jump flight), FRIDAY AI HUD, fall guard,
                                     mob ESP, hostile direction warnings, energy boss bar
  EnchantCatalogListener.java      — right-click catalog item to browse enchants
  EnchantForgeModInputListener.java — handles vibecraft:input messages from the mod
  EnchantCommand.java              — /cenchant apply | reload | catalog | guide
  CooldownTestCommand.java         — /ctestcooldown
  CooldownVisuals.java             — visual item cooldowns via player.setCooldown()
  ResourcePackManager.java         — generates enchantforge_pack.zip; serves it via built-in HTTP
  VibeCraftUiBridge.java           — all outbound plugin-channel messages to the mod;
                                     builds catalog, product guide, and AI manual schemas
  CatalogSchemaGenerator.java      — procedurally generates the enchantment catalog schema
  CatalogMetadata.java             — YAML catalog metadata fields (tags, items, order)
  CatalogFacets.java               — facet filtering helpers for the catalog screen
  VisibilityUtil.java              — full invisibility: hides player body, gear, from all clients
  ArmorTriggerSpec.java            — spec type for armor-based event triggers
  WeaponTriggerSpec.java           — spec type for weapon-based event triggers

  trigger/
    EnchantTrigger.java            — base class; id() identifies the trigger type
    EnchantTriggerTypeRegistry.java — factory map + weapon/armor spec lists; dispatched at startup
    OnEquipTrigger.java            — passive; fires on armor equip/unequip cycle
    OnDamageTakenTrigger.java      — fires when the player takes damage
    StatThresholdTrigger.java      — fires when a stat crosses a threshold at damage time
    OnRightClickTrigger.java       — fires on right-click while holding the item
    OnSuitJumpTrigger.java         — fires on double-jump mid-air (dispatched by SuitListener)
    OnDealDamageTrigger.java       — fires when the player deals damage with a weapon (WeaponTriggerSpec)
    OnKillEntityTrigger.java       — fires when the player kills an entity with a weapon (WeaponTriggerSpec)

  effect/
    EnchantEffect.java             — interface: apply(player, level, ticks), apply(..., context), remove()
    EnchantEffectTypeRegistry.java — factory registry; static block maps style strings → factories
    EnchantEffectContext.java      — typed key-value context passed from trigger to effect
    AbsorptionEffect.java          — adds absorption hearts via MAX_ABSORPTION attribute modifier
    AttributeStyleEffect.java      — modifies a Bukkit Attribute via AttributeModifier
    PotionStyleEffect.java         — applies a PotionEffect (amplifier = level - 1)
    HealEffect.java                — heals a % of damage dealt; optional overheal as absorption
    HungerEffect.java              — restores food and saturation
    FullInvisibilityEffect.java    — full invisibility via VisibilityUtil + INVISIBILITY potion
    WolfFormEffect.java            — spawns a wolf companion, hides player, grants potion buffs
    MorphFormEffect.java           — generic entity-form effect; entity type, potions, visual mode in YAML
    EyeLaserEffect.java            — eye-origin raycast beam; damages + ignites; no energy cost
    HandLaserEffect.java           — hand-origin raycast beam; damages + knockback; draws energy
    ThrusterEffect.java            — charge-up vertical thrust impulse; draws energy
    RaycastDamageEffect.java       — generic raycast damage + knockback with VisualSystem cues
    VelocityImpulseEffect.java     — generic velocity impulse with YAML direction + VisualSystem cues
    FridayAiEffect.java            — activates the FRIDAY AI suit (SuitListener.activateSuit)
    RobotCompanionEffect.java      — summons/dismisses Iron Golem companion via RobotCompanionManager
    PlayerResourcePool.java        — lazy-regen energy pool shared across energy-consuming effects
    RobotCompanionManager.java     — manages one golem per player; auto-respawns after death
    ScoreboardTeamUtil.java        — scoreboard team helpers for no-collide between player+entity

    visual/
      VisualSystem.java            — per-effect visual dispatcher; parsed from YAML visuals: block
      CuePoint.java                — enum: ON_FIRE | ON_HIT | ON_MISS | ON_FAIL
      VisualLayer.java             — interface: play(VisualLayerContext)
      VisualLayerContext.java      — wraps player + EnchantEffectContext + optional hit location
      VisualLayerTypeRegistry.java — factory registry: particle | burst | sound | beam
      layers/
        ParticleLayer.java         — spawns N particles at player location
        BurstLayer.java            — spawns N particles in a sphere at player or hit location
        SoundLayer.java            — plays a Sound at player location
        BeamLayer.java             — traces particle line from player eye along look direction

  condition/
    EndCondition.java              — interface + fromYaml() switch for duration types
    NeverCondition.java            — indefinite; effect stays until manually removed (unequip)
    TimeCondition.java             — scheduler-based expiry after N ticks
    DamagedCondition.java          — removed on the next hit taken
    FullHealthOrDamagedCondition.java — removed at full health or on the next hit
    StatThresholdCondition.java    — removed when a stat crosses a threshold
    AbsorptionDepletedCondition.java — removed when absorption drops below expected; deferred 1 tick

src/main/resources/
  plugin.yml
  config.yml                       — resource-pack host/port/url/required; vibecraft-mod URL; debug flags
  enchants/*.yml                   — bundled default enchantments (17 files)
  ui/main.json                     — base UI schema sent to the mod on startup

VibeCraftServer/plugins/EnchantForge/  — live server data folder
  config.yml                       — actual runtime config
  enchants/*.yml                   — runtime enchant files (may diverge from bundled defaults)
  cooldowns.yml                    — persisted cooldown state (save/load on enable/disable)
```

---

## Enchantment YAML schema

The filename (without extension) is the enchantment key unless `key:` is explicitly set.

```yaml
# --- Identity ---
key: optional_key_override      # defaults to filename; must be lowercase snake_case
displayName: "Swift Steps"
maxLevel: 3
debug: false                    # set true to enable per-enchant debug logging

# --- Catalog metadata (optional) ---
category: mobility              # display category grouping
catalog:
  tags:                         # filter tags for the VibeCraftMod catalog UI
    - mobility
    - tech
  items:                        # item-type tags for filtering
    - armor
    - boots
  order: 90                     # sort order within category (lower = first)

# --- What items it can be applied to ---
applicableTo:
  - ARMOR         # expands to: *_HELMET, *_CHESTPLATE, *_LEGGINGS, *_BOOTS
  - _BOOTS        # any material whose name ends with _BOOTS
  - DIAMOND_SWORD # exact material name match

# --- When it fires ---
trigger:
  type: on_equip          # passive; applied when armor is equipped, removed when unequipped

trigger:
  type: on_damage_taken   # fires every time the player takes damage (subject to cooldown)

trigger:
  type: stat_threshold    # fires when a stat crosses a threshold at the moment of damage
  stat: health            # "health" or any Bukkit Attribute field name (e.g. MAX_HEALTH)
  comparison: below       # "below" or "above"
  value: 6                # threshold (health is in half-hearts: 6 = 3 hearts)

trigger:
  type: on_right_click    # fires when the player right-clicks while holding the item

trigger:
  type: on_suit_jump      # fires on double-jump mid-air (dispatched by SuitListener)

trigger:
  type: on_deal_damage    # fires when the player deals damage with the held weapon

trigger:
  type: on_kill_entity    # fires when the player kills an entity with the held weapon

# --- What it does ---
effect:
  style: attribute              # permanently modifies a player attribute via AttributeModifier
  attribute: MOVEMENT_SPEED     # Bukkit Attribute enum name
  operation: ADD_NUMBER         # ADD_NUMBER | ADD_SCALAR | MULTIPLY_SCALAR_1
  amountPerLevel: 0.02          # modifier value added per enchant level
  slotGroup: FEET               # EquipmentSlotGroup (ANY | FEET | ARMOR | HAND | etc.)

effect:
  style: potion                 # applies a PotionEffect (amplifier = level - 1)
  potion: resistance            # PotionEffectType name, lowercase

effect:
  style: absorption             # adds absorption HP via MAX_ABSORPTION attribute modifier
                                # (4 HP per level; preserved on remove)

effect:
  style: heal                   # heals percentPerLevel × level × damage dealt
  percentPerLevel: 0.15
  overhealPerLevel: 0.0         # overflow above max HP added as temporary absorption

effect:
  style: hunger                 # restores food and saturation
  amountPerLevel: 2.0           # food points per level
  saturationPerLevel: 1.0

effect:
  style: full_invisibility      # hides player body and gear from all clients; clears mob targets

effect:
  style: wolf_form              # wolf companion + speed/strength/jump/night_vision potions

effect:
  style: morph_form             # generic entity-form: entity type, potions, and visual mode in YAML
  entity: WOLF                  # EntityType name
  visualMode: entity_follow     # entity_follow | none
  forceSwimPose: false
  hidePlayer: true
  mimicSwing: true
  effects:                      # potion specs; defaults to speed/strength/jump/night_vision if empty
    - type: speed
      amplifier: 1

effect:
  style: eye_laser              # eye-origin raycast beam; ignites target; no energy cost
  range: 20.0
  damage: 4.0

effect:
  style: hand_laser             # hand-origin repulsor beam; knockback; draws energy
  range: 25.0
  damage: 6.0
  energy_cost: 15.0

effect:
  style: thruster               # charge-up vertical/directional thrust; draws energy
  power: 0.7
  energy_cost: 18.0

effect:
  style: raycast_damage         # generic raycast damage with VisualSystem cue support
  range: 20.0
  damagePerLevel: 3.0
  knockback: 0.5
  energyCost: 0.0

effect:
  style: velocity_impulse       # generic velocity impulse with VisualSystem cue support
  direction: forward            # forward | up | backward | look | away_from_look | wasd_or_up
  powerPerLevel: 1.0
  energyCost: 0.0

effect:
  style: friday_ai              # activates FRIDAY AI suit (energy HUD, mob detection, fall guard)
  glowRadius: 32.0

effect:
  style: robot_companion        # summons Iron Golem companion; auto-respawns after death

# --- Visual cues (for raycast_damage and velocity_impulse) ---
  visuals:
    on_fire:
      - type: sound
        sound: ENTITY_BLAZE_SHOOT
        volume: 0.8
        pitch: 1.3
      - type: burst
        particle: FLASH
        count: 2
        radius: 0.05
        speed: 0.0
    on_hit:
      - type: burst
        particle: END_ROD
        count: 35
        radius: 0.45
        speed: 0.35
    on_miss:
      - type: sound
        sound: ENTITY_ARROW_HIT_PLAYER
        volume: 0.6
        pitch: 0.9
    on_fail:
      - type: sound
        sound: BLOCK_DISPENSER_FAIL
        volume: 0.9
        pitch: 0.7

# --- When the effect ends ---
duration:
  type: never                   # indefinite; effect stays until manually removed (e.g. unequip)

duration:
  type: time
  ticks: 600                    # 20 ticks = 1 second

duration:
  type: until_condition
  condition: damaged            # removed on the next hit taken

duration:
  type: until_condition
  condition: full_health_or_damaged  # removed at full health or on the next hit

duration:
  type: until_condition
  condition: stat_threshold     # removed when stat crosses threshold
  stat: health
  comparison: above
  value: 10

duration:
  type: until_condition
  condition: absorption_depleted  # removed when absorption drops below expected amount
                                  # (deferred 1 tick post-damage so vanilla drain completes)

# --- Timing & stacking ---
cooldown: 1200                  # ticks between activations; 0 = no cooldown
stackBehavior: highest          # sum | highest | exclusive

# --- Out-of-combat absorption regen (absorption effect only) ---
outOfCombatRefreshTicks: 600    # ticks since last hit before regen starts; 0 = disabled
outOfCombatRegenPerTick: 0.1    # absorption HP restored per tick; 0 = instant full restore

# --- Display ---
description: "+{amount}% Movement Speed"
displayAmountPerLevel: 20.0     # multiplier for {amount} in the description template

# Description template variables:
#   {amount}    displayAmountPerLevel × level (auto-formatted: integer if whole, 2dp otherwise)
#   {level}     enchant level as integer
#   {duration}  EndCondition.getDisplayLabel() — e.g. "30s", "∞", "until hit"
#   {cooldown}  cooldown in seconds, or "none" if 0
#   {refresh}   outOfCombatRefreshTicks / 20, or "never" if 0
```

---

## How loading works

1. `onEnable` → `saveDefaultConfig()` → `saveResource("ui/main.json", false)` → `saveDefaultEnchants()`
2. `saveDefaultEnchants()` copies bundled YAMLs to `plugins/EnchantForge/enchants/` only if the deployed
   file is missing or differs byte-for-byte from the bundled version (updates on plugin upgrade, unlike
   the old `saveResource(..., false)` which never overwrote)
3. `loadEnchantments()` scans `plugins/EnchantForge/enchants/*.yml`
4. Each file is loaded via `YamlConfiguration.loadConfiguration(file)` and passed to
   `CustomEnchant.fromYaml(key, yaml)`
5. Enchants are registered into `EnchantmentRegistry` (keyed by `NamespacedKey`)
6. `EnchantmentRegistry.getEnchants(ItemStack)` reads `PersistentDataContainer` keys on the item
   to find which enchants are applied and at what level
7. Cooldowns are loaded from `cooldowns.yml` after enchants are registered

`/cenchant reload` removes all active effects, clears tracker/cooldowns/energy for online players,
re-runs `loadEnchantments()`, reloads cooldowns, and calls `refreshPlayer` on each online player.

---

## How active effects are managed

### Passive (on_equip, NeverCondition)
Applied in `applyOnEquip` when armor is equipped. Removed in `removeOnEquip` before armor changes
are processed. These are NOT tracked in `ActiveEffectTracker` because they never expire on their
own — they are simply reapplied after every armor-change cycle.

### Passive with end condition (on_equip + requiresTracking)
Applied on equip AND tracked. The tracker records `player → enchantKey → effectiveLevel`. The
`DamageTakenListener` checks end conditions on every damage/regen event. When the condition is
met, the effect is removed, the tracker entry is cleared, and a cooldown is set. The
`reapplyInterruptedPassives` ticker (every 20 ticks) restores these once the cooldown expires.

### Triggered (on_damage_taken / stat_threshold)
Fires in `DamageTakenListener.onDamageTaken`. Skipped if on cooldown or already tracked. After
applying, either starts tracking (if `requiresTracking`) or sets cooldown directly.

### Weapon triggers (on_deal_damage, on_kill_entity)
Dispatched by `EnchantEventRouter` via `WeaponTriggerSpec`. Reads enchants from the held weapon's
`PersistentDataContainer`. Cooldowns are item-scoped.

### Armor triggers (on_suit_jump, etc.)
Dispatched by `EnchantEventRouter` via `ArmorTriggerSpec`, or directly by `SuitListener` for
`on_suit_jump`. Uses the `PlayerEnchantIndex` rather than weapon PDC.

### Right-click triggers (on_right_click)
Handled by `RightClickListener`. Reads enchants from the held item.

### Cooldowns
`CooldownManager` stores `expiry = currentTimeMs + (cooldownTicks × 50)`.

For item-triggered enchants (right click, deal-damage, kill), cooldowns are keyed by
`player + enchant + item-id`, so two same-material items do not share cooldown state.

For passive armor interruption logic (e.g. `on_equip + damaged`), cooldown scope is
`player + enchant` to preserve anti-equip-swap protections.

Vanilla/Paper visuals are cooldown-group based. `CooldownVisuals` assigns a unique cooldown group
per tracked stack so same-material items do not inherit each other's visual cooldown.

---

## Energy system (PlayerResourcePool)

Many active effects draw from a shared `PlayerResourcePool` instance (capacity 5000, regen 10/tick).
Effects that draw energy call `energy.tryConsume(player, cost)`, which returns `false` (and plays a
fail sound) if the remaining energy would drop below the 5% emergency reserve.

The bottom 5% of the pool is reserved for emergency triggers (fall guard in `SuitListener`), which
call `energy.tryConsumeEmergency` and bypass the reserve floor.

Energy is lazy-regen: `computeRegen` calculates elapsed ticks since the last drain and credits the
pool on read — there is no background scheduler task. `onChanged` callbacks allow `SuitListener` to
update the boss bar immediately when energy changes.

---

## SuitListener

`SuitListener` manages two overlapping suit subsystems:

### Thruster boots (on_suit_jump enchant)
Double-jumping mid-air engages creative-style custom flight (`setAllowFlight(true)` as an
anti-cheat bypass, but physics are applied manually via `setVelocity`). Controls:
- Space → ascend; Shift → descend; WASD → horizontal movement
- Sprint + W → sprint-fly mode (elytra pose, 3D look-direction movement at higher speed)
- Landing on the ground ends flight

Per-tick energy drain: hovering costs less than active flight. When energy hits the reserve floor,
flight is cancelled. A `FridayLine.FALL_PROTECTION` check in the 1-tick poll fires retrograde
braking if the player is falling fast enough toward a surface.

Scoreboard tag `thruster_flying` is added on flight start and removed on end.

### FRIDAY AI Interface (friday_ai enchant)
`FridayAiEffect.apply()` calls `SuitListener.activateSuit(player, glowRadius)`, which:
- Shows an energy boss bar (blue → yellow → red based on energy %)
- Starts a 5-tick poll for: boss bar updates, fall guard, FRIDAY audio cues, hostile mob warnings
- Sends `ui_schema_patch` with a `hostile_indicator` overlay widget to mod players
- Sends `ef_highlight_entities` messages every 5 ticks with hostile/neutral entity IDs
  for the mod's mob-ESP overlay

Hostile mob warning: if a Monster is within 14 blocks and behind the player (>120° angle), a
directional sound + `binding_update` (for mod users) or action bar arrow (for vanilla users) fires.

`FridayAiEffect.remove()` calls `SuitListener.deactivateSuit(player)`, which removes the boss bar,
clears the mod's entity highlight groups, and sends a null `binding_update` to clear the indicator.

---

## VibeCraftUiBridge

`VibeCraftUiBridge` is the sole class that sends outbound plugin-channel messages on `vibecraft:events`.
It builds three schema-driven screens:

| Method | Screen ID | Purpose |
|--------|-----------|---------|
| `openEnchantCatalog` | `enchantforge:catalog` | Searchable catalog of all loaded enchants (via CatalogSchemaGenerator) |
| `openProductGuide` | `enchantforge:product_guide` | Human-readable guide to triggers, effects, durations, stacking, energy |
| `openAIManual` | `enchantforge:ai_manual` | Technical reference: key files, live trigger/effect registries, end conditions, extension points |

`EnchantForgeModInputListener` handles incoming `vibecraft:input` messages addressed to `enchantforge`.
It supports `show all` (open catalog) and `apply <id> [level]` (run `/cenchant` and reopen catalog).

`/cenchant catalog` and `/cenchant guide` open these screens directly from the server command.

---

## Debug system

Add `debug: true` to any enchantment YAML. All state transitions for that enchant are logged to
the server console prefixed `[debug|enchant_key] PlayerName — event`.

**What is logged:**
- `on_equip applied lv2 (2 pieces, sum→2)` — passive applied on equip
- `on_equip skipped — cooldown (47s left)` — equip blocked by cooldown
- `on_equip removed (armor change)` — armor slot changed, passive removed
- `tracking started lv2` — effect entered the ActiveEffectTracker
- `triggered lv1 — hp 8.0→5.5` — damage trigger fired
- `trigger skipped — on cooldown (23s left)` — damage trigger blocked
- `trigger skipped — already active` — damage trigger blocked (effect already running)
- `cooldown started (60s)` — cooldown set
- `end condition met (until hit) — removing` — tracked effect ended
- `absorption depleted — removing` — AbsorptionDepletedCondition fired
- `passive restored lv1 (cooldown expired)` — interrupted passive reapplied by ticker
- `absorption fully restored (out-of-combat regen complete)` — OOC regen reached full

`EnchantDebug.log(enchant, player, msg)` short-circuits immediately when `enchant.isDebug()` is
false — no string allocation, no overhead.

Cooldown diagnostics:
- Enable `debug.cooldownVisuals: true` in `plugins/EnchantForge/config.yml`.
- Use `/ctestcooldown` while holding an item to verify item-isolated cooldown behavior.

---

## Resource pack

The plugin generates `enchantforge_pack.zip` on startup containing four 9×9 PNG sprites that
recolor absorption hearts teal. Configure in `plugins/EnchantForge/config.yml`:

```yaml
resource-pack:
  host: "192.168.1.123"  # server's public IP or domain; leave blank to disable
  port: 8080             # HTTP server port (must be open in firewall alongside 25565)
  url: ""                # override the auto-computed URL if behind a reverse proxy
  required: false
```

When `host` is set, the plugin starts a built-in HTTP server on `port`. The pack is sent to
players on join via `PlayerLifecycleRegistry`. The HTTP server is stopped cleanly in `onDisable`.

---

## Adding a new enchantment

1. Create `EnchantForge/src/main/resources/enchants/<key>.yml` using the schema above
2. Add `"<key>.yml"` to the `saveDefaultEnchants()` list in `EnchantForge.java`
3. Build and deploy
4. The file is copied to `VibeCraftServer/plugins/EnchantForge/enchants/` on startup if missing or outdated

If you only need the enchant on the live server without bundling it, drop the YAML directly into
`VibeCraftServer/plugins/EnchantForge/enchants/` and run `/cenchant reload`.

---

## Adding a new trigger type

Triggers come in three categories depending on where the enchant lives and what fires it.

### Weapon trigger (enchant on a held item; fires on a Bukkit event)

Examples: `on_deal_damage`, `on_kill_entity`

1. Create a class extending `EnchantTrigger` in `trigger/` with a `public static final WeaponTriggerSpec<E> SPEC` field.
   The spec declares: trigger ID, Bukkit event class, guard predicate, player extractor, weapon extractor, optional context builder.
2. Add a `fromYaml` factory (usually `section -> new YourTrigger()`).
3. Call `register("your_trigger", factory, YourTrigger.SPEC)` in `EnchantTriggerTypeRegistry`'s static block.
4. If the trigger fires on a **new** Bukkit event class not already in `EnchantEventRouter`, add a one-line `@EventHandler` stub there.
5. No other changes needed — the router discovers and registers the spec automatically at startup.

### Armor trigger (enchant on equipped armor; fires on a Bukkit event; fire-and-forget)

Examples: `on_sprint_start`, `on_jump`, `on_block_break`

Same as weapon trigger but declare `public static final ArmorTriggerSpec<E> SPEC` instead. Dispatch reads from the equipped-armor index (`PlayerEnchantIndex`) rather than the weapon PDC. Use `register("your_trigger", factory, YourTrigger.SPEC)` (the `ArmorTriggerSpec` overload).

### Complex armor trigger (needs end-condition tracking, absorption logic, or per-hit context)

Examples: `on_damage_taken`, `stat_threshold`

These are too intertwined with lifecycle management to fit the generic spec pattern. Handle them in `DamageTakenListener.fires()` / `resolveEndConditions()`, or add a new dedicated listener for a different event.

---

## Adding a new effect type

1. Implement `EnchantEffect` in `effect/`
2. Add a `fromYaml(NamespacedKey, ConfigurationSection)` or `fromYaml(ConfigurationSection)` static factory
3. Register it in `EnchantEffectTypeRegistry`'s static block: `register("your_style", YourEffect::fromYaml)`

---

## Adding a new end condition

1. Implement `EndCondition` in `condition/`
2. Add a case to `EndCondition.fromYaml()` (switch on `condition` string inside `parseUntilCondition`)
3. Add resolution logic in `DamageTakenListener.resolveEndConditions()` (or a new listener if the
   condition fires on a different event)

---

## Adding a new visual layer type

1. Implement `VisualLayer` in `effect/visual/layers/`
2. Register it in `VisualLayerTypeRegistry`'s static block: `register("your_type", YourLayer::fromMap)`

New visual layer types are picked up automatically by any effect that uses a `VisualSystem`.
