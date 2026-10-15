package dev.sky.skysdomains.client.mixin;

import dev.sky.skysdomains.domain.FreezeManager;
import net.minecraft.client.particle.ParticleEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * While a time freeze is active for this client (Stasis / Structural Collapse froze entities),
 * particles stop in mid-air as well.
 */
@Mixin(ParticleEngine.class)
abstract class ParticleEngineFreezeMixin {
	@Inject(method = "tick", at = @At("HEAD"), cancellable = true)
	private void skysdomains$freezeParticles(CallbackInfo ci) {
		if (FreezeManager.clientHasFrozen()) {
			ci.cancel();
		}
	}
}
