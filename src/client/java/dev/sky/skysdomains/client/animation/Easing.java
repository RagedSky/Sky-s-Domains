package dev.sky.skysdomains.client.animation;

import java.util.Locale;

/** Easing functions for tweens. Names match the {@code easing} field in sequence files. */
public enum Easing {
	LINEAR, EASE_IN_QUAD, EASE_OUT_QUAD, EASE_IN_OUT_QUAD, EASE_IN_CUBIC, EASE_OUT_CUBIC, EASE_IN_OUT_CUBIC,
	EASE_IN_EXPO, EASE_OUT_EXPO, EASE_IN_OUT_EXPO, EASE_OUT_BACK, EASE_OUT_ELASTIC, EASE_OUT_BOUNCE, EASE_IN_BACK;

	public float apply(float t) {
		t = Math.max(0.0F, Math.min(1.0F, t));
		return switch (this) {
			case LINEAR -> t;
			case EASE_IN_QUAD -> t * t;
			case EASE_OUT_QUAD -> 1 - (1 - t) * (1 - t);
			case EASE_IN_OUT_QUAD -> t < 0.5F ? 2 * t * t : 1 - (float) Math.pow(-2 * t + 2, 2) / 2;
			case EASE_IN_CUBIC -> t * t * t;
			case EASE_OUT_CUBIC -> 1 - (float) Math.pow(1 - t, 3);
			case EASE_IN_OUT_CUBIC -> t < 0.5F ? 4 * t * t * t : 1 - (float) Math.pow(-2 * t + 2, 3) / 2;
			case EASE_IN_EXPO -> t == 0 ? 0 : (float) Math.pow(2, 10 * t - 10);
			case EASE_OUT_EXPO -> t == 1 ? 1 : 1 - (float) Math.pow(2, -10 * t);
			case EASE_IN_OUT_EXPO -> t == 0 ? 0 : t == 1 ? 1 : t < 0.5F
					? (float) Math.pow(2, 20 * t - 10) / 2 : (2 - (float) Math.pow(2, -20 * t + 10)) / 2;
			case EASE_OUT_BACK -> {
				float c1 = 1.70158F;
				float c3 = c1 + 1;
				yield 1 + c3 * (float) Math.pow(t - 1, 3) + c1 * (float) Math.pow(t - 1, 2);
			}
			case EASE_IN_BACK -> {
				float c1 = 1.70158F;
				float c3 = c1 + 1;
				yield c3 * t * t * t - c1 * t * t;
			}
			case EASE_OUT_ELASTIC -> {
				if (t == 0 || t == 1) yield t;
				float c4 = (float) (2 * Math.PI / 3);
				yield (float) (Math.pow(2, -10 * t) * Math.sin((t * 10 - 0.75) * c4) + 1);
			}
			case EASE_OUT_BOUNCE -> {
				float n1 = 7.5625F;
				float d1 = 2.75F;
				if (t < 1 / d1) yield n1 * t * t;
				if (t < 2 / d1) { t -= 1.5F / d1; yield n1 * t * t + 0.75F; }
				if (t < 2.5F / d1) { t -= 2.25F / d1; yield n1 * t * t + 0.9375F; }
				t -= 2.625F / d1;
				yield n1 * t * t + 0.984375F;
			}
		};
	}

	public static Easing parse(String name, Easing fallback) {
		if (name == null) return fallback;
		try {
			return valueOf(name.toUpperCase(Locale.ROOT).replace('-', '_'));
		} catch (IllegalArgumentException e) {
			return fallback;
		}
	}
}
