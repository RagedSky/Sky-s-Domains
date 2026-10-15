package dev.sky.skysdomains.client.animation;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.sky.skysdomains.SkysDomains;
import dev.sky.skysdomains.client.render.SceneObject;
import dev.sky.skysdomains.common.ModSounds;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.DustColorTransitionOptions;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.Locale;
import java.util.Random;

/**
 * Parses the {@code actions} of a keyframe / event into {@link SequenceAction}s.
 *
 * <p>Supported types (all fields optional unless noted):
 * <pre>
 * impact_frame   texture* ("none" = shader only), duration, every_nth, style (bw|invert|bw_clean|invert_clean|none)
 * flash          color, duration, fade
 * invert         duration
 * post_shader    name*, duration (0 = until the domain ends)
 * shake          strength, duration
 * fov_punch      delta, duration, easing
 * time_freeze    (server side – accepted, no client effect)
 * sound          id*, volume, pitch, at (center|player|event)
 * audio_duck     duration, level
 * sphere_scale   from, to, duration, easing            (fractions of the radius)
 * sphere_style   texture, color, alpha, inside_alpha, inside_opaque, scroll, emissive
 * fog            color, density, reset
 * sky            id (space|none|default)
 * vignette       strength, duration
 * spawn          id*, kind (sphere|billboard|ring), texture*, pos, relative_to_radius, at_event, follow, offset, lit_by, scale, color, alpha, spin, tilt, emissive, only_if_planet, ring{texture,scale,tilt,alpha}
 * orbit          id*, around*, radius, relative_to_radius, period, phase, tilt
 * move           id*, pos, relative_to_radius, scale, alpha, spin, duration, easing, to_event (bool)
 * orbit_collapse target*, duration, easing
 * remove         id* ("*" = all)
 * particles      particle*, count, area (center|ground|sphere|shell|rise|event|ring|column|spiral|implode), speed, spread, color, color_to, size (dust)
 * </pre>
 */
public final class Actions {
	private static final Random RANDOM = new Random();

	private Actions() {
	}

	@Nullable
	public static SequenceAction parse(JsonObject json, String domainId) {
		String type = str(json, "type", "");
		return switch (type) {
			case "impact_frame" -> impactFrame(json, domainId);
			case "flash" -> flash(json);
			case "invert" -> invert(json);
			case "post_shader" -> postShader(json);
			case "shake" -> shake(json);
			case "fov_punch" -> fovPunch(json);
			case "time_freeze" -> (ctx, instant) -> { };
			case "sound" -> sound(json);
			case "audio_duck" -> audioDuck(json);
			case "sphere_scale" -> sphereScale(json);
			case "sphere_style" -> sphereStyle(json);
			case "fog" -> fog(json);
			case "sky" -> sky(json);
			case "vignette" -> vignette(json);
			case "spawn" -> spawn(json);
			case "orbit" -> orbit(json);
			case "move" -> move(json);
			case "orbit_collapse" -> orbitCollapse(json);
			case "remove" -> remove(json);
			case "particles" -> particles(json);
			default -> {
				SkysDomains.LOGGER.warn("Unknown sequence action type '{}'", type);
				yield null;
			}
		};
	}

	// --- overlays -----------------------------------------------------------------------------

	private static SequenceAction impactFrame(JsonObject json, String domainId) {
		String texture = str(json, "texture", "frame_01");
		int duration = integer(json, "duration", 2);
		int everyNth = integer(json, "every_nth", 1);
		String style = str(json, "style", "bw");
		Identifier id = "none".equals(texture) ? null : texture.contains(":") ? Identifier.parse(texture)
				: SkysDomains.id("textures/impact/" + domainId + "/" + texture + ".png");
		return (ctx, instant) -> {
			if (instant) return;
			if (everyNth > 1 && ctx.eventCounter() % everyNth != 0) return;
			ctx.overlays().impactFrame(id, duration, style);
		};
	}

	private static SequenceAction flash(JsonObject json) {
		int color = color(json, "color", 0xFFFFFF);
		int duration = integer(json, "duration", 3);
		int fade = integer(json, "fade", 2);
		return (ctx, instant) -> {
			if (!instant) ctx.overlays().flash(color, duration, fade);
		};
	}

	private static SequenceAction invert(JsonObject json) {
		int duration = integer(json, "duration", 2);
		return (ctx, instant) -> {
			if (!instant) ctx.post().invert(duration);
		};
	}

	private static SequenceAction postShader(JsonObject json) {
		String name = str(json, "name", "desaturate");
		int duration = integer(json, "duration", 0);
		return (ctx, instant) -> ctx.post().apply(ctx.domain().id, name, duration);
	}

	private static SequenceAction shake(JsonObject json) {
		float strength = number(json, "strength", 0.5F);
		int duration = integer(json, "duration", 10);
		return (ctx, instant) -> {
			if (!instant) ctx.camera().shake(strength, duration);
		};
	}

	private static SequenceAction fovPunch(JsonObject json) {
		float delta = number(json, "delta", 10.0F);
		int duration = integer(json, "duration", 10);
		Easing easing = Easing.parse(str(json, "easing", null), Easing.EASE_OUT_EXPO);
		return (ctx, instant) -> {
			if (!instant) ctx.camera().fovPunch(delta, duration, easing);
		};
	}

	private static SequenceAction vignette(JsonObject json) {
		float strength = number(json, "strength", 0.7F);
		int duration = integer(json, "duration", 0);
		return (ctx, instant) -> ctx.overlays().vignette(ctx.domain().id, strength, duration);
	}

	// --- audio --------------------------------------------------------------------------------

	private static SequenceAction sound(JsonObject json) {
		String id = str(json, "id", "skysdomains:domain.impact_frame");
		float volume = number(json, "volume", 1.0F);
		float pitch = number(json, "pitch", 1.0F);
		String at = str(json, "at", "center");
		return (ctx, instant) -> {
			if (instant || ctx.client().level == null) return;
			SoundEvent event = ModSounds.byId(id);
			Vec3 pos = ctx.resolve(at);
			ctx.client().level.playLocalSound(pos.x, pos.y, pos.z, event, SoundSource.PLAYERS, volume, pitch, false);
		};
	}

	private static SequenceAction audioDuck(JsonObject json) {
		int duration = integer(json, "duration", 40);
		float level = number(json, "level", 0.15F);
		return (ctx, instant) -> {
			if (!instant) ctx.audio().duck(duration, level);
		};
	}

	// --- sphere / fog / sky ---------------------------------------------------------------------

	private static SequenceAction sphereScale(JsonObject json) {
		float from = number(json, "from", 0.0F);
		float to = number(json, "to", 1.0F);
		int duration = integer(json, "duration", 20);
		Easing easing = Easing.parse(str(json, "easing", null), Easing.EASE_OUT_EXPO);
		return (ctx, instant) -> ctx.scene().sphereScale(from, to, instant ? 0 : duration, easing);
	}

	private static SequenceAction sphereStyle(JsonObject json) {
		return (ctx, instant) -> {
			Sequence.SphereStyle style = ctx.scene().sphereStyle();
			if (json.has("texture")) style.texture = json.get("texture").getAsString();
			if (json.has("color")) style.color = color(json, "color", style.color);
			if (json.has("alpha")) style.alpha = number(json, "alpha", style.alpha);
			if (json.has("inside_alpha")) style.insideAlpha = number(json, "inside_alpha", style.insideAlpha);
			if (json.has("inside_opaque")) style.insideOpaque = json.get("inside_opaque").getAsBoolean();
			if (json.has("scroll")) style.scroll = number(json, "scroll", style.scroll);
			if (json.has("emissive")) style.emissive = json.get("emissive").getAsBoolean();
		};
	}

	private static SequenceAction fog(JsonObject json) {
		boolean reset = json.has("reset") && json.get("reset").getAsBoolean();
		int color = color(json, "color", 0x000000);
		float density = number(json, "density", 0.5F);
		return (ctx, instant) -> {
			if (reset) ctx.fogSky().clearFog(ctx.domain().id);
			else ctx.fogSky().setFog(ctx.domain().id, color, density);
		};
	}

	private static SequenceAction sky(JsonObject json) {
		String id = str(json, "id", "default");
		return (ctx, instant) -> {
			if ("default".equals(id)) ctx.fogSky().clearSky(ctx.domain().id);
			else ctx.fogSky().setSky(ctx.domain().id, id);
		};
	}

	// --- scene objects ------------------------------------------------------------------------

	private static SequenceAction spawn(JsonObject json) {
		String id = str(json, "id", "object");
		SceneObject.Kind kind = SceneObject.Kind.parse(str(json, "kind", "sphere"));
		Identifier texture = Identifier.parse(str(json, "texture", "skysdomains:scene/planet_generic"));
		Vec3 pos = vec3(json, "pos", Vec3.ZERO);
		float scale = number(json, "scale", 1.0F);
		int color = color(json, "color", 0xFFFFFF);
		float alpha = number(json, "alpha", 1.0F);
		float spin = number(json, "spin", 0.0F);
		float tilt = number(json, "tilt", 0.0F);
		boolean emissive = !json.has("emissive") || json.get("emissive").getAsBoolean();
		int onlyIfPlanet = integer(json, "only_if_planet", -1);
		boolean relative = bool(json, "relative_to_radius", false);
		boolean atEvent = bool(json, "at_event", false);
		@Nullable String follow = json.has("follow") ? str(json, "follow", null) : null;
		Vec3 offset = vec3(json, "offset", Vec3.ZERO);
		@Nullable String litBy = json.has("lit_by") ? str(json, "lit_by", null) : null;
		@Nullable JsonObject ring = json.has("ring") ? json.getAsJsonObject("ring") : null;
		return (ctx, instant) -> {
			if (onlyIfPlanet >= 0 && onlyIfPlanet >= ctx.domain().planets) return;
			if (follow != null && ctx.scene().get(follow) == null) return;
			SceneObject object = ctx.scene().spawn(id, kind, texture);
			Vec3 base = relative ? pos.scale(ctx.domain().radius) : pos;
			if (atEvent) base = ctx.eventPos().subtract(ctx.domain().center).add(base);
			object.pos = base;
			object.prevPos = object.pos;
			object.followId = follow;
			object.followOffset = offset;
			object.litBy = litBy;
			object.scale = scale;
			object.color = color;
			object.alpha = alpha;
			object.spin = spin;
			object.tilt = tilt;
			object.emissive = emissive;
			if (ring != null) {
				SceneObject ringObject = ctx.scene().spawn(id + "_ring", SceneObject.Kind.RING, Identifier.parse(str(ring, "texture", "skysdomains:scene/ring_saturn")));
				ringObject.followId = id;
				ringObject.scale = number(ring, "scale", scale * 1.8F);
				ringObject.tilt = number(ring, "tilt", 20.0F);
				ringObject.alpha = number(ring, "alpha", 0.9F);
				ringObject.emissive = emissive;
			}
		};
	}

	private static SequenceAction orbit(JsonObject json) {
		String id = str(json, "id", "object");
		String around = str(json, "around", "sun");
		float radius = number(json, "radius", 3.0F);
		int period = integer(json, "period", 120);
		float phase = number(json, "phase", 0.0F);
		float tilt = number(json, "tilt", 0.0F);
		boolean relative = bool(json, "relative_to_radius", false);
		return (ctx, instant) -> {
			SceneObject object = ctx.scene().get(id);
			if (object != null) object.setOrbit(around, relative ? (float) (radius * ctx.domain().radius) : radius, period, phase, tilt);
		};
	}

	private static SequenceAction move(JsonObject json) {
		String id = str(json, "id", "object");
		@Nullable Vec3 pos = json.has("pos") ? vec3(json, "pos", Vec3.ZERO) : null;
		boolean toEvent = json.has("to_event") && json.get("to_event").getAsBoolean();
		@Nullable Float scale = json.has("scale") ? number(json, "scale", 1.0F) : null;
		@Nullable Float alpha = json.has("alpha") ? number(json, "alpha", 1.0F) : null;
		int duration = integer(json, "duration", 20);
		boolean relative = bool(json, "relative_to_radius", false);
		@Nullable Float spin = json.has("spin") ? number(json, "spin", 0.0F) : null;
		Easing easing = Easing.parse(str(json, "easing", null), Easing.EASE_IN_OUT_CUBIC);
		return (ctx, instant) -> {
			SceneObject object = ctx.scene().get(id);
			if (object == null) return;
			Vec3 target = toEvent ? ctx.eventPos().subtract(ctx.domain().center) : (pos != null && relative ? pos.scale(ctx.domain().radius) : pos);
			if (spin != null) object.spin = spin;
			object.tweenTo(target, scale, alpha, instant ? 0 : duration, easing);
		};
	}

	private static SequenceAction orbitCollapse(JsonObject json) {
		String target = str(json, "target", "sun");
		int duration = integer(json, "duration", 30);
		Easing easing = Easing.parse(str(json, "easing", null), Easing.EASE_IN_CUBIC);
		return (ctx, instant) -> ctx.scene().collapseOrbits(target, instant ? 0 : duration, easing);
	}

	private static SequenceAction remove(JsonObject json) {
		String id = str(json, "id", "*");
		return (ctx, instant) -> {
			if ("*".equals(id)) ctx.scene().removeAll();
			else ctx.scene().remove(id);
		};
	}

	// --- particles ----------------------------------------------------------------------------

	private static SequenceAction particles(JsonObject json) {
		String particle = str(json, "particle", "minecraft:end_rod");
		int count = integer(json, "count", 10);
		String area = str(json, "area", "sphere");
		float speed = number(json, "speed", 0.02F);
		float spread = number(json, "spread", 1.0F);
		int color = color(json, "color", -1);
		int colorTo = color(json, "color_to", -1);
		float size = number(json, "size", 1.0F);
		Identifier particleId = Identifier.parse(particle);
		return (ctx, instant) -> {
			if (instant || ctx.client().level == null) return;
			ParticleOptions options = resolveParticle(particleId, color, colorTo, size);
			if (options == null) return;
			Vec3 center = ctx.domain().center;
			double radius = ctx.domain().radius * ctx.scene().sphereScaleNow();
			for (int i = 0; i < count; i++) {
				double x;
				double y;
				double z;
				double vx = (RANDOM.nextDouble() - 0.5) * speed;
				double vy = (RANDOM.nextDouble() - 0.5) * speed;
				double vz = (RANDOM.nextDouble() - 0.5) * speed;
				switch (area) {
					case "center" -> {
						x = center.x + (RANDOM.nextDouble() - 0.5) * spread;
						y = center.y + 1 + (RANDOM.nextDouble() - 0.5) * spread;
						z = center.z + (RANDOM.nextDouble() - 0.5) * spread;
					}
					case "ground" -> {
						double angle = RANDOM.nextDouble() * Math.PI * 2;
						double distance = Math.sqrt(RANDOM.nextDouble()) * radius;
						x = center.x + Math.cos(angle) * distance;
						y = center.y + 0.1 + RANDOM.nextDouble() * 0.5;
						z = center.z + Math.sin(angle) * distance;
						vy = Math.abs(vy) + speed;
					}
					case "rise" -> {
						double angle = RANDOM.nextDouble() * Math.PI * 2;
						double distance = Math.sqrt(RANDOM.nextDouble()) * radius;
						x = center.x + Math.cos(angle) * distance;
						y = center.y + RANDOM.nextDouble() * radius * 0.5;
						z = center.z + Math.sin(angle) * distance;
						vx = 0;
						vz = 0;
						vy = speed + RANDOM.nextDouble() * speed;
					}
					case "shell" -> {
						Vec3 dir = randomDirection();
						x = center.x + dir.x * radius;
						y = center.y + dir.y * radius;
						z = center.z + dir.z * radius;
						vx = dir.x * speed;
						vy = dir.y * speed;
						vz = dir.z * speed;
					}
					case "event" -> {
						x = ctx.eventPos().x + (RANDOM.nextDouble() - 0.5) * spread;
						y = ctx.eventPos().y + (RANDOM.nextDouble() - 0.5) * spread;
						z = ctx.eventPos().z + (RANDOM.nextDouble() - 0.5) * spread;
						Vec3 dir = randomDirection();
						vx = dir.x * speed;
						vy = dir.y * speed;
						vz = dir.z * speed;
					}
					case "ring" -> {
						// Flat ring of radius `spread` around the event, flying outwards.
						double angle = (double) i / count * Math.PI * 2 + RANDOM.nextDouble() * 0.2;
						x = ctx.eventPos().x + Math.cos(angle) * spread;
						y = ctx.eventPos().y + 0.2 + RANDOM.nextDouble() * 0.4;
						z = ctx.eventPos().z + Math.sin(angle) * spread;
						vx = Math.cos(angle) * speed;
						vy = speed * 0.15;
						vz = Math.sin(angle) * speed;
					}
					case "column" -> {
						// Vertical pillar of light at the event, rising.
						double angle = RANDOM.nextDouble() * Math.PI * 2;
						double dist = RANDOM.nextDouble() * spread * 0.3;
						x = ctx.eventPos().x + Math.cos(angle) * dist;
						y = ctx.eventPos().y + RANDOM.nextDouble() * spread * 2.5;
						z = ctx.eventPos().z + Math.sin(angle) * dist;
						vx = 0;
						vz = 0;
						vy = speed + RANDOM.nextDouble() * speed;
					}
					case "spiral" -> {
						// Helix around the centre axis, used for activation vortices.
						double t = (double) i / count;
						double angle = t * Math.PI * 6 + ctx.domain().phaseAge * 0.3;
						double dist = spread * (0.3 + 0.7 * t);
						x = center.x + Math.cos(angle) * dist;
						y = center.y + t * spread * 1.5;
						z = center.z + Math.sin(angle) * dist;
						vx = -Math.sin(angle) * speed;
						vy = speed * 0.5;
						vz = Math.cos(angle) * speed;
					}
					case "implode" -> {
						// Particles on the shell flying towards the centre.
						Vec3 dir = randomDirection();
						x = center.x + dir.x * radius;
						y = center.y + dir.y * radius;
						z = center.z + dir.z * radius;
						vx = -dir.x * speed;
						vy = -dir.y * speed;
						vz = -dir.z * speed;
					}
					default -> {
						Vec3 dir = randomDirection();
						double distance = Math.cbrt(RANDOM.nextDouble()) * radius;
						x = center.x + dir.x * distance;
						y = center.y + dir.y * distance;
						z = center.z + dir.z * distance;
					}
				}
				ctx.client().level.addParticle(options, x, y, z, vx, vy, vz);
			}
		};
	}

	/** Simple particle types as-is; {@code dust} / {@code dust_color_transition} with colour and size. */
	@Nullable
	private static ParticleOptions resolveParticle(Identifier id, int color, int colorTo, float size) {
		ParticleType<?> type = BuiltInRegistries.PARTICLE_TYPE.getValue(id);
		if (type == ParticleTypes.DUST) {
			return new DustParticleOptions(color < 0 ? 0xFFFFFF : color, size);
		}
		if (type == ParticleTypes.DUST_COLOR_TRANSITION) {
			int from = color < 0 ? 0xFFFFFF : color;
			return new DustColorTransitionOptions(from, colorTo < 0 ? from : colorTo, size);
		}
		if (type == ParticleTypes.ENTITY_EFFECT) {
			return ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, 0xFF000000 | (color < 0 ? 0xFFFFFF : color));
		}
		return type instanceof SimpleParticleType simple ? simple : null;
	}

	private static Vec3 randomDirection() {
		double theta = RANDOM.nextDouble() * Math.PI * 2;
		double u = RANDOM.nextDouble() * 2 - 1;
		double s = Math.sqrt(1 - u * u);
		return new Vec3(s * Math.cos(theta), u, s * Math.sin(theta));
	}

	// --- json helpers -------------------------------------------------------------------------

	static boolean bool(JsonObject json, String key, boolean fallback) {
		JsonElement element = json.get(key);
		return element == null || element.isJsonNull() ? fallback : element.getAsBoolean();
	}

	static String str(JsonObject json, String key, String fallback) {
		JsonElement element = json.get(key);
		return element == null || element.isJsonNull() ? fallback : element.getAsString();
	}

	static int integer(JsonObject json, String key, int fallback) {
		JsonElement element = json.get(key);
		return element == null || element.isJsonNull() ? fallback : element.getAsInt();
	}

	static float number(JsonObject json, String key, float fallback) {
		JsonElement element = json.get(key);
		return element == null || element.isJsonNull() ? fallback : element.getAsFloat();
	}

	static Vec3 vec3(JsonObject json, String key, Vec3 fallback) {
		JsonElement element = json.get(key);
		if (element == null || !element.isJsonArray()) return fallback;
		JsonArray array = element.getAsJsonArray();
		if (array.size() < 3) return fallback;
		return new Vec3(array.get(0).getAsDouble(), array.get(1).getAsDouble(), array.get(2).getAsDouble());
	}

	/** Accepts "#RRGGBB", "RRGGBB" or a number. */
	static int color(JsonObject json, String key, int fallback) {
		JsonElement element = json.get(key);
		if (element == null || element.isJsonNull()) return fallback;
		if (element.getAsJsonPrimitive().isNumber()) return element.getAsInt();
		String text = element.getAsString().trim().toLowerCase(Locale.ROOT);
		if (text.startsWith("#")) text = text.substring(1);
		if (text.startsWith("0x")) text = text.substring(2);
		try {
			return (int) Long.parseLong(text, 16) & 0xFFFFFF;
		} catch (NumberFormatException e) {
			return fallback;
		}
	}
}
