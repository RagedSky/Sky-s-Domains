package dev.sky.skysdomains.common;

import dev.sky.skysdomains.SkysDomains;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

/**
 * Hooks the data-driven placed features ({@code data/skysdomains/worldgen/placed_feature}) into
 * the vanilla biomes via the Fabric biome modification API.
 */
public final class ModWorldgen {
	public static final ResourceKey<PlacedFeature> ORE_ARCANE = placed("ore_arcane");
	public static final ResourceKey<PlacedFeature> ORE_ABYSSAL = placed("ore_abyssal");
	public static final ResourceKey<PlacedFeature> ORE_VOID = placed("ore_void");

	private ModWorldgen() {
	}

	private static ResourceKey<PlacedFeature> placed(String path) {
		return ResourceKey.create(Registries.PLACED_FEATURE, SkysDomains.id(path));
	}

	public static void register() {
		// Overworld, Y -64..16 (height range is in the placed feature JSON).
		BiomeModifications.addFeature(BiomeSelectors.foundInOverworld(), GenerationStep.Decoration.UNDERGROUND_ORES, ORE_ARCANE);
		// Nether, all nether biomes, Y 8..100.
		BiomeModifications.addFeature(BiomeSelectors.foundInTheNether(), GenerationStep.Decoration.UNDERGROUND_DECORATION, ORE_ABYSSAL);
		// End: outer islands only (never the central island biome).
		BiomeModifications.addFeature(
				BiomeSelectors.foundInTheEnd().and(BiomeSelectors.excludeByKey(Biomes.THE_END)),
				GenerationStep.Decoration.UNDERGROUND_ORES, ORE_VOID);
	}
}
