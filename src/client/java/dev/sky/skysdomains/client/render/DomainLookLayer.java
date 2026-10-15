package dev.sky.skysdomains.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.sky.skysdomains.SkysDomains;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Util;

/**
 * Draws the domain looks over every living entity:
 * <ul>
 *   <li>ICE: the entity's model again, slightly inflated, with a scrolling frost texture.</li>
 *   <li>GLASS: the entity's own texture as translucent glass (the opaque body is hidden during
 *       extraction) plus a soft highlight shell.</li>
 *   <li>FROZEN: two chromatic ghost copies jittering around the entity plus scan-line glitches.</li>
 * </ul>
 */
public final class DomainLookLayer<S extends LivingEntityRenderState, M extends EntityModel<? super S>> extends RenderLayer<S, M> {
	private static final Identifier ICE = SkysDomains.id("textures/entity/ice_overlay.png");
	private static final Identifier GLASS = SkysDomains.id("textures/entity/glass_overlay.png");
	private static final Identifier GLITCH = SkysDomains.id("textures/entity/glitch_overlay.png");

	private final LivingEntityRenderer<?, S, M> renderer;

	public DomainLookLayer(LivingEntityRenderer<?, S, M> renderer) {
		super(renderer);
		this.renderer = renderer;
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, S state, float limbAngle, float limbDistance) {
		DomainLook look = state.getData(DomainLooks.LOOK);
		if (look == null) return;
		float time = (Util.getMillis() % 100000L) / 1000.0F;
		M model = getParentModel();
		switch (look) {
			case ICE -> {
				float intensity = state.getDataOrDefault(DomainLooks.INTENSITY, 0.5F);
				inflated(poseStack, state, 1.0F + 0.06F * intensity, () -> collector.submitModel(model, state, poseStack,
						RenderTypes.energySwirl(ICE, time * 0.02F, time * 0.03F), light, OverlayTexture.NO_OVERLAY,
						ARGB.color((int) (90 + 150 * intensity), 0xDFF6FF)));
			}
			case GLASS -> {
				Identifier skin = renderer.getTextureLocation(state);
				collector.submitModel(model, state, poseStack, RenderTypes.entityTranslucent(skin), light, OverlayTexture.NO_OVERLAY, ARGB.color(110, 0xF8FBFF));
				inflated(poseStack, state, 1.03F, () -> collector.submitModel(model, state, poseStack,
						RenderTypes.energySwirl(GLASS, time * 0.05F, -time * 0.04F), light, OverlayTexture.NO_OVERLAY, ARGB.color(70, 0xFFF4D0)));
			}
			case FROZEN -> {
				Identifier skin = renderer.getTextureLocation(state);
				// Jitter changes a few times per second, giving a "stuck in a broken frame" look.
				long frame = Util.getMillis() / 120L;
				float jx = (hash(frame) - 0.5F) * 0.10F;
				float jz = (hash(frame * 7L + 3L) - 0.5F) * 0.10F;
				ghost(poseStack, state, model, collector, skin, light, jx, jz, ARGB.color(120, 0xFF4A4A));
				ghost(poseStack, state, model, collector, skin, light, -jx, -jz, ARGB.color(120, 0x4AE8FF));
				inflated(poseStack, state, 1.02F, () -> collector.submitModel(model, state, poseStack,
						RenderTypes.energySwirl(GLITCH, 0.0F, time * 0.6F), light, OverlayTexture.NO_OVERLAY, ARGB.color(140, 0xFFFFFF)));
			}
		}
	}

	private void ghost(PoseStack poseStack, S state, M model, SubmitNodeCollector collector, Identifier skin, int light, float dx, float dz, int color) {
		poseStack.pushPose();
		poseStack.translate(dx, 0.0F, dz);
		collector.submitModel(model, state, poseStack, RenderTypes.entityTranslucent(skin), light, OverlayTexture.NO_OVERLAY, color);
		poseStack.popPose();
	}

	/** Scales the model about the entity's body centre (model space is y-down, feet at 1.501). */
	private static void inflated(PoseStack poseStack, LivingEntityRenderState state, float scale, Runnable draw) {
		float centerY = 1.501F - state.boundingBoxHeight * state.scale * 0.5F;
		poseStack.pushPose();
		poseStack.translate(0.0F, centerY, 0.0F);
		poseStack.scale(scale, scale, scale);
		poseStack.translate(0.0F, -centerY, 0.0F);
		draw.run();
		poseStack.popPose();
	}

	private static float hash(long n) {
		long x = n * 0x9E3779B97F4A7C15L;
		x ^= x >>> 29;
		x *= 0xBF58476D1CE4E5B9L;
		x ^= x >>> 32;
		return (x & 0xFFFF) / 65535.0F;
	}
}
