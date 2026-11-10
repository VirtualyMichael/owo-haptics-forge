# OWO Haptics for Forge / NeoForge

Client-side mod that drives an OWO haptic suit from Minecraft, built so it works in Vivecraft modpacks.
Clean-room implementation (no code from owocraft); the OWO network protocol is implemented directly
in Java (UDP, port 54020), so no native libraries or OWO SDK are needed.

## Which jar?

All jars are in `dist/`. Pick the one matching your pack's Minecraft version **and** loader.

| Jar | Minecraft | Loader | Java |
|---|---|---|---|
| `owohaptics-forge-1.16.5-1.1.0.jar` | 1.16.5 | Forge 36+ | 8 |
| `owohaptics-forge-1.19.2-1.1.0.jar` | 1.19.2 | Forge 43+ | 17 |
| `owohaptics-forge-1.20.1-1.1.0.jar` | 1.20.1 | Forge 47+ | 17 |
| `owohaptics-forge-1.21.1-1.1.0.jar` | 1.21.1 | Forge 52+ | 21 |
| `owohaptics-neoforge-1.21.1-1.1.0.jar` | 1.21.1 | NeoForge 21.1+ | 21 |

Fabric already has [owocraft](https://modrinth.com/mod/owocraft).

## Install

1. Install and start the OWO desktop app (or its Visualizer, which needs no suit) on a PC on the same network.
2. Drop the jar in the pack's `mods` folder. It is client-only, so servers don't need it.
3. Launch the game. In a world, run `/owo status`, then `/owo test all`.

If the app isn't found (different subnet, VPN, standalone headset), set `owoAppHost` in the mod's config
to the IP of the PC running the OWO app.

## Features

- **Hits:** directional by where the damage came from (front/left/right/back), with patterns per damage type
  (fall, explosion, fire, drowning, freezing, magic, thorns, projectiles).
- **Environment:** on fire, drowning, freezing (1.19.2+), low-health heartbeat.
- **Arms (works in VR):** swing, mining, bow/crossbow draw and release, on the arm that did it (follows your main-hand setting).
- **Eating/drinking** pulses.

## Settings page (web UI)

While the game runs, open **http://localhost:8765** (or type `/owo ui` in game for the address) in any browser.
One page works for every version and loader:

- Sliders for each effect's strength, per-damage scaling, duration, frequency and fades, plus a master strength slider.
- Toggles for the feature groups and a zone picker (front/back/left/right, individual muscles, or "follows the game" zones).
- A **Test** button on every effect that plays the values currently on screen through the running game's OWO connection.
- Changes apply instantly and are saved to `config/owohaptics/effects.json` (including the global settings).

It listens on this PC only. To use it from a phone or second PC (handy in VR), set `allowNetworkAccess = true` in the
`[webUi]` section of the mod config, then open `http://<this PC's IP>:8765`. Anyone on that network could then change your
settings, so leave it off on shared networks. Writes also require a custom header, so other websites you have open cannot
change your settings. The port and the whole page can be changed or disabled in the same config section.

## Commands

`/owo status`, `/owo ui`, `/owo stop`, `/owo reload`, `/owo play <effect>`, `/owo test <front|back|left|right|all|heart|arms>`

On 1.16.5 there is no client-command API, so these are intercepted from chat (type them the same way).

## Configuration

- `config/owohaptics-client.toml`: master switch, OWO game id, app host, and web UI options (on/off, port, network access).
- `config/owohaptics/effects.json`: every effect plus the global settings (master strength, feature toggles). Created on first
  launch and edited by the web UI (or by hand, then `/owo reload`).
  If you used v1.0.0: master strength and the toggles moved from the toml into this file.

## Scripting: effects.json

Edit values, then run `/owo reload`. Each effect has:

| Field | Meaning |
|---|---|
| `enabled` | turn the effect off with `false` |
| `frequency` | 1-100 Hz |
| `duration` | seconds (0.1-20) |
| `intensity` | base percent (0-100) |
| `perDamage` | extra percent per unit of "amount" (damage points for hits, ticks drawn for bows) |
| `rampUp`, `rampDown` | fade in/out seconds (0-2) |
| `zones` | list of body zones |

Final intensity = `intensity + perDamage * amount`, scaled by the global intensity.

**Zones:** `front`, `back`, `left`, `right`, `all`, `torso`, `arms`, `heart`, a single muscle
(`PECTORAL_R/L`, `ABDOMINAL_R/L`, `ARM_R/L`, `DORSAL_R/L`, `LUMBAR_R/L`), or dynamic tokens:
`source` (side facing the damage), `hand` (the arm that swung/drew), `main_arm`, `off_arm`.

**Built-in effect ids:** `damage.fall`, `damage.explosion`, `damage.drown`, `damage.fire`, `damage.freeze`,
`damage.magic`, `damage.thorns`, `damage.projectile`, `damage.generic`, `env.fire`, `env.drowning`,
`env.freezing`, `heartbeat.lub`, `heartbeat.dub`, `arm.swing`, `arm.mine`, `arm.draw`, `arm.release`, `eat`.

**Example:** make fall damage stronger, and map a modded damage type to an existing effect:

```json
{
  "effects": {
    "damage.fall": { "frequency": 30, "intensity": 60, "perDamage": 10 },
    "my.custom":   { "frequency": 50, "duration": 0.4, "intensity": 70, "zones": ["PECTORAL_R", "ARM_R"] }
  },
  "damageTypes": {
    "mymod.zap": "damage.magic"
  }
}
```

Fields you omit keep their defaults. A file that fails to parse is reported in the log and defaults are used.
Test an effect with `/owo play my.custom`.

## Building from source

Each version is its own Gradle project (`owo-forge-<mc>`, `owo-neoforge-1.21.1`). Shared code lives in `core/`;
run `sh sync-core.sh` to copy it into every project, then `./gradlew build` with the matching JDK
(8 for 1.16.5, 17 for 1.19.2/1.20.1, 21 for 1.21.1).

## Status and limits

- Compiles for all five targets; protocol handshake, effect loading and the web UI (including Test and save) were tested against a mock OWO app.
  In-game behavior with a real client is **not yet verified**.
- Vivecraft is not called directly; arm haptics are inferred from swing and item-use state, which Vivecraft drives too.
- Scripting is JSON-based (no Python/JS engine); the web UI is the friendly front end for it.

## License and credits

MIT (see `LICENSE`). Not affiliated with OWO. The protocol implementation follows the MIT-licensed official OWO SDK
as documented by the [Unofficial Python OWO SDK](https://github.com/VixenCreations/Unofficial-Python-OWO-SDK).
Each loader project started from its official MDK template (Forge / NeoForge licenses apply to those template files).
