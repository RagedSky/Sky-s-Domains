package dev.sky.skysdomains.client.render;

import dev.sky.skysdomains.client.ClientDomain;
import dev.sky.skysdomains.client.ClientDomainState;
import dev.sky.skysdomains.domain.FreezeManager;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

/**
 * Decides per living entity which {@link DomainLook} applies. Evaluated during render state
 * extraction (see {@code LivingEntityRendererLookMixin}) and stored on the render state so the
 * {@link DomainLookLayer} can draw it.
 */
public final class DomainLooks {
	public static final RenderStateDataKey<DomainLook> LOOK = RenderStateDataKey.create(() -> "skysdomains domain look");
	/** 0..1 intensity (ice growth), stored next to the look. */
	public static final RenderStateDataKey<Float> INTENSITY = RenderStateDataKey.create(() -> "skysdomains domain look intensity");

	private DomainLooks() {
	}

	@Nullable
	public static DomainLook lookFor(LivingEntity entity) {
		int id = entity.getId();
		for (ClientDomain domain : ClientDomainState.all()) {
			if (domain.closing || domain.ownerEntityId == id) continue;
			if (entity.position().distanceToSqr(domain.center) > domain.radius * domain.radius) continue;
			switch (domain.type) {
				case FROST_VAULT -> {
					return DomainLook.ICE;
				}
				case GLASS_HEAVEN -> {
					return DomainLook.GLASS;
				}
				default -> { }
			}
		}
		if (FreezeManager.isClientFrozen(id)) return DomainLook.FROZEN;
		return null;
	}

	/** Ice shell intensity from the vanilla freeze ticks (synced to the client). */
	public static float iceIntensity(LivingEntity entity) {
		return 0.35F + 0.65F * Math.min(1.0F, entity.getTicksFrozen() / 140.0F);
	}
}
