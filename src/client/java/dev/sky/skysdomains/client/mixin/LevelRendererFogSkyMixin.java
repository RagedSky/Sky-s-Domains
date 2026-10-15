package dev.sky.skysdomains.client.mixin;

import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import dev.sky.skysdomains.client.fx.FogSkyState;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * FogOverride / SkyOverride: tints the fog colour passed to the level renderer and skips the
 * vanilla sky while a domain requests its own sky (the sphere's inner wall draws the skybox).
 * Signature of {@code render} as in Fabric API 0.162 for 26.3.
 */
@Mixin(LevelRenderer.class)
abstract class LevelRendererFogSkyMixin {
	@Inject(method = "render", at = @At("HEAD"))
	private void skysdomains$tintFog(GraphicsResourceAllocator resourceAllocator, boolean renderOutline, CameraRenderState cameraState,
									 GpuBufferSlice terrainFog, Vector4f fogColor, boolean shouldRenderSky, boolean consistentDepthRequired, CallbackInfo ci) {
		FogSkyState.get().applyFogColor(fogColor);
	}

	@ModifyVariable(method = "render", at = @At("HEAD"), argsOnly = true, ordinal = 1)
	private boolean skysdomains$suppressSky(boolean shouldRenderSky) {
		return shouldRenderSky && !FogSkyState.get().suppressSky();
	}
}
