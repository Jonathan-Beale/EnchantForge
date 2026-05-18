# EnchantForge Refactor Plan — Four Generalization Patterns

## Overview

Four classes/utilities to implement, followed by updates to their consumers and deletion of superseded files.

---

## Checklist

**Phase 1 — Generalization**
- [x] [§1 `EnchantEffectContext`](#1-enchanteffectcontext--extensible-key-value-context) — extensible key-value context; add `TRIGGER_ID` key
- [x] [§2 `PlayerLifecycleRegistry`](#2-playerlifecycleregistry--declarative-session-cleanup) — declarative lifecycle cleanup; delete `PlayerSessionListener`
- [x] [§3 `StackingDispatcher`](#3-stackingdispatcher--collect-stack-dispatch-utility) — DRY collect-stack-dispatch; add `collectFromArmorSlots`; collapse double-dispatch with `NONE`
- [x] [§4 `PlayerResourcePool`](#4-playerresourcepool--generalized-lazy-regen-resource) — replace `EnergyManager` singleton; delete `EnergyManager`

**Phase 2 — Correctness**
- [x] [§5 Cooldown persistence](#5-cooldown-persistence--survive-reloadrestart) — serialize to `cooldowns.yml` on disable/reload
- [x] [§6 YAML error isolation](#6-yaml-load-error-isolation) — per-file try-catch in `loadEnchantments()`

**Phase 3 — Tests**
- [x] [§7 Test coverage](#7-test-coverage--core-logic) — `StackBehaviorTest`, `CooldownManagerTest`, `EnchantEffectContextTest`

**Phase 4 — Cleanup**
- [x] [§8 Remove `config.json`](#8-configjson--remove-dead-code) — delete legacy stub and its `saveResource` call
- [x] [§9 Resource pack hash-check](#9-resource-pack--skip-redundant-regeneration) — skip regeneration when pack is unchanged
- [ ] [§10 Registry singletons *(optional)*](#10-registry-singletons--instance-wiring-low-priority) — constructor-inject `EnchantTriggerTypeRegistry`, `EnchantEffectTypeRegistry`
- [ ] [§11 `StackBehavior.AVERAGE` *(optional)*](#11-stackbehavior--add-average-low-priority) — add averaging stack mode

**Phase 5 — Bug Fixes**
- [x] [§12 `StatThreshold` attribute lookup](#12-statthresholdtrigger--statthresholdcondition--broken-attribute-lookup) — replace broken reflection with `Registry.ATTRIBUTE`; log on failure
- [x] [§13 `StatThreshold` null YAML fields](#13-statthresholdtrigger--statthresholdcondition--null-fields-from-yaml) — validate `stat` and `comparison` at load time
- [x] [§14 Inverted comparison default](#14-statthresholdtrigger-vs-statthresholdcondition--inverted-comparison-default) — align default and validate at load time

**Phase 6 — Hardening**
- [x] [§15 `WolfFormEffect` null check](#15-wolfformeffect--missing-plugin-null-check) — guard `apply()` against pre-`init()` call
- [x] [§16 Scoreboard team duplication](#16-wolfformeffect--morphformeffect--scoreboard-team-code-duplication) — extract shared `ScoreboardTeamUtil`
- [x] [§17 `CooldownVisuals` reflection](#17-cooldownvisuals--unnecessary-reflection-for-paper-1214) — replace with direct call; remove fallback machinery
- [x] [§18 `CooldownVisuals` `synchronized`](#18-cooldownvisuals--misleading-synchronized) — remove misleading lock; document main-thread assumption

**Phase 7 — Minor Cleanup**
- [x] [§19 `DamageTakenListener` array hack](#19-damagetakenlistener--array-hack-mutable-box) — replace `Set[]` box with `AtomicReference`

**Phase 8 — UI Screens**
- [x] [§20 Enchantment Catalog](#20-enchantment-catalog--already-present) — technical browse screen via `VibeCraftUiBridge.openEnchantCatalog()`; present
- [x] [§21 Product Guide](#21-product-guide--player-facing-capability-overview) — player-facing capability overview; plain language, no YAML keys or tick values
- [x] [§22 AI Manual](#22-ai-manual--in-game-technical-reference) — in-game technical reference with file, class, and method pointers for AI consultation
- [x] [§23 Mod recommendation on join](#23-mod-recommendation-on-join) — detect missing VibeCraftMod on connect; send clickable acquisition message

---

## 1. `EnchantEffectContext` — Extensible Key-Value Context

**File:** `src/main/java/com/example/enchantforge/effect/EnchantEffectContext.java`

**Problem:** Current `record EnchantEffectContext(String triggerType, double dealtDamage)` is fixed-schema. Adding new context fields (e.g., source entity, critical hit flag) requires touching every call site.

**New design:** Replace the record with a final class backed by an unmodifiable `Map<String, Object>`, accessed via typed `Key<T>` descriptors.

```java
public final class EnchantEffectContext {

    public record Key<T>(String id, Class<T> type) {}

    public static final Key<Double> DEALT_DAMAGE = new Key<>("dealt_damage", Double.class);
    public static final EnchantEffectContext NONE = new EnchantEffectContext(Map.of());

    private final Map<String, Object> data;

    // --- Accessors ---
    public <T> T get(Key<T> key)                         // null if absent
    public <T> T getOrDefault(Key<T> key, T defaultValue)
    public <T> boolean has(Key<T> key)

    // --- Convenience (backward compat) ---
    public boolean hasDealtDamage()  // get(DEALT_DAMAGE) != null && > 0
    public double dealtDamage()      // getOrDefault(DEALT_DAMAGE, 0.0)

    // --- Factories ---
    public static EnchantEffectContext fromDealDamage(double damage)
    public static Builder builder()

    public static final class Builder {
        public <T> Builder set(Key<T> key, T value)
        public EnchantEffectContext build()
    }
}
```

**What changes:**
- `triggerType` field removed (was never read externally).
- `NONE` kept as `new EnchantEffectContext(Map.of())` — still used by `CustomEnchant.apply(Player, int)`.
- `fromDealDamage()` delegates to `builder().set(DEALT_DAMAGE, ...).build()`.
- All call sites in `EnchantEventRouter` (`ctx.hasDealtDamage()`, `ctx.dealtDamage()`) remain unchanged.

**Adding a new context field in future:**
```java
// Declare once:
public static final Key<Boolean> CRITICAL_HIT = new Key<>("critical_hit", Boolean.class);

// Set in trigger spec contextBuilder:
EnchantEffectContext.builder()
    .set(DEALT_DAMAGE, e.getFinalDamage())
    .set(CRITICAL_HIT, isCrit(e))
    .build()

// Read in effect:
boolean crit = context.getOrDefault(EnchantEffectContext.CRITICAL_HIT, false);
```

**Pre-declare `TRIGGER_ID` while rewriting this class:**
```java
public static final Key<String> TRIGGER_ID = new Key<>("trigger_id", String.class);
```
Set it in every `contextBuilder` at no extra cost. Effects ignore it unless they need to branch on which trigger fired (e.g., `HealEffect` behaving differently on `on_deal_damage` vs `on_kill_entity`). Costs nothing now; avoids a future breaking change.

**Downstream simplification (§3):** Once `NONE` exists, the double-dispatch in `EnchantEventRouter` collapses — see §3 for details.

---

## 2. `PlayerLifecycleRegistry` — Declarative Session Cleanup

**File:** `src/main/java/com/example/enchantforge/PlayerLifecycleRegistry.java`

**Problem:** `PlayerSessionListener` is a monolithic listener that hard-codes every manager's cleanup call. Adding a new manager (e.g., `PlayerResourcePool`) means editing `PlayerSessionListener` directly — tight coupling.

**New design:** A `Listener` that holds ordered lists of `Consumer<Player>` handlers, wired at startup.

```java
public class PlayerLifecycleRegistry implements Listener {

    private final List<Consumer<Player>> joinHandlers = new ArrayList<>();
    private final List<Consumer<Player>> quitHandlers = new ArrayList<>();

    public PlayerLifecycleRegistry onJoin(Consumer<Player> handler) { ... return this; }
    public PlayerLifecycleRegistry onQuit(Consumer<Player> handler) { ... return this; }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) { joinHandlers.forEach(h -> h.accept(event.getPlayer())); }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) { quitHandlers.forEach(h -> h.accept(event.getPlayer())); }
}
```

**`EnchantForge.onEnable()` wiring:**
```java
PlayerLifecycleRegistry lifecycle = new PlayerLifecycleRegistry();
lifecycle.onJoin(resourcePackManager::sendPackTo);
lifecycle.onQuit(p -> {
    UUID id = p.getUniqueId();
    cooldowns.clearPlayer(id);
    tracker.clearPlayer(id);
    combatTracker.clearPlayer(id);
    enchantIndex.clearPlayer(id);
    energy.cleanup(id);
});
getServer().getPluginManager().registerEvents(lifecycle, this);
```

**Delete:** `PlayerSessionListener.java` — fully replaced.

---

## 3. `StackingDispatcher` — Collect-Stack-Dispatch Utility

**File:** `src/main/java/com/example/enchantforge/StackingDispatcher.java`

**Problem:** The three-phase pattern (collect → stack → dispatch) is duplicated in `EnchantEventRouter.dispatchWeapon()`, `dispatchArmor()`, and `RightClickListener.onRightClick()`. Each collision follows the same `LinkedHashMap<NamespacedKey, List<Integer>> + forEach` structure.

**New design:** Static utility with three reusable methods:

```java
public final class StackingDispatcher {
    private StackingDispatcher() {}

    /** Factory for the accumulation map (preserves insertion order). */
    public static Map<NamespacedKey, List<Integer>> newMap()

    /**
     * Adds enchants from an item's PDC that match triggerId and are not on cooldown.
     * Uses item-scoped cooldown (isOnCooldown(player, enchant, item)).
     */
    public static void collectFromItem(
            Map<NamespacedKey, List<Integer>> out,
            Map<CustomEnchant, Integer> enchants,
            String triggerId,
            Player player,
            CooldownManager cooldowns,
            ItemStack item)

    /**
     * Adds enchants from an equipped-armor index list that are not on cooldown.
     * Uses player-scoped cooldown (isOnCooldown(player, enchant)).
     */
    public static void collectFromIndex(
            Map<NamespacedKey, List<Integer>> out,
            List<PlayerEnchantIndex.SlottedEnchant> equipped,
            Player player,
            CooldownManager cooldowns)

    /**
     * Resolves stacking for each collected key and dispatches.
     * Calls apply(enchant, effectiveLevel), then onCooldown(enchant) if enchant.hasCooldown().
     */
    public static void dispatch(
            Map<NamespacedKey, List<Integer>> collected,
            EnchantmentRegistry registry,
            BiConsumer<CustomEnchant, Integer> apply,
            Consumer<CustomEnchant> onCooldown)
}
```

**Usage in `EnchantEventRouter.dispatchWeapon()`:**
```java
Map<NamespacedKey, List<Integer>> triggered = StackingDispatcher.newMap();
StackingDispatcher.collectFromItem(triggered, registry.getEnchants(weapon), spec.triggerId(), player, cooldowns, weapon);
if (triggered.isEmpty()) return;

EnchantEffectContext ctx = spec.contextBuilder() != null ? spec.contextBuilder().apply(event) : null;
StackingDispatcher.dispatch(triggered, registry, (enchant, effectiveLevel) -> {
    if (ctx != null) enchant.apply(player, effectiveLevel, ctx);
    else enchant.apply(player, effectiveLevel);
    String msg = spec.triggerId() + " triggered lv" + effectiveLevel;
    if (ctx != null && ctx.hasDealtDamage())
        msg += " (+" + String.format("%.1f", ctx.dealtDamage()) + " dmg)";
    EnchantDebug.log(enchant, player, msg);
}, enchant -> {
    cooldowns.setCooldown(player, enchant, weapon);
    CooldownVisuals.applyMainHandCooldown(player, enchant.getCooldownTicks());
    EnchantDebug.log(enchant, player, "cooldown started (" + (enchant.getCooldownTicks() / 20) + "s)");
});
```

**Usage in `EnchantEventRouter.dispatchArmor()`:**
```java
Map<NamespacedKey, List<Integer>> triggered = StackingDispatcher.newMap();
StackingDispatcher.collectFromIndex(triggered, equipped, player, cooldowns);
if (triggered.isEmpty()) return;

EnchantEffectContext ctx = spec.contextBuilder() != null ? spec.contextBuilder().apply(event) : null;
StackingDispatcher.dispatch(triggered, registry, (enchant, effectiveLevel) -> {
    if (ctx != null) enchant.apply(player, effectiveLevel, ctx);
    else enchant.apply(player, effectiveLevel);
    EnchantDebug.log(enchant, player, spec.triggerId() + " triggered lv" + effectiveLevel);
}, enchant -> {
    cooldowns.setCooldown(player, enchant);
    EnchantDebug.log(enchant, player, "cooldown started (" + (enchant.getCooldownTicks() / 20) + "s)");
});
```

**Usage in `RightClickListener.onRightClick()` (collection phase only):**
```java
Map<NamespacedKey, List<Integer>> triggered = StackingDispatcher.newMap();

if (mainHand.getType() != Material.AIR)
    StackingDispatcher.collectFromItem(triggered, registry.getEnchants(mainHand), "on_right_click", player, cooldowns, mainHand);

// Armor slot loop stays inline — uses item-scoped cooldown with null/AIR guard
for (PlayerEnchantIndex.SlottedEnchant se : enchantIndex.getByTrigger(player.getUniqueId(), "on_right_click")) {
    ItemStack armorItem = player.getInventory().getItem(se.slot());
    if (armorItem == null || armorItem.getType().isAir()) continue;
    if (cooldowns.isOnCooldown(player, se.enchant(), armorItem)) continue;
    triggered.computeIfAbsent(se.enchant().getKey(), k -> new ArrayList<>()).add(se.level());
}

if (offHand.getType() != Material.AIR)
    StackingDispatcher.collectFromItem(triggered, registry.getEnchants(offHand), "on_right_click", player, cooldowns, offHand);
```

The `RightClickListener` dispatch phase stays inline (complex cooldown-visual source resolution).

**RightClickListener armor-loop gap:** The inline armor-slot loop in `onRightClick()` (item-scoped cooldown + null/AIR guard) diverges from the weapon path and must be maintained separately. Follow-up: add a `collectFromArmorSlots` overload that accepts a `PlayerEnchantIndex` and encapsulates the null/AIR guard, letting `RightClickListener` call it the same way `dispatchWeapon` calls `collectFromItem`.

**Double-dispatch collapse (depends on §1):** Once `EnchantEffectContext.NONE` exists, the null-check split in `dispatchWeapon` and `dispatchArmor` collapses to a single call path:
```java
// Before (two branches):
if (ctx != null) enchant.apply(player, effectiveLevel, ctx);
else enchant.apply(player, effectiveLevel);

// After (NONE is a valid context; apply() handles it):
EnchantEffectContext ctx = spec.contextBuilder() != null
    ? spec.contextBuilder().apply(event)
    : EnchantEffectContext.NONE;
enchant.apply(player, effectiveLevel, ctx);
```
This also eliminates the `CustomEnchant.apply(Player, int)` overload or reduces it to a `apply(p, lvl, NONE)` delegate.

**Imports to remove from `EnchantEventRouter`:** `java.util.ArrayList`, `java.util.LinkedHashMap`
**Imports to remove from `RightClickListener`:** `java.util.LinkedHashMap`

---

## 4. `PlayerResourcePool` — Generalized Lazy-Regen Resource

**File:** `src/main/java/com/example/enchantforge/effect/PlayerResourcePool.java`

**Problem:** `EnergyManager` is a static singleton locked to a single 100/0.4 energy pool. Adding a second resource type (shields, mana, heat, etc.) would require duplicating the entire class.

**New design:** Instance class; same lazy `computeRegen` algorithm, parameterized at construction.

```java
public final class PlayerResourcePool {

    private static final long MS_PER_TICK = 50L;

    private final double max;
    private final double regenPerTick;
    private final Map<UUID, Double> pool = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastRegenAt = new ConcurrentHashMap<>();
    private Consumer<Player> onChanged = p -> {};

    public PlayerResourcePool(double max, double regenPerTick)

    public double getMax()
    public void onChanged(Consumer<Player> callback)
    public double get(Player player)
    public boolean tryConsume(Player player, double cost)
    public void cleanup(UUID id)

    private double computeRegen(UUID id)  // identical algorithm to EnergyManager.computeRegen
}
```

**`SuitListener` constructor change:**
```java
// Before:
public SuitListener(EnchantmentRegistry registry, Plugin plugin, PlayerEnchantIndex enchantIndex)

// After:
public SuitListener(Plugin plugin, PlayerEnchantIndex enchantIndex, PlayerResourcePool energy)
```

- Remove `EnchantmentRegistry registry` param (was accepted but never stored or used).
- Add `private final PlayerResourcePool energy` field.
- Replace all `EnergyManager.X(...)` calls: `EnergyManager.get(p)` → `energy.get(p)`, `EnergyManager.MAX` → `energy.getMax()`, `EnergyManager.tryConsume(p, n)` → `energy.tryConsume(p, n)`, `EnergyManager.registerCallback(...)` → `energy.onChanged(...)`.

**`EnchantForge` changes:**
- Add `private PlayerResourcePool energy;` field.
- In `onEnable()`:
  ```java
  energy = new PlayerResourcePool(100.0, 0.4);
  ```
- Change `SuitListener` instantiation:
  ```java
  // Before: new SuitListener(registry, this, enchantIndex)
  // After:  new SuitListener(this, enchantIndex, energy)
  ```
- In `reload()`, add `energy.cleanup(player.getUniqueId());` inside the online-player loop.
- Imports: add `com.example.enchantforge.effect.PlayerResourcePool`, `java.util.UUID`.

**Delete:** `effect/EnergyManager.java` — fully replaced by `PlayerResourcePool`.

---

## 5. Cooldown Persistence — Survive Reload/Restart

**Problem:** `CooldownManager` is in-memory only. `/cenchant reload` or a server restart resets all cooldowns silently. On a long-cooldown enchant (e.g., `last_stand` at 2400 ticks = 2 minutes), players can force a reset by relogging or triggering a reload.

**Fix:** On plugin disable and on `/cenchant reload` (before clearing state), serialize all non-expired entries to `plugins/EnchantForge/cooldowns.yml` as `player-uuid.enchant-key → epoch-ms expiry`. On enable, read them back and skip expired entries.

**Scope:** Changes only `CooldownManager.java` (add `save(File)` / `load(File)` methods) and `EnchantForge.java` (call at shutdown/reload). No changes to callers.

**Note:** Item-scoped cooldowns (keyed by `player + enchant + item-id`) should also be persisted; the item-id is already a stable PDC value so the key survives restarts.

---

## 6. YAML Load Error Isolation

**Problem:** `loadEnchantments()` calls `CustomEnchant.fromYaml()` in a loop. An unchecked exception from one malformed file aborts the entire load, leaving the registry partially populated with no indication of which file failed.

**Fix:** Wrap each file's load call in a try-catch:
```java
for (File file : enchantFiles) {
    try {
        CustomEnchant enchant = CustomEnchant.fromYaml(key, section);
        registry.register(enchant);
    } catch (Exception e) {
        getLogger().severe("Failed to load enchant from " + file.getName() + ": " + e.getMessage());
    }
}
```
Log the filename and message; continue to the next file. No good enchant should be skipped because its neighbor is broken.

**Scope:** `EnchantForge.loadEnchantments()` only.

---

## 7. Test Coverage — Core Logic

**Problem:** Two shallow tests exist. The most brittle logic — stacking resolution, cooldown scoping, and (after §1) the typed-key context — has no coverage.

**Priority test classes to add:**

**`StackBehaviorTest`** (pure logic, zero mocks):
- `SUM` of levels [1, 2, 1] → 4
- `HIGHEST` of levels [1, 3, 2] → 3
- `EXCLUSIVE` of levels [2, 1] → 2 (first found)

**`CooldownManagerTest`** (Mockito for Player/ItemStack):
- Item-scoped and global cooldowns do not bleed into each other for the same player + enchant pair
- `isOnCooldown` returns false after expiry time has passed
- `clearPlayer` removes all entries for that UUID

**`EnchantEffectContextTest`** (pure logic, no mocks — add after §1 is implemented):
- `get(key)` returns null when absent; `getOrDefault` returns supplied default
- `has(key)` reflects presence correctly
- `NONE` has no keys; `hasDealtDamage()` returns false
- `fromDealDamage(x).dealtDamage()` round-trips correctly

**Scope:** New files in `src/test/java/com/example/enchantforge/`.

---

## 8. `config.json` — Remove Dead Code

**Problem:** `src/main/resources/config.json` is an empty `{"enchantments":[]}` legacy stub kept only for a historical `saveResource` call. It adds confusion (two config files, one unused).

**Fix:** Delete `config.json` from resources and remove its `saveResource` call from `EnchantForge.java`.

**Scope:** Delete one file; remove one line from `EnchantForge.java`.

---

## 9. Resource Pack — Skip Redundant Regeneration

**Problem:** `ResourcePackManager` regenerates and re-serves `enchantforge_pack.zip` on every startup, even though the PNG sprites are static and never change.

**Fix:** After generating the zip bytes, compute a SHA-256 hash and compare against a stored hash file (`plugins/EnchantForge/pack.sha256`). Skip write + HTTP server restart if the hash matches.

**Scope:** `ResourcePackManager.java` only.

---

## 10. Registry Singletons — Instance Wiring (Low Priority)

**Problem:** `EnchantTriggerTypeRegistry` and `EnchantEffectTypeRegistry` remain as static singletons after `EnergyManager` is replaced. They hold no per-player state, so this is low urgency — but it leaves two static singletons in a codebase otherwise moving toward constructor injection.

**Fix:** Convert both to instances, construct them in `EnchantForge.onEnable()`, and pass them where needed (currently only `CustomEnchant.fromYaml` and `EnchantTriggerTypeRegistry` lookups in the router/listeners).

---

## 11. `StackBehavior` — Add `AVERAGE` (Low Priority)

**Problem:** Some enchant designs (e.g., a speed modifier where wearing more pieces should not stack additively) want the average of equipped levels rather than sum or max. There is currently no way to express this in YAML.

**Fix:** Add `AVERAGE` to the `StackBehavior` enum; handle it alongside `SUM`/`HIGHEST`/`EXCLUSIVE` in the stacking resolution in `StackingDispatcher.dispatch()` (after §3) or the current inline stacking code.

---

## 12. `StatThresholdTrigger` / `StatThresholdCondition` — Broken Attribute Lookup

**Files:** `trigger/StatThresholdTrigger.java`, `condition/StatThresholdCondition.java`

**Problem:** Both files resolve non-`health` stats via reflection:
```java
Attribute attr = (Attribute) Attribute.class.getField(stat.toUpperCase()).get(null);
```
In Paper 1.21.4, `Attribute` is a registry-backed interface, not an enum. Field names on the interface were also renamed in 1.21 (e.g. `GENERIC_MOVEMENT_SPEED` → `MOVEMENT_SPEED`). The reflection throws `NoSuchFieldException` for valid attribute names and swallows it silently, returning `0`. Any `stat_threshold` enchant referencing a non-`health` stat either never fires or its end condition never clears.

The same problem exists in `AttributeStyleEffect.resolveAttribute()`, which uses identical reflection but at least throws on failure rather than silently returning `0`.

**Fix:** Use the same registry pattern already used in `MorphFormEffect.requireEffectType()`:
```java
// Before:
Attribute attr = (Attribute) Attribute.class.getField(stat.toUpperCase()).get(null);

// After:
Attribute attr = Registry.ATTRIBUTE.get(NamespacedKey.minecraft(stat.toLowerCase()));
if (attr == null) {
    plugin.getLogger().warning("Unknown attribute '" + stat + "' in stat_threshold enchant");
    return 0;
}
```
Apply the same fix to `AttributeStyleEffect.resolveAttribute()` to eliminate the reflection there too.

**Scope:** `trigger/StatThresholdTrigger.java`, `condition/StatThresholdCondition.java`, `effect/AttributeStyleEffect.java`.

---

## 13. `StatThresholdTrigger` / `StatThresholdCondition` — Null Fields from YAML

**Files:** `trigger/StatThresholdTrigger.java`, `condition/StatThresholdCondition.java`

**Problem:** Both `fromYaml` methods pass `section.getString("stat")` and `section.getString("comparison")` directly to the constructor without null checks. If the YAML omits either key, the null is stored silently. The NPE surfaces later at runtime (`stat.toLowerCase()`, `comparison.equals(...)`) during combat, not at load time, with no indication of which enchant file is responsible.

**Fix:** Validate in `fromYaml` before constructing:
```java
String stat = section.getString("stat");
String comparison = section.getString("comparison");
if (stat == null || stat.isBlank())
    throw new IllegalArgumentException("stat_threshold trigger missing required field 'stat'");
if (comparison == null || comparison.isBlank())
    throw new IllegalArgumentException("stat_threshold trigger missing required field 'comparison'");
```
This integrates cleanly with the §6 per-file try-catch, which will log the file name.

**Scope:** `trigger/StatThresholdTrigger.java`, `condition/StatThresholdCondition.java`.

---

## 14. `StatThresholdTrigger` vs `StatThresholdCondition` — Inverted Comparison Default

**Files:** `trigger/StatThresholdTrigger.java`, `condition/StatThresholdCondition.java`

**Problem:** The two `matches()` methods handle unknown comparison strings in opposite ways:
```java
// StatThresholdTrigger.matches — unknown defaults to "above" (statValue > value)
return comparison.equals("below") ? statValue < value : statValue > value;

// StatThresholdCondition.matches — unknown defaults to "below" (statValue < value)
return comparison.equals("above") ? statValue > value : statValue < value;
```
An invalid YAML value like `comparison: sideways` would cause the trigger to fire as "above" but the end condition to clear as "below". On a `last_stand`-style enchant this means the effect activates and never turns off. After §13 adds load-time validation, invalid values are caught early, but the defaults should still be made consistent.

**Fix:** Replace the ternary default with an explicit `switch` (or `if/else if/else throw`) that rejects unknown values:
```java
return switch (comparison) {
    case "below" -> statValue < value;
    case "above" -> statValue > value;
    default -> throw new IllegalStateException("Invalid comparison: " + comparison);
};
```
Apply identically to both classes.

**Scope:** `trigger/StatThresholdTrigger.java`, `condition/StatThresholdCondition.java`.

---

## 15. `WolfFormEffect` — Missing `plugin` Null Check

**File:** `effect/WolfFormEffect.java`

**Problem:** `WolfFormEffect.apply()` dereferences the static `plugin` field directly at the scheduler call (line 78) without checking for null. `MorphFormEffect.apply()` guards against this with `if (plugin == null) throw new IllegalStateException("MorphFormEffect not initialized")` — `WolfFormEffect` does not.

**Fix:** Add the same guard at the top of `WolfFormEffect.apply()`:
```java
if (plugin == null) throw new IllegalStateException("WolfFormEffect not initialized — call init() first");
```

**Scope:** `effect/WolfFormEffect.java`, one line.

---

## 16. `WolfFormEffect` / `MorphFormEffect` — Scoreboard Team Code Duplication

**Files:** `effect/WolfFormEffect.java`, `effect/MorphFormEffect.java`

**Problem:** Both files contain near-identical `addNoCollideEntries` / `removeNoCollideEntries` methods against separate hardcoded team names (`"ef_wolf_nc"`, `"ef_morph_nc"`). A bug fix in one requires a manual identical fix in the other. The methods differ only in team name.

**Fix:** Extract a shared utility:
```java
// effect/ScoreboardTeamUtil.java
public final class ScoreboardTeamUtil {
    private ScoreboardTeamUtil() {}

    public static void addNeverCollide(String teamName, String... entries) {
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        Team team = board.getTeam(teamName);
        if (team == null) {
            team = board.registerNewTeam(teamName);
            team.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);
        }
        for (String entry : entries) if (entry != null) team.addEntry(entry);
    }

    public static void removeNeverCollide(String teamName, String... entries) {
        Team team = Bukkit.getScoreboardManager().getMainScoreboard().getTeam(teamName);
        if (team == null) return;
        for (String entry : entries) if (entry != null) team.removeEntry(entry);
    }
}
```
Both `WolfFormEffect` and `MorphFormEffect` delegate to this utility, keeping their own team name constants.

**Scope:** Create `effect/ScoreboardTeamUtil.java`; edit `WolfFormEffect.java` and `MorphFormEffect.java`.

---

## 17. `CooldownVisuals` — Unnecessary Reflection for Paper 1.21.4

**File:** `CooldownVisuals.java`

**Problem:** `resolveSetCooldownItem()` uses reflection to probe for the `Player.setCooldown(ItemStack, int)` API, with a material-based fallback for servers that lack it. Since `build.gradle.kts` compiles against Paper 1.21.4 and the plugin declares `api-version: 1.21` in `plugin.yml`, this API is always present at runtime. The reflection machinery — `setCooldownItemMethod`, `lookedUp`, `Method.invoke`, the `ReflectiveOperationException` catch path, and the entire material-fallback branch — exists solely for compatibility with servers that will never run this plugin.

**Fix:** Delete `resolveSetCooldownItem()`, `setCooldownItemMethod`, `lookedUp`, `isMaterialFallbackEnabled()`, `trackMaterialFallback()`, `refreshHeldItemVisual()`, `isTracked()`, `warnPerItemOnlyMode()`, and `warnedPerItemOnly`. Replace `applyPerItemApi()` with a direct call:
```java
public static void applyItemCooldown(Player player, ItemStack item, int ticks) {
    if (player == null || item == null || item.getType().isAir() || ticks <= 0) return;
    ensurePerItemCooldownGroup(player, item, false);
    player.setCooldown(item, ticks);
}
```
The `TRACKED` map, the two event handlers (`onItemHeld`, `onSwapHands`), and the `TrackedCooldown` record can all be deleted. `onQuit` can also be removed if `TRACKED` is gone.

**Scope:** `CooldownVisuals.java` — significant reduction in size.

---

## 18. `CooldownVisuals` — Misleading `synchronized`

**File:** `CooldownVisuals.java`

**Note:** This item becomes moot if §17 is implemented (the `synchronized` method and the `TRACKED` map it guards are both deleted). Document here for completeness in case §17 is deferred.

**Problem:** `trackMaterialFallback` is declared `synchronized`, implying thread-safety intent. But `refreshHeldItemVisual`, `isTracked`, and `onQuit` all read and mutate `TRACKED` without any lock. All callers run on the main server thread, so there is no actual concurrency — the `synchronized` is misleading overhead that suggests a guarantee it does not provide.

**Fix:** Remove `synchronized` from `trackMaterialFallback`; add a comment noting that all access is main-thread-only:
```java
// All callers run on the main server thread; no synchronization needed.
private static void trackMaterialFallback(Player player, ItemStack item, int ticks, boolean persistToMainHand) {
```

**Scope:** `CooldownVisuals.java`, one keyword removal.

---

## 19. `DamageTakenListener` — Array-Hack Mutable Box

**File:** `DamageTakenListener.java`

**Problem:** The debug deduplication uses an unchecked generic array as a mutable lambda-capture box:
```java
Set<NamespacedKey>[] loggedSkip = new Set[]{null};
// ... inside loop:
if (loggedSkip[0] == null) loggedSkip[0] = new HashSet<>();
```
This pattern is surprising, generates an unchecked-cast compiler warning, and obscures intent.

**Fix:** Replace with `AtomicReference`, which is the idiomatic Java mutable box:
```java
AtomicReference<Set<NamespacedKey>> loggedSkip = new AtomicReference<>();
// ... inside loop:
if (loggedSkip.get() == null) loggedSkip.set(new HashSet<>());
if (loggedSkip.get().add(enchant.getKey())) ...
```

**Scope:** `DamageTakenListener.java`, one variable declaration and a handful of access sites.

---

## Implementation Order

Generalization refactors first (§1–4), then correctness gaps (§5–6), then tests (§7), then cleanup (§8–11), then bug fixes (§12–14), then hardening (§15–18), then minor cleanup (§19).

**Phase 1 — Generalization (§1–4, original plan)**
1. Write `effect/PlayerResourcePool.java` (new)
2. Write `PlayerLifecycleRegistry.java` (new)
3. Write `StackingDispatcher.java` (new) — include `collectFromArmorSlots` overload (§3 follow-up)
4. Rewrite `effect/EnchantEffectContext.java` — include `TRIGGER_ID` key and `NONE` (§1)
5. Edit `SuitListener.java`
6. Edit `EnchantForge.java`
7. Edit `EnchantEventRouter.java` — collapse double-dispatch to single path using `NONE` (§3)
8. Edit `RightClickListener.java` — use `collectFromArmorSlots` (§3 follow-up)
9. Delete `effect/EnergyManager.java`
10. Delete `PlayerSessionListener.java`

**Phase 2 — Correctness (§5–6)**
11. Add `save()`/`load()` to `CooldownManager.java`; wire in `EnchantForge.java` (§5)
12. Wrap per-file load in try-catch in `EnchantForge.loadEnchantments()` (§6)

**Phase 3 — Tests (§7)**
13. Add `StackBehaviorTest.java`
14. Add `CooldownManagerTest.java`
15. Add `EnchantEffectContextTest.java` (after Phase 1 complete)

**Phase 4 — Cleanup (§8–9, low-priority §10–11 at discretion)**
16. Delete `config.json`; remove its `saveResource` call (§8)
17. Add hash-check to `ResourcePackManager.java` (§9)
18. *(Optional)* Convert registry singletons to instances (§10)
19. *(Optional)* Add `AVERAGE` to `StackBehavior` (§11)

**Phase 5 — Bug Fixes (§12–14)**
20. Fix attribute lookup in `StatThresholdTrigger`, `StatThresholdCondition`, `AttributeStyleEffect` — replace reflection with `Registry.ATTRIBUTE` (§12)
21. Add null validation for `stat` / `comparison` in both `fromYaml` methods (§13)
22. Replace ternary comparison default with explicit `switch` in both `matches` methods (§14)

**Phase 6 — Hardening (§15–18)**
23. Add `plugin == null` guard to `WolfFormEffect.apply()` (§15)
24. Create `effect/ScoreboardTeamUtil.java`; replace duplicate code in `WolfFormEffect` and `MorphFormEffect` (§16)
25. Remove reflection machinery from `CooldownVisuals`; replace with direct `player.setCooldown(item, ticks)` call (§17)
26. Remove `synchronized` from `trackMaterialFallback` (§18, skip if §17 done)

**Phase 7 — Minor Cleanup (§19)**
27. Replace `Set<NamespacedKey>[]` array hack with `AtomicReference` in `DamageTakenListener` (§19)

---

## Files Touched Summary

| File | Action | Phase |
|------|--------|-------|
| `effect/PlayerResourcePool.java` | Create | 1 |
| `PlayerLifecycleRegistry.java` | Create | 1 |
| `StackingDispatcher.java` | Create (+ `collectFromArmorSlots`) | 1 |
| `effect/EnchantEffectContext.java` | Rewrite (+ `TRIGGER_ID`, `NONE`) | 1 |
| `SuitListener.java` | Edit (constructor, EnergyManager refs) | 1 |
| `EnchantForge.java` | Edit (field, onEnable, reload, §6 try-catch, §8 saveResource) | 1/2/4 |
| `EnchantEventRouter.java` | Edit (dispatchWeapon, dispatchArmor, NONE collapse) | 1 |
| `RightClickListener.java` | Edit (collectFromArmorSlots, imports) | 1 |
| `effect/EnergyManager.java` | Delete | 1 |
| `PlayerSessionListener.java` | Delete | 1 |
| `CooldownManager.java` | Edit (save/load persistence) | 2 |
| `src/test/.../StackBehaviorTest.java` | Create | 3 |
| `src/test/.../CooldownManagerTest.java` | Create | 3 |
| `src/test/.../EnchantEffectContextTest.java` | Create | 3 |
| `src/main/resources/config.json` | Delete | 4 |
| `ResourcePackManager.java` | Edit (hash-check) | 4 |
| `StackBehavior.java` | Edit (add AVERAGE) | 4 (optional) |
| `trigger/StatThresholdTrigger.java` | Edit (Registry lookup, null validation, comparison switch) | 5 |
| `condition/StatThresholdCondition.java` | Edit (Registry lookup, null validation, comparison switch) | 5 |
| `effect/AttributeStyleEffect.java` | Edit (replace reflection with Registry.ATTRIBUTE) | 5 |
| `effect/WolfFormEffect.java` | Edit (null check, delegate to ScoreboardTeamUtil) | 5/6 |
| `effect/MorphFormEffect.java` | Edit (delegate to ScoreboardTeamUtil) | 6 |
| `effect/ScoreboardTeamUtil.java` | Create | 6 |
| `CooldownVisuals.java` | Edit (remove reflection + fallback machinery) | 6 |
| `DamageTakenListener.java` | Edit (AtomicReference for loggedSkip) | 7 |
| `CustomEnchant.java` | Edit (add `category` field, parse from YAML) | 8 |
| `VibeCraftUiBridge.java` | Edit (add `openProductGuide`, `openAIManual`) | 8 |
| `enchants/*.yml` | Edit (add `category:` field to each) | 8 |

---

## 20. Enchantment Catalog — Already Present

**File:** `VibeCraftUiBridge.java` → `openEnchantCatalog(Player, EnchantmentRegistry)`

The catalog is the existing VibeCraftMod screen opened by right-clicking the catalog item (wired
in `EnchantCatalogListener`) or via `/cenchant catalog`. It generates a schema-driven screen from
all registered enchants: collapsible cards, search bar, tag and item filters.

Each card shows: display name, level range, trigger type ID, applicable items, effect style,
cooldown ticks, duration type, and the YAML description. This is a technical view — appropriate
for server admins and modpack builders, not for general players.

No changes planned for the catalog itself. The Product Guide (§21) is the player-facing
counterpart; the AI Manual (§22) is the developer-facing counterpart.

---

## 21. Product Guide — Player-Facing Capability Overview

**File:** `VibeCraftUiBridge.java` (new method `openProductGuide`)

**Purpose:** A screen that answers "what can my gear do?" without exposing YAML keys, tick values,
trigger type names, or internal identifiers. Intended for players discovering the enchantment
system for the first time or browsing what is available.

**Design principles:**
- Group enchants by `category` (a new YAML field: `offensive`, `defensive`, `mobility`,
  `survival`, `utility`, `transform`) rather than listing them alphabetically
- Convert all technical values to human-readable form before display:
  - Cooldown: `1200 ticks` → `60s cooldown` (or `no cooldown`)
  - Duration: `time/600` → `lasts 30s`; `until_condition/damaged` → `until next hit`; `never` → `permanent`
  - Trigger: `on_equip` → `while equipped`; `on_damage_taken` → `activates when you take damage`;
    `stat_threshold/health/below/6` → `activates below 3 hearts`; `on_right_click` → `right-click to activate`
  - Item slots: `_CHESTPLATE` → `chest armor`; `ARMOR` → `any armor`
- Show the `description` field as the primary content (it is already player-written)
- Do not show: enchant key, trigger type ID, effect style, YAML field names, tick counts

**New YAML field: `category`**

Add to `CustomEnchant.java` and each enchant YAML:
```yaml
category: defensive   # offensive | defensive | mobility | survival | utility | transform
```
Enchants without `category` fall into an `uncategorized` group at the bottom.

**Schema structure (`openProductGuide`):**

```java
public static void openProductGuide(Player player, EnchantmentRegistry registry) {
    // Group enchants by category
    Map<String, List<CustomEnchant>> byCategory = registry.all().stream()
        .collect(Collectors.groupingBy(
            e -> e.getCategory() != null ? e.getCategory() : "uncategorized",
            LinkedHashMap::new, Collectors.toList()
        ));

    // Build widgets: one collapsible section per category
    List<Map<String, Object>> widgets = new ArrayList<>();
    for (var entry : byCategory.entrySet()) {
        widgets.add(buildCategorySection(entry.getKey(), entry.getValue()));
    }

    // Send ui_schema + open_screen
    sendScreen(player, "enchantforge:product_guide", "EnchantForge Guide", widgets);
}
```

Each category section is a `collapsible` containing one card per enchant. Each card shows:
- **Name** (displayName + max level badge)
- **Slot** (plain-English item slot description)
- **How it works** (plain-English trigger + effect description)
- **Duration** (plain-English end condition)
- **Cooldown** (seconds, or omitted if 0)
- **Description** (the `description` field)

**Scope:**
- `CustomEnchant.java` — add `String category` field; parse `section.getString("category")` in
  `fromYaml()`
- `VibeCraftUiBridge.java` — add `openProductGuide()` + helper methods for plain-English
  conversion of trigger/duration/slot; a `formatTrigger(EnchantTrigger)`,
  `formatDuration(EndCondition, int)`, `formatSlot(List<String>)` helper set
- `enchants/*.yml` — add `category:` to each bundled enchant
- `EnchantCatalogListener.java` or `EnchantCommand.java` — wire the new screen to a command or
  item (e.g., `/cenchant guide` or a separate guide item)

---

## 22. AI Manual — In-Game Technical Reference

**File:** `VibeCraftUiBridge.java` (new method `openAIManual`)

**Purpose:** A comprehensive reference screen for the AI (Claude/VibeCraft) to consult when
assisting with EnchantForge development. Contains file paths, class names, method signatures, key
patterns, extension points, and runtime lifecycle — organized so the AI can navigate directly to
the relevant section without re-reading source files.

Unlike CLAUDE.md (which is prose), the manual is structured for in-game browsing and is generated
partly dynamically (the registered trigger/effect/condition types are listed from the live
registry, so the manual stays current as new types are added).

**Screen structure (sections as `collapsible` widgets):**

**§ Architecture**
- Package: `com.example.enchantforge`
- Entry point: `EnchantForge.java` — `onEnable()` wires all managers, listeners, and registries;
  `onDisable()` tears them down; `reload()` clears player state and re-runs `loadEnchantments()`
- Key collaborators passed by constructor injection: `EnchantmentRegistry`, `CooldownManager`,
  `ActiveEffectTracker`, `CombatTracker`, `PlayerEnchantIndex`, `PlayerResourcePool` (§4),
  `VibeCraftUiBridge`

**§ Key Files**
Formatted as a table: file path → one-line role, matching the Files Touched Summary pattern
throughout this document. Covers all files in the project layout section of CLAUDE.md. Includes
`src/` paths so the AI can issue a `Read` tool call directly.

**§ YAML Schema**
The full enchant YAML schema from CLAUDE.md, reproduced verbatim with field names, accepted
values, and inline comments. Includes the `category` field added in §21 and `glintColor` added
in the VibeCraftMod plan (§7).

**§ Trigger Types** *(dynamic — from `EnchantTriggerTypeRegistry`)*
Lists every registered trigger type ID, its class, spec type (`WeaponTriggerSpec` /
`ArmorTriggerSpec`), Bukkit event class, and a one-line description. Generated at screen-open
time from the live registry so new trigger types appear automatically.

**§ Effect Types** *(dynamic — from `EnchantEffectTypeRegistry`)*
Lists every registered effect style ID, its class, and a one-line description. Generated from
the live registry.

**§ End Conditions** *(static — from `EndCondition.fromYaml()` switch)*
Lists each condition type string, the class that handles it, and its `getDisplayLabel()` return
value. Static because the registry is a switch block rather than a keyed map.

**§ Extension Points**
Prose instructions for: adding a trigger type, adding an effect type, adding an end condition —
matching the "Adding a new trigger type" section in CLAUDE.md, including the specific method
names and files to edit.

**§ Runtime Lifecycle**
Sequence: load → equip → trigger → cooldown → end condition → reapply. References the exact
methods involved at each step:
- `loadEnchantments()` → `CustomEnchant.fromYaml()` → `EnchantmentRegistry.register()`
- `EquipmentEnchantListener.applyOnEquip()` → `EnchantmentRegistry.getEnchants(ItemStack)`
- `DamageTakenListener.onDamageTaken()` → `EnchantEventRouter.dispatchArmor()` →
  `StackingDispatcher.dispatch()` → `CustomEnchant.apply()`
- `DamageTakenListener.resolveEndConditions()` → `EndCondition.matches()` →
  `ActiveEffectTracker.clear()` + `CooldownManager.setCooldown()`
- `EquipmentEnchantListener.reapplyTicker` (20-tick) → checks cooldown expired → `applyOnEquip()`

**§ Debug System**
`EnchantDebug.log(enchant, player, msg)` — no-ops when `enchant.isDebug()` is false. Enable per
enchant with `debug: true` in YAML. Enable cooldown diagnostics with `debug.cooldownVisuals: true`
in `config.yml`. Use `/ctestcooldown` to verify item-scoped cooldown behavior on the held item.

**§ VibeCraftMod Integration**
Channel: `vibecraft:events` (plugin messaging). All messages are JSON with a `type` field.
Relevant types sent by EnchantForge: `ui_schema`, `open_screen`, `binding_update`,
`binding_updates`. `VibeCraftUiBridge.java` is the sole integration point — all outbound messages
go through it. References the REFACTOR_PLAN.md in VibeCraftMod for the full list of planned
binding keys (`enchantforge.energy`, `enchantforge.cooldown.*`, etc.).

**Implementation notes:**

The manual is mostly static content assembled in `openAIManual()`. The dynamic sections
(trigger types, effect types) call `EnchantTriggerTypeRegistry.registeredIds()` and
`EnchantEffectTypeRegistry.registeredIds()` — these methods do not currently exist and need to
be added (simple `return Collections.unmodifiableSet(registry.keySet())`).

The screen does not need to be wired to a player-accessible command or item; it is intended to be
opened programmatically by the AI via VibeCraft, not by players navigating menus.

**Scope:**
- `VibeCraftUiBridge.java` — add `openAIManual(Player, EnchantmentRegistry)` + static content
  builders for each section
- `EnchantTriggerTypeRegistry.java` — add `registeredIds()` and `getSpec(String)` accessors
- `EnchantEffectTypeRegistry.java` — add `registeredIds()` accessor
- No YAML changes needed

---

## 23. Mod Recommendation on Join

**Files:** `PlayerSessionListener.java`, `config.yml`

**Purpose:** When a player connects without VibeCraftMod installed, send them a chat message
explaining what the mod does and providing a clickable download link. Players who already have
the mod see nothing.

**Detection**

VibeCraftMod registers the plugin channel `vibecraft:events` via Fabric's `ClientPlayNetworking`
during client connection. Paper exposes registered client channels via
`player.getListeningPluginChannels()`. However, channel registration arrives slightly after
`PlayerJoinEvent` fires — a 40-tick (2 second) delayed check is reliable:

```java
plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
    if (!player.isOnline()) return;
    if (player.getListeningPluginChannels().contains("vibecraft:events")) return;
    sendModRecommendation(player);
}, 40L);
```

**Message format**

Use Paper's Adventure API (`net.kyori.adventure`) for a clickable component:

```java
private void sendModRecommendation(Player player) {
    String url = plugin.getConfig().getString("vibecraft-mod.url", "");
    if (url == null || url.isBlank()) return; // disabled if no URL configured

    Component msg = Component.text()
        .append(Component.text("[EnchantForge] ").color(NamedTextColor.GOLD))
        .append(Component.text("This server uses ")
            .color(NamedTextColor.YELLOW))
        .append(Component.text("VibeCraftMod")
            .color(NamedTextColor.AQUA)
            .decorate(TextDecoration.BOLD))
        .append(Component.text(" for an enhanced HUD and enchantment UI. ")
            .color(NamedTextColor.YELLOW))
        .append(Component.text("[Get it here]")
            .color(NamedTextColor.GREEN)
            .clickEvent(ClickEvent.openUrl(url))
            .hoverEvent(HoverEvent.showText(
                Component.text("Click to open download page").color(NamedTextColor.GRAY))))
        .build();

    player.sendMessage(msg);
}
```

If `vibecraft-mod.url` is blank or absent, the method returns immediately — the feature is
disabled until configured.

**New config.yml section**

```yaml
vibecraft-mod:
  url: ""   # download URL shown to players without the mod; leave blank to disable
```

Follows the same pattern as the existing `resource-pack:` block.

**Scope:**
- `PlayerSessionListener.java` — add `Plugin plugin` constructor parameter (for scheduler);
  add 40-tick delayed check in `onPlayerJoin`; add `sendModRecommendation(Player)` helper
- `config.yml` (resources) — add `vibecraft-mod.url: ""` section
- `EnchantForge.java` — pass `this` to `PlayerSessionListener` constructor
