# Build verification

`./gradlew build` succeeds (Minecraft 26.3, Fabric Loader 0.19.5, Fabric API 0.162.0+26.3, Loom 1.17.20, JDK 25).

## Signatures corrected after checking the 26.3 client jar

| Original guess | 26.3 reality |
|---|---|
| `Player.displayClientMessage(Component, true)` | `Player.sendOverlayMessage(Component)` |
| `Entity.hurtMarked` | `Entity.syncVelocity` |
| `PoseStack.mulPose(Quaternionf)` | `PoseStack.rotate(Quaternionfc)` |
| `Options.hideGui` | `Minecraft.gui.hud.isHidden()` |
| `SoundManager.updateSourceVolume` | `SoundManager.updateCategoryVolume(SoundSource, float)` |
| `GameRenderer.setPostEffect / clearPostEffect` | post effects are a per-frame list (`GameRenderer#update`); the mod appends its ids via `GameRendererPostEffectMixin` |

Confirmed unchanged: `CameraRenderState.orientation` (Quaternionf), `AnvilMenu.cost` / `repairItemCountCost`,
`ItemCombinerMenu.inputSlots` / `resultSlots`, `Level.explode(Entity, double, double, double, float, ExplosionInteraction)`,
`Options.fov()` / `getSoundSourceVolume`, `Item.Properties.rarity` / `fireResistant`, `RenderTypes.translucentMovingBlock()`,
`ARGB.color(int, int)`, `Registry.getValue(Identifier)`, `ClientLevel.playLocalSound` / `addParticle`.

## Still to be checked in game

* Camera shake: rotates `CameraRenderState.orientation` after extraction. If the view does not shake,
  rotate `CameraRenderState.viewRotationMatrix` as well (same class, `Matrix4f`).
* Sphere / planet rendering through `RenderTypes.translucentMovingBlock()` with sprites from the block atlas.
* Post effects (`post_effect/*.json` + GLSL) compile on the GPU; a failing effect is skipped by vanilla
  (`failedPostEffects`) and logged.
* Fog tint via `LevelRenderer.render(...)`'s `Vector4f fogColor` and sky suppression via the
  `shouldRenderSky` argument.

Dedicated-server smoke test: `./gradlew runServer` loads the mod, applies the mixins and parses all
data (loot tables, recipes, tags, worldgen) – see the session log for the result.
