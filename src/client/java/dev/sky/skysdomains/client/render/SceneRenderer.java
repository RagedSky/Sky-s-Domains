package dev.sky.skysdomains.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.sky.skysdomains.client.ClientDomain;
import dev.sky.skysdomains.client.animation.Easing;
import dev.sky.skysdomains.client.animation.Sequence;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Renders the domain sphere (visible from inside and outside, half transparent, scrolling texture)
 * and all scene objects of one domain. Geometry is submitted through the vanilla
 * {@code SubmitNodeCollector} with the translucent moving-block render type, which samples the
 * block atlas (where our scene textures are stitched).
 */
public final class SceneRenderer {
	/** Lightmap coordinates for "full bright" (sky 15, block 15). */
	private static final int FULL_BRIGHT = 0xF000F0;

	private final ClientDomain domain;
	private final Map<String, SceneObject> objects = new LinkedHashMap<>();
	private final Sequence.SphereStyle style;

	private float sphereScale;
	private float prevSphereScale;
	private float sphereFrom;
	private float sphereTo = 1.0F;
	private int sphereTweenAge;
	private int sphereTweenDuration;
	private Easing sphereEasing = Easing.EASE_OUT_EXPO;
	private boolean sphereTweening;
	private int age;

	public SceneRenderer(ClientDomain domain, Sequence.SphereStyle style) {
		this.domain = domain;
		this.style = style.copy();
	}

	// --- sphere -------------------------------------------------------------------------------

	public Sequence.SphereStyle sphereStyle() {
		return style;
	}

	public void sphereScale(float from, float to, int duration, Easing easing) {
		sphereFrom = from;
		sphereTo = to;
		sphereTweenAge = 0;
		sphereTweenDuration = Math.max(0, duration);
		sphereEasing = easing;
		sphereTweening = true;
		if (duration == 0) {
			sphereScale = to;
			prevSphereScale = to;
			sphereTweening = false;
		}
	}

	public float sphereScaleNow() {
		return sphereScale;
	}

	// --- objects ------------------------------------------------------------------------------

	public SceneObject spawn(String id, SceneObject.Kind kind, Identifier texture) {
		SceneObject object = new SceneObject(id, kind, texture);
		objects.put(id, object);
		return object;
	}

	@Nullable
	public SceneObject get(String id) {
		return objects.get(id);
	}

	public void remove(String id) {
		objects.remove(id);
		objects.remove(id + "_ring");
	}

	public void removeAll() {
		objects.clear();
	}

	public List<SceneObject> objects() {
		return new ArrayList<>(objects.values());
	}

	/** All orbiting objects spiral into {@code target} (sun implosion). */
	public void collapseOrbits(String target, int duration, Easing easing) {
		SceneObject center = objects.get(target);
		Vec3 to = center == null ? Vec3.ZERO : center.pos;
		for (SceneObject object : objects.values()) {
			if (object.orbiting || (object.followId != null && !object.id.equals(target))) {
				object.tweenTo(to, 0.05F, object.alpha, duration, easing);
			}
		}
	}

	// --- ticking ------------------------------------------------------------------------------

	public void tick() {
		age++;
		prevSphereScale = sphereScale;
		if (sphereTweening) {
			sphereTweenAge++;
			float t = sphereEasing.apply((float) sphereTweenAge / sphereTweenDuration);
			sphereScale = sphereFrom + (sphereTo - sphereFrom) * t;
			if (sphereTweenAge >= sphereTweenDuration) {
				sphereScale = sphereTo;
				sphereTweening = false;
			}
		}
		Minecraft client = Minecraft.getInstance();
		for (SceneObject object : objects.values()) {
			object.tick(objects::get);
			if (object.trailParticle != null && object.isTweening() && client.level != null) {
				ParticleType<?> type = BuiltInRegistries.PARTICLE_TYPE.getValue(object.trailParticle);
				if (type instanceof SimpleParticleType simple) {
					Vec3 world = domain.center.add(object.pos);
					for (int i = 0; i < 3; i++) {
						client.level.addParticle(simple, world.x + (Math.random() - 0.5) * object.scale, world.y + (Math.random() - 0.5) * object.scale,
								world.z + (Math.random() - 0.5) * object.scale, 0, 0, 0);
					}
				}
			}
		}
	}

	// --- rendering ----------------------------------------------------------------------------

	public void render(LevelRenderContext context, float partialTick) {
		PoseStack poseStack = context.poseStack();
		if (poseStack == null) return;
		Vec3 camera = context.levelState().cameraRenderState.pos;
		float scaleNow = prevSphereScale + (sphereScale - prevSphereScale) * partialTick;
		double radius = domain.radius * scaleNow;
		boolean insideSphere = camera.distanceToSqr(domain.center) < radius * radius;

		// Scene objects first (they are inside the sphere), then the sphere wall.
		for (SceneObject object : objects.values()) {
			if (!object.visible || object.alpha <= 0.01F || object.scale <= 0.001F) continue;
			renderObject(context, poseStack, object, camera, partialTick);
		}
		if (radius > 0.05) {
			renderSphere(context, poseStack, camera, radius, insideSphere, partialTick);
		}
	}

	private void renderSphere(LevelRenderContext context, PoseStack poseStack, Vec3 camera, double radius, boolean inside, float partialTick) {
		TextureAtlasSprite sprite = DomainSprites.get(Identifier.parse(style.texture));
		float alpha = inside ? (style.insideOpaque ? 1.0F : style.insideAlpha) : style.alpha;
		int color = style.color;
		float scroll = (age + partialTick) * style.scroll;
		int light = style.emissive ? FULL_BRIGHT : lightAt(domain.center);

		poseStack.pushPose();
		poseStack.translate(domain.center.x - camera.x, domain.center.y - camera.y, domain.center.z - camera.z);
		poseStack.rotate(new Quaternionf().rotateY((age + partialTick) * 0.002F));
		poseStack.scale((float) radius, (float) radius, (float) radius);
		PoseStack.Pose pose = poseStack.last();
		float a = alpha;
		context.submitNodeCollector().submitCustomGeometry(poseStack, RenderTypes.translucentMovingBlock(), (p, buffer) ->
				emitSphere(p, buffer, SphereMesh.HIGH, sprite, color, a, light, scroll, true));
		poseStack.popPose();
	}

	private void renderObject(LevelRenderContext context, PoseStack poseStack, SceneObject object, Vec3 camera, float partialTick) {
		TextureAtlasSprite sprite = DomainSprites.get(object.texture);
		Vec3 world = domain.center.add(object.renderPos(partialTick));
		float scale = object.renderScale(partialTick);
		int light = object.emissive ? FULL_BRIGHT : lightAt(world);

		poseStack.pushPose();
		poseStack.translate(world.x - camera.x, world.y - camera.y, world.z - camera.z);
		switch (object.kind) {
			case SPHERE -> {
				poseStack.rotate(new Quaternionf().rotateX((float) Math.toRadians(object.tilt)).rotateY((float) Math.toRadians(object.angle + object.spin * partialTick)));
				poseStack.scale(scale, scale, scale);
				context.submitNodeCollector().submitCustomGeometry(poseStack, RenderTypes.translucentMovingBlock(), (p, buffer) ->
						emitSphere(p, buffer, SphereMesh.LOW, sprite, object.color, object.alpha, light, 0.0F, false));
			}
			case RING -> {
				poseStack.rotate(new Quaternionf().rotateX((float) Math.toRadians(object.tilt)).rotateY((float) Math.toRadians(object.angle + object.spin * partialTick)));
				poseStack.scale(scale, scale, scale);
				context.submitNodeCollector().submitCustomGeometry(poseStack, RenderTypes.translucentMovingBlock(), (p, buffer) ->
						emitRing(p, buffer, sprite, object.color, object.alpha, light));
			}
			case BILLBOARD -> {
				// Face the camera: build right/up from the direction to the camera and world up.
				Vec3 toCamera = camera.subtract(world);
				double length = toCamera.length();
				Vec3 forward = length < 1.0E-4 ? new Vec3(0, 0, 1) : toCamera.scale(1.0 / length);
				Vec3 up = Math.abs(forward.y) > 0.99 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
				Vec3 right = up.cross(forward).normalize();
				Vec3 trueUp = forward.cross(right).normalize();
				double s = scale;
				float spinRad = (float) Math.toRadians(object.angle + object.spin * partialTick);
				double c = Math.cos(spinRad);
				double sn = Math.sin(spinRad);
				Vec3 r = right.scale(c).add(trueUp.scale(sn)).scale(s);
				Vec3 u = trueUp.scale(c).subtract(right.scale(sn)).scale(s);
				context.submitNodeCollector().submitCustomGeometry(poseStack, RenderTypes.translucentMovingBlock(), (p, buffer) ->
						emitBillboard(p, buffer, sprite, object.color, object.alpha, light, r, u, forward));
			}
		}
		poseStack.popPose();
	}

	// --- geometry emitters --------------------------------------------------------------------

	private static void emitSphere(PoseStack.Pose pose, VertexConsumer buffer, SphereMesh mesh, TextureAtlasSprite sprite,
								   int color, float alpha, int light, float scroll, boolean doubleSided) {
		int a = (int) (alpha * 255.0F);
		int r = (color >> 16) & 0xFF;
		int g = (color >> 8) & 0xFF;
		int b = color & 0xFF;
		float u0 = sprite.getU0();
		float v0 = sprite.getV0();
		float du = sprite.getU1() - u0;
		float dv = sprite.getV1() - v0;
		float[] d = mesh.data;
		for (int q = 0; q < mesh.quadCount; q++) {
			int base = q * 20;
			// outside (counter-clockwise seen from outside)
			for (int v = 0; v < 4; v++) {
				int i = base + v * 5;
				float su = (d[i + 3] + scroll) % 1.0F;
				if (su < 0) su += 1.0F;
				buffer.addVertex(pose, d[i], d[i + 1], d[i + 2]).setColor(r, g, b, a)
						.setUv(u0 + du * su, v0 + dv * d[i + 4]).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
						.setNormal(pose, d[i], d[i + 1], d[i + 2]);
			}
			if (doubleSided) {
				for (int v = 3; v >= 0; v--) {
					int i = base + v * 5;
					float su = (d[i + 3] + scroll) % 1.0F;
					if (su < 0) su += 1.0F;
					buffer.addVertex(pose, d[i], d[i + 1], d[i + 2]).setColor(r, g, b, a)
							.setUv(u0 + du * su, v0 + dv * d[i + 4]).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
							.setNormal(pose, -d[i], -d[i + 1], -d[i + 2]);
				}
			}
		}
	}

	private static void emitRing(PoseStack.Pose pose, VertexConsumer buffer, TextureAtlasSprite sprite, int color, float alpha, int light) {
		int a = (int) (alpha * 255.0F);
		int r = (color >> 16) & 0xFF;
		int g = (color >> 8) & 0xFF;
		int b = color & 0xFF;
		float u0 = sprite.getU0();
		float v0 = sprite.getV0();
		float u1 = sprite.getU1();
		float v1 = sprite.getV1();
		// A flat textured square (the texture is an annulus with transparent centre), both faces.
		float[][] corners = {{-1, 0, -1, u0, v0}, {-1, 0, 1, u0, v1}, {1, 0, 1, u1, v1}, {1, 0, -1, u1, v0}};
		for (float[] c : corners) {
			buffer.addVertex(pose, c[0], c[1], c[2]).setColor(r, g, b, a).setUv(c[3], c[4])
					.setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, 1, 0);
		}
		for (int i = 3; i >= 0; i--) {
			float[] c = corners[i];
			buffer.addVertex(pose, c[0], c[1], c[2]).setColor(r, g, b, a).setUv(c[3], c[4])
					.setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, -1, 0);
		}
	}

	private static void emitBillboard(PoseStack.Pose pose, VertexConsumer buffer, TextureAtlasSprite sprite, int color, float alpha, int light,
									  Vec3 right, Vec3 up, Vec3 normal) {
		int a = (int) (alpha * 255.0F);
		int r = (color >> 16) & 0xFF;
		int g = (color >> 8) & 0xFF;
		int b = color & 0xFF;
		float u0 = sprite.getU0();
		float v0 = sprite.getV0();
		float u1 = sprite.getU1();
		float v1 = sprite.getV1();
		Vec3[] corners = {right.scale(-1).add(up), right.scale(-1).subtract(up), right.add(up.scale(-1)), right.add(up)};
		float[][] uvs = {{u0, v0}, {u0, v1}, {u1, v1}, {u1, v0}};
		for (int i = 0; i < 4; i++) {
			Vec3 c = corners[i];
			buffer.addVertex(pose, (float) c.x, (float) c.y, (float) c.z).setColor(r, g, b, a).setUv(uvs[i][0], uvs[i][1])
					.setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
		}
		for (int i = 3; i >= 0; i--) {
			Vec3 c = corners[i];
			buffer.addVertex(pose, (float) c.x, (float) c.y, (float) c.z).setColor(r, g, b, a).setUv(uvs[i][0], uvs[i][1])
					.setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, (float) -normal.x, (float) -normal.y, (float) -normal.z);
		}
	}

	private static int lightAt(Vec3 pos) {
		// Non-emissive objects are rare; keep it simple and bright enough to be visible.
		return 0xA000A0;
	}
}
