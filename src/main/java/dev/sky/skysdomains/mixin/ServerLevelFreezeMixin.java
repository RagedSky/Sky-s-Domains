package dev.sky.skysdomains.mixin;

import dev.sky.skysdomains.domain.FreezeManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Time freeze, server side. {@code tickNonPassenger} is the single entry point through which the
 * level ticks an entity (and its passengers); skipping it here stops every subclass' tick body
 * (LivingEntity AI and travel, projectile flight, ...), not just {@code Entity#tick}.
 */
@Mixin(ServerLevel.class)
abstract class ServerLevelFreezeMixin {
	@Inject(method = "tickNonPassenger", at = @At("HEAD"), cancellable = true)
	private void skysdomains$skipFrozenEntity(Entity entity, CallbackInfo ci) {
		if (FreezeManager.shouldSkipTick(entity)) {
			ci.cancel();
		}
	}
}
