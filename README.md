# Sky's Domains

A Fabric mod for **Minecraft Java Edition 26.3** (Fabric Loader ≥ 0.19.5, Fabric API 0.162.0+26.3, Java 25).
Talismans with inscribed sigils open one of six cinematic **domains** around the player: a sphere with
guaranteed-hit effects, server-synchronised animations (every player in range sees the same thing),
impact frames, camera shake, fog and sky overrides, post-processing shaders and a scripted
"Structural Collapse" that throws the solar system at your enemies.

* Mod id: `skysdomains` · Package: `dev.sky.skysdomains` · Author: Sky · License: MIT
* Build: `./gradlew build` → `build/libs/skysdomains-<version>.jar`
* Dev client: `./gradlew runClient`

> **Build status:** `./gradlew build` passes against Minecraft 26.3 / Fabric API 0.162.0+26.3
> (Loom 1.17.20, JDK 25). The vanilla signatures that could not be checked while writing were
> verified against the 26.3 client jar afterwards; see [docs/VERIFY.md](docs/VERIFY.md) for what
> changed and what still needs an in-game test.

---

## Installation (CurseForge launcher)

1. In the CurseForge app create a **Minecraft 26.3** profile with the **Fabric** mod loader
   (Loader 0.19.5 or newer).
2. Add **Fabric API** for 26.3 to the profile (`Add More Content` → search *Fabric API*), or drop the
   Fabric API jar into the profile's `mods` folder.
3. Copy `skysdomains-<version>.jar` into the profile's `mods` folder
   (`Profile` → `⋯` → `Open Folder` → `mods`).
4. Start the profile. The config file `config/skysdomains.json` is created on first start.
5. On a dedicated server install the same two jars into the server's `mods` folder. Clients need the
   mod as well (it has rendering and key-binding parts).

Default activation key: **R** (change it in *Options → Controls → Sky's Domains* or in the config).

---

## How to play

Everything below is also explained in game: craft the **Domain Codex** (Book + Arcane Dust), a
written book with all recipes, drops and domain descriptions in German and English.

1. **Mine the ores.** Arcane Ore (Overworld, Y −64…16, iron pickaxe) drops Arcane Dust; Abyssal Ore
   (Nether, Y 8…100, diamond pickaxe) drops Abyssal Shards; Void Ore (End outer islands, netherite
   pickaxe) drops Void Crystals. Fortune works, Silk Touch gives the block.
2. **Hunt bosses.** The Wither drops a Cursed Fragment (85 %), the Warden drops Echoed Matter (90 %),
   both independent of Looting.
3. **Craft a talisman.** Domain Core (4 Arcane Dust + 4 Ender Pearls + 1 Book) → Fractured Talisman
   (Domain Core + 2 Sealing Ink + 4 Paper). Sealing Ink: Ink Sac + Arcane Dust + Glass Bottle → 3.
4. **Inscribe a sigil.** Talisman + Sigil in a crafting table. Every domain has its own sigil
   (recipes below). A new sigil overwrites the old one; tier and refinements stay.
5. **Use it.** Put the talisman in the **offhand** and press the activation key. Each domain costs
   energy (HUD bar above the hotbar), has a radius, a duration and a cooldown.
6. **Upgrade.** Smithing table: talisman + template + catalyst. The catalysts bundle the upgrade
   materials because the smithing table has one addition slot:
   * Sealed (T2): Fractured + **Sealing Template** (Nether fortress chests) + Sealing Catalyst
     (2 Abyssal Shard + 1 Cursed Fragment)
   * Absolute (T3): Sealed + **Absolute Template** (End city chests) + Absolute Catalyst
     (2 Void Crystal + 1 Nether Star)
   * Transcendent (T4): Absolute + **Transcendent Template** (Ancient city chests) + Transcendent
     Catalyst (2 Echoed Matter + 2 Void Crystal + 1 Cursed Fragment)
7. **Refine.** Anvil: talisman + Refinement (Abyssal Shard + Arcane Dust + Amethyst Shard), up to 3
   times, each +10 % radius and duration. The tooltip shows the count and all current values.

### Sigils

| Sigil | Recipe (crafting table) |
|---|---|
| Frost Sigil | 4 Packed Ice, 1 Powder Snow Bucket, 2 Arcane Dust, 1 Blue Ice |
| Umbral Sigil | 4 Ink Sacs, 2 Sculk, 1 Echo Shard, 2 Arcane Dust |
| Glass Sigil | 4 Glass, 2 Amethyst Shards, 1 Gold Ingot, 2 Arcane Dust |
| Ember Sigil | 4 Blaze Powder, 2 Magma Cream, 1 Fire Charge, 2 Arcane Dust |
| Stasis Sigil | 1 Clock, 4 Amethyst Shards, 2 Arcane Dust, 1 Abyssal Shard |
| Collapse Sigil | 1 End Crystal, 1 Cursed Fragment, 2 Abyssal Shards, 2 Arcane Dust |

### Domains

| Domain | Effect |
|---|---|
| **Frost Vault** | Enemies freeze fast, get Slowness and take frost damage once fully frozen; water turns to ice, lava to obsidian. The floor freezes over from the centre outwards into ice, packed ice and blue ice with snow dusting, and ice spikes grow out of the ground like stalagmites in different sizes. Everything is journaled and thawed back when the vault closes. |
| **Umbral Sea** | Darkness for everyone but the owner, enemies are pulled to the centre and "drown in shadows" there (armour-ignoring magic damage). |
| **Glass Heaven** | Owner: Resistance II + Regeneration II; enemies: Weakness II; hostile projectiles shatter on the sphere. Pure defence, longer duration. |
| **Ember Garden** | The floor is infected from the centre outwards: scorched ground with glowing cracks, molten ground and small lava pools. Enemies take fire damage per second and are scorched by every step; the owner is immune to fire and lava. Every replaced block is journaled and restored when the domain closes (also after a crash or server stop). |
| **Stasis** | All projectiles inside are deleted, mobs do not tick (no AI, movement, gravity, attacks). Players move freely (configurable). |
| **Structural Collapse** | Time freeze, the sky becomes space, a solar system appears, collapses into a white hole, and the planets are thrown at the frozen enemies (3/4/6/8 planets per tier; tier 4 ends with the sun). Explosion damage only, block damage optional. |

Rules that apply to every domain:
* "Guaranteed hit": every enemy inside the sphere is affected, no line of sight needed.
* The owner is immune against the own domain. Other players are enemies by default
  (configurable, scoreboard teams are respected).
* The state lives on the server; clients only receive open/phase/close/event packets.
* **Domain clash:** if two spheres overlap, the higher tier wins; on equal tier the one with more
  energy spent; the other collapses immediately.

---

## Configuration (`config/skysdomains.json`)

The file is rewritten on every start so new options appear automatically.

| Key | Meaning |
|---|---|
| `keybindDefault` | Default key as SDL scancode name without prefix (`R`, `G`, `V`, `LEFTBRACKET`). The in-game controls screen overrides it. |
| `energy.max`, `energy.regenPerSecond`, `energy.regenDelaySeconds` | Energy pool, regeneration, pause after an activation. |
| `tiers.<fractured/sealed/absolute/transcendent>` | `radius`, `durationSeconds`, `cooldownSeconds`, `regenMultiplier` (while in the offhand), `energyCost`. |
| `refinementBonus` | Radius/duration bonus per refinement (0.10 = +10 %). |
| `domains.frostVault` | `freezeTicksPerTick[4]`, `freezeDamagePerSecond[4]`, `slownessAmplifier[4]`, `convertWaterToIce`, `convertLavaToObsidian`, `radiusMultiplier`, `durationMultiplier`, `freezeGround` (ice floor, default true), `iceSpikes` (stalagmites, default true), `spikeDensity` (0..1, default 0.07), `maxSpikeHeight` (default 8), `spreadTicks` (default 45). |
| `domains.umbralSea` | `pullStrength[4]`, `centerDamagePerSecond[4]`, `centerRadius`, `darknessForOtherPlayers`. |
| `domains.glassHeaven` | `resistanceAmplifier`, `regenerationAmplifier`, `weaknessAmplifier`, `shatterProjectiles`, `durationMultiplier`. |
| `domains.emberGarden` | `fireDamagePerSecond[4]`, `radiusMultiplier`, `durationMultiplier`, `infectGround` (turn the floor into scorched/molten ground, default true), `lavaPools` (small lava pools near the centre, default true), `spreadTicks` (ticks until the infection reaches the rim, default 60), `groundDamage` (per step for enemies). |
| `domains.groundBlacklist` | Block ids Ember Garden and Frost Vault never replace (unbreakable blocks, containers and bedrock are skipped anyway). |
| `domains.stasis` | `freezePlayers`, `deleteProjectiles`, `radiusMultiplier`, `durationMultiplier`. |
| `domains.structuralCollapse` | `planetsPerTier[4]`, `sunAtTier4`, `explosionPower{mercury…neptune}`, `sunExplosionPower`, `blockDamage`, `impactIntervalTicks`, `planetFlightTicks`, phase lengths `activationTicks`, `expandTicks`, `solarSystemTicks`, `collapseTicks`, `endTicks`. |
| `targeting` | `playersAreEnemies`, `respectTeams`, `onlyHostileMobs`, `spareOwnPets`. |
| `accessibility.reducedFlashes` | Hard white/invert frames become soft fades. `shakeMultiplier` scales camera shake (0 = off). |
| `debugLogging` | Extra log lines for activations and phases. |

Arrays with four entries are per tier (Fractured, Sealed, Absolute, Transcendent).

---

## Animation system

Every domain is a **sequence file**: `assets/skysdomains/sequences/<domain>.json`, reloaded with
F3+T. A sequence has a sphere style, keyframes per **phase** (ticks relative to the phase start,
optionally repeating with `every`) and action lists per **server event** (`impact`, `planet_launch`,
`shatter`, `center_hit`, `clash`, `close`). The phases and their lengths come from the server:
`open` / `active` / `close` for the five ordinary domains, and `activation` / `expand` /
`solar_system` / `collapse` / `bombardment` / `end` for Structural Collapse.

Actions: `impact_frame`, `flash`, `invert`, `post_shader`, `shake`, `fov_punch`, `sound`,
`audio_duck`, `sphere_scale`, `sphere_style`, `fog`, `sky`, `vignette`, `spawn`, `orbit`, `move`,
`orbit_collapse`, `remove`, `particles` (plus `time_freeze`, which is server-side and accepted as a
no-op). The full reference with all fields is in [docs/SEQUENCES.md](docs/SEQUENCES.md).

---

## Assets

Impact frames are real anime impact frames: a post shader turns the live scene into two tones with ink
edges and flickering speed lines, and transparent 2560×1440 overlays (speed lines, ink splatter, cracks)
are drawn on top. Overlays and scene textures (1024×512 sphere skins, 512px planets and sprites)
are procedurally rendered by `tools/generate_art.py` (`--frames 2160` produces true 4K frames at
about four times the jar size and VRAM). Item and block icons come from
`tools/generate_placeholders.py`. [docs/ASSETS.md](docs/ASSETS.md) lists each path so you can
replace them 1:1. Sounds are still vanilla placeholders (see `sounds.json`).

## Project layout

```
src/main/java/dev/sky/skysdomains
  SkysDomains.java            common entrypoint
  DomainType / TalismanTier   enums used everywhere
  common/                     blocks, items, components, sounds, creative tab, loot, worldgen hooks
  config/                     JSON config
  domain/                     server logic: DomainManager, ActiveDomain, energy, targeting, freeze
  domain/effects/             one class per domain
  network/                    payloads (open/phase/close/event/freeze/status/activate)
  mixin/                      Entity tick freeze, anvil refinement
src/client/java/dev/sky/skysdomains/client
  SkysDomainsClient.java      client entrypoint (key, HUD, overlays, rendering, tooltips)
  animation/                  Sequencer, Sequence loader, Actions, Easing
  render/                     SceneRenderer (sphere, planets, billboards), sprites, mesh
  fx/                         overlays, camera shake/FOV, audio duck, fog/sky, post effects
  hud/                        energy bar
  mixin/                      particle freeze, fog colour / sky suppression
src/main/resources            assets (models, textures, lang, sequences, sounds, post effects) and data
                              (loot tables, tags, recipes, worldgen)
```
