package dev.sky.skysdomains.client.mixin;

import dev.sky.skysdomains.client.render.DomainLook;
import dev.sky.skysdomains.client.render.DomainLooks;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Stores the domain look of a living entity on its render state during extraction. For the glass
 * look the opaque body is hidden here; {@code DomainLookLayer} draws the translucent copy.
 */
@Mixin(LivingEntityRenderer.class)
abstract class LivingEntityRendererLookMixin {
	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V", at = @At("RETURN"))
	private void skysdomains$extractLook(LivingEntity entity, LivingEntityRenderState state, float partialTick, CallbackInfo ci) {
		DomainLook look = DomainLooks.lookFor(entity);
		state.setData(DomainLooks.LOOK, look);
		state.setData(DomainLooks.INTENSITY, look == DomainLook.ICE ? DomainLooks.iceIntensity(entity) : null);
		if (look == DomainLook.GLASS) {
			state.isInvisible = true;
		}
	}
}
