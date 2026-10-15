package dev.sky.skysdomains.client.render;

import dev.sky.skysdomains.client.animation.Easing;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.Locale;

/**
 * A renderable object inside a domain: planet, sun, white hole, glass shard, clock hand...
 * Positions are relative to the domain centre. Supports tweens (move/scale/alpha with easing),
 * orbits around another object and following another object (rings).
 */
public final class SceneObject {
	public enum Kind {
		SPHERE, BILLBOARD, RING;

		public static Kind parse(String name) {
			try {
				return valueOf(name.toUpperCase(Locale.ROOT));
			} catch (IllegalArgumentException e) {
				return SPHERE;
			}
		}
	}

	public final String id;
	public final Kind kind;
	public Identifier texture;
	public Vec3 pos = Vec3.ZERO;
	public Vec3 prevPos = Vec3.ZERO;
	public float scale = 1.0F;
	public float prevScale = 1.0F;
	public float alpha = 1.0F;
	public int color = 0xFFFFFF;
	/** Degrees per tick around the Y axis. */
	public float spin;
	/** Degrees, fixed tilt around X. */
	public float tilt;
	public boolean emissive = true;
	public boolean visible = true;
	public float angle;
	public int age;
	/** Particle trail while a tween is running (e.g. planets flying at their target). */
	@Nullable
	public Identifier trailParticle;

	// orbit
	@Nullable
	public String orbitAround;
	public float orbitRadius;
	public int orbitPeriod;
	public float orbitPhase;
	public float orbitTilt;
	public boolean orbiting;

	// follow (rings)
	@Nullable
	public String followId;

	// tween
	private boolean tweening;
	private Vec3 fromPos = Vec3.ZERO;
	private Vec3 toPos = Vec3.ZERO;
	private float fromScale;
	private float toScale;
	private float fromAlpha;
	private float toAlpha;
	private int tweenAge;
	private int tweenDuration;
	private Easing tweenEasing = Easing.LINEAR;
	@Nullable
	private Runnable onTweenEnd;

	public SceneObject(String id, Kind kind, Identifier texture) {
		this.id = id;
		this.kind = kind;
		this.texture = texture;
	}

	public void setOrbit(String around, float radius, int period, float phase, float tilt) {
		this.orbitAround = around;
		this.orbitRadius = radius;
		this.orbitPeriod = Math.max(1, period);
		this.orbitPhase = phase;
		this.orbitTilt = tilt;
		this.orbiting = true;
	}

	public void stopOrbit() {
		this.orbiting = false;
	}

	public void tweenTo(@Nullable Vec3 targetPos, @Nullable Float targetScale, @Nullable Float targetAlpha, int duration, Easing easing) {
		tweenTo(targetPos, targetScale, targetAlpha, duration, easing, null);
	}

	public void tweenTo(@Nullable Vec3 targetPos, @Nullable Float targetScale, @Nullable Float targetAlpha, int duration, Easing easing, @Nullable Runnable onEnd) {
		orbiting = false;
		fromPos = pos;
		toPos = targetPos == null ? pos : targetPos;
		fromScale = scale;
		toScale = targetScale == null ? scale : targetScale;
		fromAlpha = alpha;
		toAlpha = targetAlpha == null ? alpha : targetAlpha;
		tweenDuration = Math.max(0, duration);
		tweenAge = 0;
		tweenEasing = easing;
		onTweenEnd = onEnd;
		tweening = true;
		if (tweenDuration == 0) finishTween();
	}

	public boolean isTweening() {
		return tweening;
	}

	private void finishTween() {
		pos = toPos;
		scale = toScale;
		alpha = toAlpha;
		tweening = false;
		Runnable callback = onTweenEnd;
		onTweenEnd = null;
		if (callback != null) callback.run();
	}

	/** Advances the object by one tick. {@code resolver} finds other objects (orbit centre / follow target). */
	public void tick(java.util.function.Function<String, SceneObject> resolver) {
		prevPos = pos;
		prevScale = scale;
		age++;
		angle += spin;
		if (tweening) {
			tweenAge++;
			float t = tweenEasing.apply((float) tweenAge / tweenDuration);
			pos = fromPos.lerp(toPos, t);
			scale = fromScale + (toScale - fromScale) * t;
			alpha = fromAlpha + (toAlpha - fromAlpha) * t;
			if (tweenAge >= tweenDuration) finishTween();
		} else if (orbiting && orbitAround != null) {
			SceneObject center = resolver.apply(orbitAround);
			Vec3 base = center == null ? Vec3.ZERO : center.pos;
			double a = orbitPhase * Math.PI * 2 + (double) age / orbitPeriod * Math.PI * 2;
			double x = Math.cos(a) * orbitRadius;
			double z = Math.sin(a) * orbitRadius;
			double tiltRad = Math.toRadians(orbitTilt);
			double y = Math.sin(a) * orbitRadius * Math.sin(tiltRad);
			pos = base.add(x, y, z * Math.cos(tiltRad));
		} else if (followId != null) {
			SceneObject target = resolver.apply(followId);
			if (target != null) {
				pos = target.pos;
				if (!target.visible) visible = false;
			}
		}
	}

	public Vec3 renderPos(float partialTick) {
		return prevPos.lerp(pos, partialTick);
	}

	public float renderScale(float partialTick) {
		return prevScale + (scale - prevScale) * partialTick;
	}
}
