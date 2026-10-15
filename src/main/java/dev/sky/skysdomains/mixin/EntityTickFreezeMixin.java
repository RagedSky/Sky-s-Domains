package dev.sky.skysdomains.mixin;

import dev.sky.skysdomains.domain.FreezeManager;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Time freeze: a frozen entity skips its whole tick (AI, movement, gravity, attacks, animations).
 * Applies on both sides; the server decides who is frozen and mirrors it to the clients.
 */
@Mixin(Entity.class)
abstract class EntityTickFreezeMixin {
	@Inject(method = "tick", at = @At("HEAD"), cancellable = true)
	private void skysdomains$skipTickWhenFrozen(CallbackInfo ci) {
		if (FreezeManager.shouldSkipTick((Entity) (Object) this)) {
			ci.cancel();
		}
	}
}
