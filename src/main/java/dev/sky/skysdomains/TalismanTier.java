package dev.sky.skysdomains;

import org.jspecify.annotations.Nullable;

/**
 * Talisman tiers. Each tier has its own item ({@code <id>_talisman}) and its own row in the config
 * (radius, duration, cooldown, regeneration multiplier, energy cost).
 */
public enum TalismanTier {
	FRACTURED(1, "fractured"),
	SEALED(2, "sealed"),
	ABSOLUTE(3, "absolute"),
	TRANSCENDENT(4, "transcendent");

	private final int level;
	private final String id;

	TalismanTier(int level, String id) {
		this.level = level;
		this.id = id;
	}

	/** 1..4 */
	public int level() {
		return level;
	}

	/** 0..3, handy for per-tier config arrays. */
	public int index() {
		return level - 1;
	}

	public String id() {
		return id;
	}

	public String itemName() {
		return id + "_talisman";
	}

	public String translationKey() {
		return "tier." + SkysDomains.MOD_ID + "." + id;
	}

	@Nullable
	public static TalismanTier byLevel(int level) {
		for (TalismanTier tier : values()) {
			if (tier.level == level) return tier;
		}
		return null;
	}

	public static TalismanTier byLevelOrFirst(int level) {
		TalismanTier tier = byLevel(level);
		return tier == null ? FRACTURED : tier;
	}
}
