# EnchantForge — Claude Reference

## What this is

A Paper 1.21.4 plugin that adds data-driven custom enchantments defined entirely in YAML files.
No code changes are needed to add new enchantments. Each `.yml` file in the `enchants/` folder is
one enchantment. The plugin ships default enchants bundled in the jar and extracts them on first
run via `saveResource`.

**Package:** `com.example.enchantforge`
**Build:** Gradle (`.\gradlew.bat build`), output at `build/libs/EnchantForge-1.0-SNAPSHOT.jar`
**Deploy:** `server\build-EnchantForge.bat` builds and copies the jar into `server\plugins\`
**Reload in-game:** `/cenchant reload` (re-reads all YAML files and config without restart)

---

## Project layout

```
src/main/java/com/example/enchantforge/
  EnchantForge.java              — plugin entry point, wires everything together
  CustomEnchant.java             — the core data object, loaded from YAML
  EnchantmentRegistry.java       — holds all loaded enchants, reads them off ItemStacks
  EnchantDebug.java              — per-enchant debug logging utility (see Debug section)
  CooldownManager.java           — player × enchant → expiry timestamp
  ActiveEffectTracker.java       — player × enchant → effective level (for tracked effects)
  CombatTracker.java             — player → last-hit timestamp (for out-of-combat regen)
  StackBehavior.java             — enum: SUM / HIGHEST / EXCLUSIVE
  EquipmentEnchantListener.java  — handles armor equip/unequip, passive reapply, OOC regen
  DamageTakenListener.java       — handles on_damage_taken triggers and end-condition resolution
  PlayerSessionListener.java     — cleanup on quit, resource pack send on join
  EnchantCatalogListener.java    — right-click catalog item to browse enchants
  EnchantCommand.java            — /cenchant apply | reload | catalog
  ResourcePackManager.java       — generates enchantforge_pack.zip, serves it via HTTP
  trigger/                       — EnchantTrigger and its three subtypes
  effect/                        — EnchantEffect interface and three implementations
  condition/                     — EndCondition interface and five implementations

src/main/resources/
  plugin.yml
  config.yml                     — resource-pack host/port/url/required settings
  config.json                    — empty {"enchantments":[]} legacy stub (kept for saveResource)
  enchants/*.yml                 — bundled default enchantments

server/plugins/EnchantForge/    — live server data folder
  config.yml                     — actual runtime config (host IP, port, etc.)
  enchants/*.yml                 — runtime enchant files (may diverge from source defaults)
```

---

## Enchantment YAML schema

The filename (without extension) is used as the enchantment key unless `key:` is explicitly set.

```yaml
# --- Identity ---
key: optional_key_override      # defaults to filename; must be lowercase snake_case
displayName: "Swift Steps"
maxLevel: 3
debug: false                    # set true to enable per-enchant debug logging

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
  style: absorption             # adds level×4 absorption HP via MAX_ABSORPTION attribute modifier
                                # (AbsorptionEffect.java — golden-apple hearts are preserved on remove)

# --- When the effect ends ---
duration:
  type: never                   # indefinite; effect stays until manually removed (e.g. unequip)

duration:
  type: time
  ticks: 600                    # 20 ticks = 1 second; effect self-expires (potion duration or
                                # scheduler for attributes)

duration:
  type: until_condition
  condition: damaged            # removed on the next hit taken

duration:
  type: until_condition
  condition: stat_threshold     # removed when stat crosses threshold (checked on damage/regen)
  stat: health
  comparison: above
  value: 10

duration:
  type: until_condition
  condition: absorption_depleted  # removed when absorption drops below the enchant's expected amount
                                  # (deferred 1 tick post-damage so vanilla absorption drain completes)

# --- Timing & stacking ---
cooldown: 1200                  # ticks between activations; 0 = no cooldown
stackBehavior: highest          # how multiple pieces with the same enchant combine:
                                #   sum      — add all levels
                                #   highest  — use the max level
                                #   exclusive — use the first piece found only

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

1. `onEnable` → `saveDefaultConfig()` → `saveDefaultEnchants()` (extracts bundled YAMLs to data
   folder if not already present; uses `saveResource(..., false)` so existing files are never
   overwritten)
2. `loadEnchantments()` scans `plugins/EnchantForge/enchants/*.yml`
3. Each file is loaded via `YamlConfiguration.loadConfiguration(file)` and passed to
   `CustomEnchant.fromYaml(key, section)`
4. Enchants are registered into `EnchantmentRegistry` (keyed by `NamespacedKey`)
5. `EnchantmentRegistry.getEnchants(ItemStack)` reads `PersistentDataContainer` keys on the item
   to find which enchants are applied and at what level

`/cenchant reload` clears all active effects and cooldowns for online players, then re-runs
`loadEnchantments()` and reapplies on-equip effects.

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

### Cooldowns
`CooldownManager` stores `expiry = currentTimeMs + (cooldownTicks × 50)`. A cooldown on an
on_equip+DamagedCondition enchant is set for ALL armor pieces on any hit (not just equipped ones)
to prevent mid-combat equip exploits.

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
false — no string allocation, no overhead. The lazy-init `Set` in the damage handler prevents
duplicate skip messages when the same enchant appears on multiple armor pieces.

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

When `host` is set, the plugin starts a built-in HTTP server on `port` and auto-sets the URL to
`http://<host>:<port>/enchantforge_pack.zip`. The pack is sent to players on join via
`PlayerSessionListener`. The HTTP server is stopped cleanly in `onDisable`.

---

## Adding a new enchantment

1. Create `EnchantForge/src/main/resources/enchants/<key>.yml` using the schema above
2. Add `"<key>.yml"` to the `saveDefaultEnchants()` list in `EnchantForge.java`
3. Build and deploy
4. The file is extracted to `server/plugins/EnchantForge/enchants/` on startup if not present

If you only need the enchant on the live server without bundling it, drop the YAML directly into
`server/plugins/EnchantForge/enchants/` and run `/cenchant reload`.

---

## Adding a new trigger type

1. Create a class extending `EnchantTrigger` in `trigger/`
2. Add a `fromYaml(ConfigurationSection)` static factory
3. Register it in `EnchantTrigger.fromYaml()`
4. Handle it in `DamageTakenListener.fires()` or `EquipmentEnchantListener` as appropriate

## Adding a new effect type

1. Implement `EnchantEffect` in `effect/`
2. Add a `fromYaml(NamespacedKey, ConfigurationSection)` static factory
3. Register it in `EnchantEffect.fromYaml()`

## Adding a new end condition

1. Implement `EndCondition` in `condition/`
2. Register it in `EndCondition.fromYaml()`
3. Add resolution logic in `DamageTakenListener.resolveEndConditions()` (or a new listener if the
   condition fires on a different event)
