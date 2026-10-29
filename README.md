# Swinging Hanging Signs

Hanging signs in Minecraft no longer hang frozen in place. They sway in the wind, swing when something brushes past them, and slowly settle again.

## Features

- **Idle sway:** every hanging sign rocks very slightly and slowly. Each sign has its own timing, so a row of signs never moves in sync.
- **Wind and weather:** signs swing more in rain and much more in a thunderstorm. Gusts rise and fall over time.
- **Entity push:** players, mobs, items and minecarts that move past or under a sign push it in their direction. It swings, overshoots a little and settles back.
- **Realistic motion:** each sign is a small damped spring that pivots at the top, where the chains attach.
- **Wall signs:** signs hung from a wall bracket swing too, or you can switch them off.

## Design

- **Client-side only.** Works on any server, including vanilla ones.
- **Visual only.** Blocks, hitboxes, gameplay and world data are untouched.
- **Cheap.** Only rendered signs are simulated, and signs at rest cost almost nothing.
- **Compatible** with Sodium and Iris.

## Settings

Open the settings from Mod Menu on Fabric or from the Mods list on NeoForge and Forge. They are saved in `config/swingingsigns.json`.

| Setting | Default | |
|---|---|---|
| Enabled | on | Turns all swinging on or off |
| Wind strength | 1.0 | 0 to 3 |
| Push strength | 1.0 | 0 to 3 |
| Weather | on | Rain and thunderstorms make signs swing harder |
| Wall signs | on | Wall-bracket hanging signs swing too |

## Versions

| Loader | Minecraft |
|---|---|
| Fabric (needs Fabric API) | 1.20.1 to 26.3 |
| NeoForge | 1.21 to 26.3 |
| Forge | 1.20.1 |

## License

MIT
