package dev.sky.skysdomains.client.mixin;

import dev.sky.skysdomains.client.fx.PostEffectManager;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * 26.3 rebuilds the list of requested post effects every frame in {@code GameRenderer#update}
 * (player effects + spectated entity). We append the domain post effects at the end of that
 * method so they are extracted and applied like vanilla ones.
 */
@Mixin(GameRenderer.class)
abstract class GameRendererPostEffectMixin {
	@Shadow
	@Final
	private List<Identifier> requestedPostEffects;

	@Inject(method = "update", at = @At("TAIL"))
	private void skysdomains$requestDomainPostEffects(DeltaTracker deltaTracker, CallbackInfo ci) {
		PostEffectManager.get().collectRequested(this.requestedPostEffects);
	}
}
