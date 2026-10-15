package dev.sky.skysdomains.client.animation;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * A parsed sequence file ({@code assets/skysdomains/sequences/<domain>.json}).
 *
 * @param sphere default look of the domain sphere
 * @param phases keyframes per phase name
 * @param events actions per server event name (impact, planet_launch, shatter, ...)
 */
public record Sequence(SphereStyle sphere, Map<String, List<Keyframe>> phases, Map<String, List<SequenceAction>> events) {
	public static final Sequence EMPTY = new Sequence(SphereStyle.DEFAULT, Map.of(), Map.of());

	public List<Keyframe> phase(String name) {
		List<Keyframe> list = phases.get(name);
		return list == null ? List.of() : list;
	}

	@Nullable
	public List<SequenceAction> event(String name) {
		return events.get(name);
	}

	/** Visual style of the sphere; every field can be changed at runtime by a {@code sphere_style} action. */
	public static final class SphereStyle {
		public static final SphereStyle DEFAULT = new SphereStyle();

		public String texture = "skysdomains:scene/sphere_default";
		public int color = 0xFFFFFF;
		public float alpha = 0.35F;
		public float insideAlpha = 0.5F;
		public boolean insideOpaque = false;
		public float scroll = 0.01F;
		public boolean emissive = true;

		public SphereStyle copy() {
			SphereStyle copy = new SphereStyle();
			copy.texture = texture;
			copy.color = color;
			copy.alpha = alpha;
			copy.insideAlpha = insideAlpha;
			copy.insideOpaque = insideOpaque;
			copy.scroll = scroll;
			copy.emissive = emissive;
			return copy;
		}
	}
}
