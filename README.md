# EnchantForge

EnchantForge is a Paper 1.21.4 plugin that adds data-driven custom enchantments defined in YAML files.

## Highlights

- YAML-based custom enchantments (no code changes needed for most new enchants)
- Trigger-based effects: on_equip, on_damage_taken, stat_threshold, on_right_click, on_deal_damage, on_kill_entity, on_suit_jump
- Configurable cooldowns and end conditions
- Shared energy pool for active abilities (hand laser, thruster boots, FRIDAY AI)
- Suit system: double-jump flight, FRIDAY AI HUD, mob detection, fall protection
- Morph forms: transform player into an entity (wolf, or any spawnable entity)
- Robot Companion: Iron Golem that follows and auto-respawns
- Visual system: YAML-defined particle and sound cues on effect fire, hit, miss, fail
- Optional resource pack (teal absorption hearts)
- Optional debug logging per enchant

## Requirements

- Paper: 1.21.4
- Java: 21

## Build

From the EnchantForge folder:

```bash
./gradlew jar
```

Output jar:

- `build/libs/EnchantForge-1.0-SNAPSHOT.jar`

## Deploy

The build-all script in VibeCraftServer handles this automatically:

```bash
cd VibeCraftServer && ./build-all.sh
```

Or manually copy the jar to the server plugins folder and reload.

For config and YAML changes (no restart needed):

```
/cenchant reload
```

## Commands

| Command | Description |
|---------|-------------|
| `/cenchant <key> [level]` | Apply an enchantment to the held item |
| `/cenchant reload` | Reload all enchant YAMLs and config |
| `/cenchant catalog` | Open the enchant catalog (VibeCraftMod required) |
| `/cenchant guide` | Open the product guide (VibeCraftMod required) |
| `/ctestcooldown` | Apply a test cooldown to the held item for diagnostics |

## Permissions

- `enchantforge.cenchant` — access to `/cenchant` (apply and reload)
- `enchantforge.ctestcooldown` — access to `/ctestcooldown`

## Configuration

Runtime config is in `plugins/EnchantForge/config.yml`:

```yaml
resource-pack:
  host: ""       # server IP or domain; leave blank to disable the pack
  port: 8080
  url: ""        # override auto-computed URL (optional)
  required: false

vibecraft-mod:
  url: ""        # mod download link shown to players who join without it; blank to disable

debug:
  cooldownVisuals: false
```

## Enchant Definitions

Runtime enchant files are in:

- `plugins/EnchantForge/enchants/`

Bundled defaults live in source:

- `src/main/resources/enchants/`

Bundled files are copied to the runtime folder on startup if the deployed file is missing or
differs from the bundled version. Drop a new YAML directly into the runtime folder and run
`/cenchant reload` to add an enchant without rebuilding.

## Cooldown Behavior

Item-triggered enchants (right-click, on_deal_damage, on_kill_entity) use a player + enchant + item-id
cooldown key, so two same-material items have independent cooldown state. Passive armor interruption
logic uses a player + enchant key to prevent equip-swap bypasses.

Use `/ctestcooldown` while holding an item to verify item-isolated cooldown behavior.

## Project Notes

For implementation details, YAML schema examples, architecture notes, and extension guides, see:

- `CLAUDE.md`
