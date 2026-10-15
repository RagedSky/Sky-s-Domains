package dev.sky.skysdomains.common;

import dev.sky.skysdomains.DomainType;
import dev.sky.skysdomains.TalismanTier;
import dev.sky.skysdomains.config.ConfigManager;
import dev.sky.skysdomains.config.SkysDomainsConfig;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Effective values of a talisman: tier config, refinements and per-domain multipliers folded
 * together. Used by the server logic and by the client tooltip (same config on both sides in
 * single player; on servers the tooltip shows the client's config copy).
 */
public record TalismanStats(TalismanTier tier, @Nullable DomainType domain, int refinements,
							double radius, int durationTicks, int cooldownTicks, double energyCost, double regenMultiplier) {

	public static TalismanStats of(ItemStack stack) {
		TalismanTier tier = TalismanItem.tierOf(stack);
		if (tier == null) tier = TalismanTier.FRACTURED;
		return of(tier, TalismanItem.domainOf(stack), TalismanItem.refinementsOf(stack));
	}

	public static TalismanStats of(TalismanTier tier, @Nullable DomainType domain, int refinements) {
		SkysDomainsConfig config = ConfigManager.get();
		SkysDomainsConfig.TierValues values = config.tiers.byIndex(tier.index());
		double refinementFactor = 1.0 + refinements * config.refinementBonus;

		double radius = values.radius * refinementFactor * radiusMultiplier(config, domain);
		double duration = values.durationSeconds * refinementFactor * durationMultiplier(config, domain);
		int durationTicks = (int) Math.round(duration * 20.0);
		int cooldownTicks = (int) Math.round(values.cooldownSeconds * 20.0);
		return new TalismanStats(tier, domain, refinements, radius, durationTicks, cooldownTicks, values.energyCost, values.regenMultiplier);
	}

	private static double radiusMultiplier(SkysDomainsConfig config, @Nullable DomainType domain) {
		if (domain == null) return 1.0;
		return switch (domain) {
			case FROST_VAULT -> config.domains.frostVault.radiusMultiplier;
			case EMBER_GARDEN -> config.domains.emberGarden.radiusMultiplier;
			case STASIS -> config.domains.stasis.radiusMultiplier;
			default -> 1.0;
		};
	}

	private static double durationMultiplier(SkysDomainsConfig config, @Nullable DomainType domain) {
		if (domain == null) return 1.0;
		return switch (domain) {
			case FROST_VAULT -> config.domains.frostVault.durationMultiplier;
			case UMBRAL_SEA -> config.domains.umbralSea.durationMultiplier;
			case GLASS_HEAVEN -> config.domains.glassHeaven.durationMultiplier;
			case EMBER_GARDEN -> config.domains.emberGarden.durationMultiplier;
			case STASIS -> config.domains.stasis.durationMultiplier;
			case STRUCTURAL_COLLAPSE -> 1.0;
		};
	}
}
