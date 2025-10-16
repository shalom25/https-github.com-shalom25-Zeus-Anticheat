# Zeus Anti-Cheat

Lightweight anti-cheat plugin for Spigot/Bukkit servers. It includes movement, combat, placement/breaking, inventory, teleport, vehicles and elytra detections, each with an `enabled` toggle and configurable thresholds in `config.yml`. Messaging is available in English and Spanish.

## Features
- Per-check toggles via `enabled` in `config.yml`.
- Immediate setbacks or cancellations for illegal actions.
- Violation level (VL) system with `warn` and `kick` thresholds per check.
- Administration commands (`/anticheat`) and utilities (`/zeus`).
- Customizable messages in `lang/messages_en.yml` and `lang/messages_es.yml`.
- Optional Essentials hook (softdepend).

## Requirements
- Java 8 or newer.
- Spigot/Bukkit server. Compiles against `spigot-api:1.8.8` and declares `api-version: 1.20` in `plugin.yml`. Adjust to your server version if needed.

## Installation
1. Download/build the JAR.
2. Copy `build/libs/ZeusAntiCheat-0.1.0.jar` into your server's `plugins/` folder.
3. Restart the server or use `/reload`.
4. Configure `plugins/ZeusAntiCheat/config.yml` to your needs.

## Build from source
- Windows: `./gradlew.bat clean build`
- Linux/Mac: `./gradlew clean build`
- Artifact: `build/libs/ZeusAntiCheat-0.1.0.jar`

## Quick configuration
All checks can be toggled and tuned:

```yml
# Example toggles
checks:
  movement:
    speed:
      enabled: true
    fly:
      enabled: true
    bhop:
      enabled: true
    ongroundspped:
      enabled: true
    step:
      enabled: true
    jesus:
      enabled: true
    jetpack:
      enabled: true
    spider:
      enabled: true
    blink:
      enabled: true
    nofall:
      enabled: true
    timer:
      enabled: true
    noslow:
      enabled: true
  combat:
    reach:
      enabled: true
    killaura:
      enabled: true
  click:
    autoclicker:
      enabled: true
  inventory:
    autototem:
      enabled: true
    autotool:
      enabled: true
  break:
    fastbreak:
      enabled: true
  place:
    fastplace:
      enabled: true
    scaffold:
      enabled: true
    freecam:
      enabled: true
    autocrystal:
      enabled: true
    autotrap:
      enabled: true
  teleport:
    chorus:
      enabled: true
  elytra:
    enabled: true
  vehicle:
    boat:
      speed:
        enabled: true
      fly:
        enabled: true
```

Additionally, each check has configurable thresholds (e.g. `checks.movement.speed.max_base_walk`, `checks.combat.reach.max_distance`, etc.). See `src/main/resources/config.yml` for the full list and comments.

### Punishments
Configure per-check when to warn (`warn`) or kick (`kick`):

```yml
punishments:
  Speed: { warn: 5, kick: 12 }
  Fly: { warn: 4, kick: 10 }
  # ... other checks
```

## Commands
`/anticheat` (permission: `anticheat.admin`)
- `reload` — Reload config and messages.
- `status <player>` — Show player violations.
- `reset <player> [all|check]` — Reset total VL or a single check.

`/zeus` (permissions under `zeus.*`)
- `help` — Show help.
- `reload` — Reload config and messages.
- `logs [player] [count] [page]` — View detection logs.
- `last [player]` — Show latest detection detail (defaults to yourself).
- `alerts` — Toggle staff alerts.
- `mute <player> [minutes]` — Mute player.
- `unmute <player>` — Unmute player.

## Permissions
- `anticheat.admin` — Manage Zeus Anti-Cheat (default `op`).
- `zeus.reload`, `zeus.help`, `zeus.logs`, `zeus.alerts`, `zeus.mute`, `zeus.unmute` — Zeus utilities.
- `zeus.*` — Access all Zeus permissions.

## Available checks
- Movement: `Speed`, `OnGroundSpeed`, `Fly`, `BHop`, `Blink`, `Step`, `WaterWalk`, `Jetpack`, `NoFall`, `Timer`, `Climb`, `NoSlow`.
- Elytra/Vehicles: `ElytraFly`, `BoatSpeed`, `BoatFly`.
- Combat: `Reach`, `KillAura`.
- Placement: `FastPlace`, `Scaffold`, `Freecam`, `AutoCrystal`, `AutoTrap`.
- Breaking: `FastBreak`.
- Click/Inventory: `AutoClicker`, `AutoTotem`, `AutoTool`, `Inventory`.
- Teleport: `ChorusControl`.
- Protection: `Crash` (books/signs), `Baritone` (automated pathing).

## Messages and localization
- Files: `lang/messages_en.yml` and `lang/messages_es.yml`.
- Configurable prefix: `messages.prefix` in `config.yml`.
- Default language is set to English (`language: en`).

## Hooks
- `Essentials` (softdepend): if present and `hooks.essentials.enabled: true`, moderation listeners are registered.

## Tuning tips
- Adjust thresholds if you see false positives (e.g. sliding surfaces in `checks.movement.speed.ignore_surfaces`).
- Use `/zeus alerts` to manage staff alert noise.
- Disable checks you don't need with `enabled: false`.

## Compatibility
- Compiles against Spigot 1.8.8; `plugin.yml` declares `api-version: 1.20`. The plugin is designed to work across a wide range of versions; validate in your environment and adjust `plugin.yml`/dependencies if your server uses a different version.

## Support
If you have questions or issues, open an issue or adjust configuration and retry. Please include your server version, Java, and relevant configs.

### Notes & roadmap
- This plugin is a starting point. It will not block “nearly all” hacks.
- Expand combat checks (KillAura, reach), autoclicker, scaffold, timer, nofall, etc.
- Integrate packet analysis via ProtocolLib for robust detections.
- Safe teleport/setback at high flags to prevent position gain.
- Per-world/game-mode profiles and structured logging (webhook/file).

Contributions and improvements are welcome.