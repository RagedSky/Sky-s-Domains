# Sequence file reference

`assets/skysdomains/sequences/<domain>.json`, reloaded on every resource reload (F3+T).

```jsonc
{
  "sphere": { "texture": "skysdomains:scene/sphere_frost_vault", "color": "#DDF6FF",
              "alpha": 0.35, "inside_alpha": 0.45, "inside_opaque": false, "scroll": 0.004, "emissive": true },
  "phases": {
    "open":   [ { "tick": 0, "actions": [ ... ] }, { "tick": 4, "actions": [ ... ] } ],
    "active": [ { "tick": 0, "every": 6, "actions": [ ... ] } ],
    "close":  [ { "tick": 0, "actions": [ ... ] } ]
  },
  "events": { "impact": [ ... ], "clash": [ ... ] }
}
```

* **Phases** are defined by the server. Ordinary domains: `open` (30 ticks), `active` (duration from
  the config), `close` (20 ticks). Structural Collapse: `activation`, `expand`, `solar_system`,
  `collapse`, `bombardment`, `end` (lengths in the config). `tick` is relative to the phase start;
  `every` repeats the keyframe every N ticks after `tick`.
* **Events** are sent by the server: `impact`, `planet_launch`, `sun_launch`, `sun_impact`
  (Structural Collapse), `shatter` (Glass Heaven), `center_hit` (Umbral Sea), `clash` (every domain)
  and `close` (fired by the client when the close packet arrives). Inside event actions the position
  keyword `event` refers to the event position, and `every_nth` on an `impact_frame` fires it only on
  every n-th event of that name.
* Players that come into range later replay the earlier keyframes instantly (no flashes, tweens
  jump to their end), so they see the current state.
* Colours are `"#RRGGBB"`. Positions are `[x, y, z]` relative to the domain centre, in blocks, or
  multiplied by the radius with `"relative_to_radius": true`.

## Actions

| Type | Fields | Effect |
|---|---|---|
| `impact_frame` | `texture` (name under `textures/impact/<domain>/` or a full id), `duration`, `every_nth` | Full-screen PNG for `duration` ticks. |
| `flash` | `color`, `duration` (hold), `fade` | Full-screen colour, then fades out. |
| `invert` | `duration` | Inverts the screen (post effect; soft flash with reduced flashes). |
| `post_shader` | `name` (`invert`, `desaturate`, `chromatic`, `radial_blur`, `heat`), `duration` (0 = until the domain ends) | Data-driven post effect. |
| `shake` | `strength`, `duration` | Camera shake with fall-off. |
| `fov_punch` | `delta` (degrees, may be negative), `duration`, `easing` | Temporary FOV change. |
| `time_freeze` | – | Server side (config); accepted for documentation. |
| `sound` | `id`, `volume`, `pitch`, `at` (`center`, `player`, `event`) | Plays a sound event (mod or vanilla id). |
| `audio_duck` | `duration`, `level` | Lowers music/ambient/mob/block/weather volume. Released when the domain closes. |
| `sphere_scale` | `from`, `to` (fractions of the radius), `duration`, `easing` | Grows / shrinks the sphere. |
| `sphere_style` | any `sphere` field | Changes the sphere look at runtime. |
| `fog` | `color`, `density` or `reset: true` | Tints the vanilla fog colour and adds a screen tint; reset removes it. |
| `sky` | `id` (`none`, `space`, `default`) | `none`/`space` suppress the vanilla sky (the sphere wall shows the backdrop); `default` restores it. |
| `vignette` | `strength`, `duration` (0 = until the domain ends) | Dark screen edges. |
| `spawn` | `id`, `kind` (`sphere`, `billboard`, `ring`), `texture`, `pos`, `relative_to_radius`, `at_event` (pos is relative to the event position), `follow` (object id to stick to) + `offset`, `lit_by` (object id used as light source for lambert shading of spheres), `scale`, `color`, `alpha`, `spin` (deg/tick), `tilt`, `emissive`, `only_if_planet`, `ring {texture, scale, tilt, alpha}` | Creates a scene object. `only_if_planet: n` skips it when the domain throws fewer than n+1 planets. |
| `orbit` | `id`, `around`, `radius`, `relative_to_radius`, `period` (ticks), `phase` (0–1), `tilt` | Object orbits another object. |
| `move` | `id`, `pos`, `relative_to_radius`, `to_event`, `scale`, `alpha`, `spin`, `duration`, `easing` | Tweens position / scale / alpha. |
| `orbit_collapse` | `target`, `duration`, `easing` | Every orbiting object spirals into `target`. |
| `remove` | `id` (`*` = all) | Removes objects. |
| `particles` | `particle` (simple vanilla particle id), `count`, `area` (`center`, `ground`, `rise`, `sphere`, `shell`, `event`), `speed`, `spread` | Spawns client particles. |

Easings: `linear`, `ease_in_quad`, `ease_out_quad`, `ease_in_out_quad`, `ease_in_cubic`,
`ease_out_cubic`, `ease_in_out_cubic`, `ease_in_expo`, `ease_out_expo`, `ease_in_out_expo`,
`ease_out_back`, `ease_in_back`, `ease_out_elastic`, `ease_out_bounce`.

## Structural Collapse specifics

The planets are spawned by the `solar_system` phase with the ids `mercury` … `neptune` (plus `sun`,
`white_hole`). When the server launches planet *n*, the client moves the object with that id from the
`white_hole` object to the target over the flight time and runs `events.planet_launch`; on impact the
object is removed and `events.impact` runs at the impact position. `sun_launch` / `sun_impact` work
the same way for the sun.
