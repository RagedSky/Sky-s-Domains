package dev.sky.skysdomains.config;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Plain data holder serialised with Gson to {@code config/skysdomains.json}.
 * Every field has a sensible default; missing fields in an existing file keep their defaults.
 *
 * <p>Per-tier arrays always have four entries (Fractured, Sealed, Absolute, Transcendent).
 */
public class SkysDomainsConfig {
	/** Default key (SDL scancode name without the SDL_SCANCODE_ prefix, e.g. "R", "G", "V"). */
	public String keybindDefault = "R";

	public Energy energy = new Energy();
	public Tiers tiers = new Tiers();
	public Domains domains = new Domains();
	public Targeting targeting = new Targeting();
	public Accessibility accessibility = new Accessibility();
	public boolean debugLogging = false;

	public static class Energy {
		public float max = 100.0F;
		/** Energy regenerated per second without a talisman in the offhand. */
		public float regenPerSecond = 1.5F;
		/** Seconds after activating a domain during which no energy regenerates. */
		public float regenDelaySeconds = 4.0F;
	}

	public static class Tiers {
		public TierValues fractured = new TierValues(8.0, 10.0, 60.0, 1.0, 40.0);
		public TierValues sealed = new TierValues(11.0, 13.0, 50.0, 1.35, 50.0);
		public TierValues absolute = new TierValues(15.0, 16.0, 40.0, 1.8, 60.0);
		public TierValues transcendent = new TierValues(20.0, 20.0, 30.0, 2.5, 70.0);

		public TierValues byIndex(int index) {
			return switch (index) {
				case 1 -> sealed;
				case 2 -> absolute;
				case 3 -> transcendent;
				default -> fractured;
			};
		}
	}

	public static class TierValues {
		public double radius;
		public double durationSeconds;
		public double cooldownSeconds;
		/** Energy regeneration multiplier while this talisman is in the offhand. */
		public double regenMultiplier;
		public double energyCost;

		public TierValues() {
		}

		public TierValues(double radius, double durationSeconds, double cooldownSeconds, double regenMultiplier, double energyCost) {
			this.radius = radius;
			this.durationSeconds = durationSeconds;
			this.cooldownSeconds = cooldownSeconds;
			this.regenMultiplier = regenMultiplier;
			this.energyCost = energyCost;
		}
	}

	/** Radius and duration bonus per refinement (0.10 = +10 %). */
	public double refinementBonus = 0.10;

	public static class Domains {
		public FrostVault frostVault = new FrostVault();
		public UmbralSea umbralSea = new UmbralSea();
		public GlassHeaven glassHeaven = new GlassHeaven();
		public EmberGarden emberGarden = new EmberGarden();
		public Stasis stasis = new Stasis();
		public StructuralCollapse structuralCollapse = new StructuralCollapse();
		/** Blocks Ember Garden / Frost Vault never replace (bedrock, containers and unbreakable blocks are skipped anyway). */
		public java.util.List<String> groundBlacklist = new java.util.ArrayList<>(java.util.List.of(
				"minecraft:obsidian", "minecraft:crying_obsidian", "minecraft:reinforced_deepslate", "minecraft:spawner",
				"minecraft:end_portal_frame", "minecraft:command_block", "minecraft:barrier"));
	}

	public static class FrostVault {
		public double durationMultiplier = 1.0;
		/** Freeze ticks added per game tick (powder snow adds 1). */
		public int[] freezeTicksPerTick = {3, 4, 5, 7};
		/** Freeze damage per second once fully frozen. */
		public float[] freezeDamagePerSecond = {1.0F, 1.5F, 2.0F, 3.0F};
		/** Slowness amplifier (0 = Slowness I). */
		public int[] slownessAmplifier = {1, 2, 3, 4};
		public boolean convertWaterToIce = true;
		public boolean convertLavaToObsidian = true;
		/** Radius multiplier applied to the tier radius. */
		public double radiusMultiplier = 1.0;
		/** Freeze the floor inside the sphere into ice / packed ice / blue ice while open (always thawed back). */
		public boolean freezeGround = true;
		/** Ice stalagmites shooting out of the frozen floor (needs freezeGround). */
		public boolean iceSpikes = true;
		/** Share of floor columns that try to grow a spike (0..1); big ones keep extra spacing. */
		public double spikeDensity = 0.07;
		/** Tallest spike in blocks (2..12). */
		public int maxSpikeHeight = 8;
		/** Ticks until the frost has reached the full radius. */
		public int spreadTicks = 45;
	}

	public static class UmbralSea {
		public double durationMultiplier = 1.0;
		/** Pull speed towards the centre in blocks per tick. */
		public double[] pullStrength = {0.04, 0.06, 0.08, 0.11};
		/** Armour-ignoring magic damage per second at the centre. */
		public float[] centerDamagePerSecond = {2.0F, 3.0F, 4.5F, 6.0F};
		/** Radius around the centre where the damage applies. */
		public double centerRadius = 2.0;
		public boolean darknessForOtherPlayers = true;
	}

	public static class GlassHeaven {
		/** Defensive domain: lasts longer than the attack domains. */
		public double durationMultiplier = 1.8;
		public int resistanceAmplifier = 1;
		public int regenerationAmplifier = 1;
		public int weaknessAmplifier = 1;
		public boolean shatterProjectiles = true;
	}

	public static class EmberGarden {
		public double durationMultiplier = 1.0;
		public float[] fireDamagePerSecond = {1.5F, 2.5F, 3.5F, 5.0F};
		public double radiusMultiplier = 1.0;
		/** Turn the floor inside the sphere into scorched/molten ground while the domain is open (always restored). */
		public boolean infectGround = true;
		/** Small lava pools near the centre (only with infectGround). */
		public boolean lavaPools = true;
		/** Ticks until the infection has reached the full radius. */
		public int spreadTicks = 60;
		/** Damage enemies take per step on the infected ground (fire damage, they also catch fire). */
		public float groundDamage = 1.0F;
	}

	public static class Stasis {
		public double durationMultiplier = 1.0;
		public double radiusMultiplier = 1.1;
		/** Whether other players inside the sphere are frozen as well. */
		public boolean freezePlayers = false;
		public boolean deleteProjectiles = true;
	}

	public static class StructuralCollapse {
		/** Planets thrown per tier (max 8). */
		public int[] planetsPerTier = {3, 4, 6, 8};
		/** Tier 4 only: after the last planet the sun itself falls onto the centre. */
		public boolean sunAtTier4 = true;
		/** Explosion power per planet. Damage only, see blockDamage. */
		public Map<String, Float> explosionPower = defaultPowers();
		/** Explosion power of the final sun impact. */
		public float sunExplosionPower = 10.0F;
		/** Real block-destroying explosions (vanilla TNT behaviour). Off by default. */
		public boolean blockDamage = false;
		/** Ticks between two planet launches. */
		public int impactIntervalTicks = 10;
		/** Flight time of a planet from the white hole to its target. */
		public int planetFlightTicks = 12;
		/** Phase lengths in ticks (activation, expand, solar system, collapse, end). */
		public int activationTicks = 30;
		public int expandTicks = 20;
		public int solarSystemTicks = 50;
		public int collapseTicks = 30;
		public int endTicks = 30;

		private static Map<String, Float> defaultPowers() {
			Map<String, Float> map = new LinkedHashMap<>();
			map.put("mercury", 2.0F);
			map.put("venus", 3.0F);
			map.put("earth", 3.5F);
			map.put("mars", 3.0F);
			map.put("jupiter", 6.0F);
			map.put("saturn", 5.5F);
			map.put("uranus", 4.5F);
			map.put("neptune", 4.5F);
			return map;
		}
	}

	public static class Targeting {
		/** Other players are enemies by default. */
		public boolean playersAreEnemies = true;
		/** Players on the same scoreboard team are never targeted. */
		public boolean respectTeams = true;
		/** Only mobs implementing the hostile marker interface are targeted (false = every non-allied mob). */
		public boolean onlyHostileMobs = false;
		/** Pets owned by the domain owner are never targeted. */
		public boolean spareOwnPets = true;
	}

	public static class Accessibility {
		/** Hard white/invert frames become soft fades. */
		public boolean reducedFlashes = false;
		/** Camera shake strength multiplier (0 disables). */
		public float shakeMultiplier = 1.0F;
	}

	/** Returns the explosion power configured for a planet, with a sane fallback. */
	public float explosionPower(String planet) {
		Float value = domains.structuralCollapse.explosionPower.get(planet);
		return value == null ? 3.0F : value;
	}
}
