package dev.sky.skysdomains;

import dev.sky.skysdomains.common.ModBlocks;
import dev.sky.skysdomains.common.ModComponents;
import dev.sky.skysdomains.common.ModCreativeTab;
import dev.sky.skysdomains.common.ModItems;
import dev.sky.skysdomains.common.ModLoot;
import dev.sky.skysdomains.common.ModSounds;
import dev.sky.skysdomains.common.ModWorldgen;
import dev.sky.skysdomains.config.ConfigManager;
import dev.sky.skysdomains.domain.DomainManager;
import dev.sky.skysdomains.domain.GroundJournal;
import dev.sky.skysdomains.domain.EnergyManager;
import dev.sky.skysdomains.network.ModNetworking;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Common (client + dedicated server) entrypoint of Sky's Domains.
 *
 * <p>Registration order matters: data components first (items reference them), then blocks,
 * items, sounds, creative tab, loot modifiers, worldgen, networking and finally the server logic.
 */
public final class SkysDomains implements ModInitializer {
	public static final String MOD_ID = "skysdomains";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		ConfigManager.load();

		ModComponents.register();
		ModBlocks.register();
		ModItems.register();
		ModSounds.register();
		ModCreativeTab.register();
		ModLoot.register();
		ModWorldgen.register();

		ModNetworking.registerCommon();
		EnergyManager.register();
		DomainManager.register();
		GroundJournal.register();

		LOGGER.info("Sky's Domains initialised ({} domains, {} talisman tiers)", DomainType.values().length, TalismanTier.values().length);
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	public static void debug(String message, Object... args) {
		if (ConfigManager.get().debugLogging) {
			LOGGER.info("[debug] " + message, args);
		}
	}
}
