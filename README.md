# EnchantForge

EnchantForge is a Paper 1.21.4 plugin that adds data-driven custom enchantments defined in YAML files.

## Highlights

- YAML-based custom enchantments (no code changes needed for most new enchants)
- Trigger-based effects (for example right click, damage taken, deal damage, kill, equip)
- Configurable cooldowns and end conditions
- Optional debug logging for enchant behavior and cooldown visuals
- Optional generated resource pack support

## Requirements

- Paper: 1.21.4
- Java: 21

## Build

From the EnchantForge folder:

```powershell
.\gradlew.bat jar
```

Output jar:

- build/libs/EnchantForge-1.0-SNAPSHOT.jar

## Deploy

Copy the built jar to your server plugins folder:

```powershell
Copy-Item -Path .\build\libs\EnchantForge-1.0-SNAPSHOT.jar -Destination ..\server\plugins\EnchantForge.jar -Force
```

Then reload the server plugin or restart server:

- /reload confirm

For config and YAML changes, you can use:

- /cenchant reload

## Commands

- /cenchant <enchantment> [level]
- /cenchant reload
- /ctestcooldown

## Permissions

- enchantforge.cenchant
- enchantforge.ctestcooldown

## Configuration

Runtime config is in:

- server/plugins/EnchantForge/config.yml

Important debug switch:

```yaml
debug:
  cooldownVisuals: true
```

## Enchant Definitions

Runtime enchant files are in:

- server/plugins/EnchantForge/enchants/

Bundled defaults live in source:

- src/main/resources/enchants/

## Cooldown Behavior

EnchantForge uses item-aware cooldown handling for item-triggered enchants. This allows same-material items to have independent cooldown state.

- Item-triggered paths use player + enchant + item-id cooldown keys.
- Passive armor interruption logic remains player + enchant scoped to prevent equip-swap bypasses.
- Cooldown visuals use per-item cooldown groups when available.

Use /ctestcooldown while holding an item to quickly verify item-isolated cooldown behavior.

## Project Notes

For deeper implementation details, architecture notes, and YAML schema examples, see:

- CLAUDE.md
