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

		float spin = (age + partialTick) * 0.002F;
		// Camera position in the sphere's object space (unit sphere, spun around Y) for the rim/fresnel glow.
		Vec3 rel = camera.subtract(domain.center).scale(1.0 / radius);
		double cs = Math.cos(-spin);
		double sn = Math.sin(-spin);
		float[] camLocal = {(float) (rel.x * cs - rel.z * sn), (float) rel.y, (float) (rel.x * sn + rel.z * cs)};

		poseStack.pushPose();
		poseStack.translate(domain.center.x - camera.x, domain.center.y - camera.y, domain.center.z - camera.z);
		poseStack.rotate(new Quaternionf().rotateY(spin));
		poseStack.scale((float) radius, (float) radius, (float) radius);
		float a = alpha;
		context.submitNodeCollector().submitCustomGeometry(poseStack, RenderTypes.translucentMovingBlock(), (p, buffer) ->
				emitSphere(p, buffer, SphereMesh.HIGH, sprite, color, a, light, scroll, true, camLocal, 1.4F, null));
		// Soft outer glow shell.
		poseStack.scale(1.035F, 1.035F, 1.035F);
		float glowAlpha = Math.min(1.0F, alpha * 0.45F);
		context.submitNodeCollector().submitCustomGeometry(poseStack, RenderTypes.translucentMovingBlock(), (p, buffer) ->
				emitSphere(p, buffer, SphereMesh.LOW, glowSprite(), color, glowAlpha, FULL_BRIGHT, 0.0F, true, camLocal, 3.0F, null));
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
				float tiltRad = (float) Math.toRadians(object.tilt);
				float spinRad = (float) Math.toRadians(object.angle + object.spin * partialTick);
				poseStack.rotate(new Quaternionf().rotateX(tiltRad).rotateY(spinRad));
				poseStack.scale(scale, scale, scale);
				float[] lightLocal = null;
				if (object.litBy != null) {
					SceneObject sun = objects.get(object.litBy);
					if (sun != null && sun != object) {
						Vec3 toSun = sun.renderPos(partialTick).subtract(object.renderPos(partialTick));
						if (toSun.lengthSqr() > 1.0E-6) {
							lightLocal = toObjectSpace(toSun.normalize(), tiltRad, spinRad);
						}
					}
				}
				float[] lit = lightLocal;
				context.submitNodeCollector().submitCustomGeometry(poseStack, RenderTypes.translucentMovingBlock(), (p, buffer) ->
						emitSphere(p, buffer, SphereMesh.HIGH, sprite, object.color, object.alpha, light, 0.0F, false, null, 0.0F, lit));
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

	/**
	 * Emits a unit sphere.
	 *
	 * @param camLocal   camera position in object space for a fresnel rim (null = constant alpha)
	 * @param rimPower   how tight the rim glow is (higher = thinner rim)
	 * @param lightLocal light direction in object space for lambert shading (null = unlit / emissive)
	 */
	private static void emitSphere(PoseStack.Pose pose, VertexConsumer buffer, SphereMesh mesh, TextureAtlasSprite sprite,
								   int color, float alpha, int light, float scroll, boolean doubleSided,
								   float @Nullable [] camLocal, float rimPower, float @Nullable [] lightLocal) {
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
			for (int pass = 0; pass < (doubleSided ? 2 : 1); pass++) {
				boolean inside = pass == 1;
				for (int k = 0; k < 4; k++) {
					int v = inside ? 3 - k : k;
					int i = base + v * 5;
					float nx = d[i];
					float ny = d[i + 1];
					float nz = d[i + 2];
					float su = (d[i + 3] + scroll) % 1.0F;
					if (su < 0) su += 1.0F;
					float a = alpha;
					if (camLocal != null) {
						float vx = camLocal[0] - nx;
						float vy = camLocal[1] - ny;
						float vz = camLocal[2] - nz;
						float len = (float) Math.sqrt(vx * vx + vy * vy + vz * vz);
						float facing = len < 1.0E-5F ? 1.0F : Math.abs((nx * vx + ny * vy + nz * vz) / len);
						float rim = (float) Math.pow(1.0F - Math.min(1.0F, facing), rimPower);
						a = alpha * (0.3F + 0.7F * rim);
					}
					int cr = r;
					int cg = g;
					int cb = b;
					if (lightLocal != null) {
						float lambert = Math.max(0.0F, nx * lightLocal[0] + ny * lightLocal[1] + nz * lightLocal[2]);
						float shade = 0.18F + 0.82F * lambert;
						cr = (int) (r * shade);
						cg = (int) (g * shade);
						cb = (int) (b * shade);
					}
					buffer.addVertex(pose, nx, ny, nz).setColor(cr, cg, cb, (int) (Math.min(1.0F, a) * 255.0F))
							.setUv(u0 + du * su, v0 + dv * d[i + 4]).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
							.setNormal(pose, inside ? -nx : nx, inside ? -ny : ny, inside ? -nz : nz);
				}
			}
		}
	}

	/** Inverse of the object's rotate(X tilt) * rotate(Y spin): brings a world direction into object space. */
	private static float[] toObjectSpace(Vec3 dir, float tiltRad, float spinRad) {
		// inverse Y spin
		double cy = Math.cos(-spinRad);
		double sy = Math.sin(-spinRad);
		double x1 = dir.x * cy + dir.z * sy;
		double y1 = dir.y;
		double z1 = -dir.x * sy + dir.z * cy;
		// inverse X tilt
		double cx = Math.cos(-tiltRad);
		double sx = Math.sin(-tiltRad);
		double y2 = y1 * cx - z1 * sx;
		double z2 = y1 * sx + z1 * cx;
		return new float[]{(float) x1, (float) y2, (float) z2};
	}

	private static TextureAtlasSprite glowSprite() {
		return DomainSprites.get(Identifier.fromNamespaceAndPath("skysdomains", "scene/glow_soft"));
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
