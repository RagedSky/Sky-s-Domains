package dev.sky.skysdomains.client.mixin;

import dev.sky.skysdomains.domain.FreezeManager;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Time freeze, client side: frozen entities are not ticked at all (and their interpolation is pinned). */
@Mixin(ClientLevel.class)
abstract class ClientLevelFreezeMixin {
	@Inject(method = "tickNonPassenger", at = @At("HEAD"), cancellable = true)
	private void skysdomains$skipFrozenEntity(Entity entity, CallbackInfo ci) {
		if (FreezeManager.shouldSkipTick(entity)) {
			ci.cancel();
		}
	}
}
