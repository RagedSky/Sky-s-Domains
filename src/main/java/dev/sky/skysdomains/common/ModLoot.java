package dev.sky.skysdomains.common;

import dev.sky.skysdomains.SkysDomains;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.NestedLootTable;

/**
 * Injects extra loot pools into vanilla loot tables. The vanilla pools stay untouched; each
 * injected pool references one of our own loot tables under {@code data/skysdomains/loot_table/inject},
 * where the drop chances live (so they can be tweaked by data packs):
 * <ul>
 *   <li>Wither: 85 % one Cursed Fragment (independent of Looting).</li>
 *   <li>Warden: 90 % one Echoed Matter (independent of Looting).</li>
 *   <li>Nether fortress chests: Sealing Template; End city chests: Absolute Template;
 *       Ancient city chests: Transcendent Template.</li>
 * </ul>
 */
public final class ModLoot {
	private static final ResourceKey<LootTable> NETHER_BRIDGE = vanilla("chests/nether_bridge");
	private static final ResourceKey<LootTable> END_CITY_TREASURE = vanilla("chests/end_city_treasure");
	private static final ResourceKey<LootTable> ANCIENT_CITY = vanilla("chests/ancient_city");

	private static final ResourceKey<LootTable> INJECT_WITHER = inject("wither");
	private static final ResourceKey<LootTable> INJECT_WARDEN = inject("warden");
	private static final ResourceKey<LootTable> INJECT_NETHER_BRIDGE = inject("nether_bridge");
	private static final ResourceKey<LootTable> INJECT_END_CITY = inject("end_city");
	private static final ResourceKey<LootTable> INJECT_ANCIENT_CITY = inject("ancient_city");

	private ModLoot() {
	}

	private static ResourceKey<LootTable> vanilla(String path) {
		return ResourceKey.create(Registries.LOOT_TABLE, Identifier.withDefaultNamespace(path));
	}

	private static ResourceKey<LootTable> inject(String path) {
		return ResourceKey.create(Registries.LOOT_TABLE, SkysDomains.id("inject/" + path));
	}

	public static void register() {
		LootTableEvents.MODIFY.register((key, tableBuilder, source, provider) -> {
			if (!source.isBuiltin()) return;

			if (EntityTypes.WITHER.getDefaultLootTable().orElse(null) == key) {
				addReference(tableBuilder, provider, INJECT_WITHER);
			} else if (EntityTypes.WARDEN.getDefaultLootTable().orElse(null) == key) {
				addReference(tableBuilder, provider, INJECT_WARDEN);
			} else if (NETHER_BRIDGE.equals(key)) {
				addReference(tableBuilder, provider, INJECT_NETHER_BRIDGE);
			} else if (END_CITY_TREASURE.equals(key)) {
				addReference(tableBuilder, provider, INJECT_END_CITY);
			} else if (ANCIENT_CITY.equals(key)) {
				addReference(tableBuilder, provider, INJECT_ANCIENT_CITY);
			}
		});
	}

	private static void addReference(LootTable.Builder tableBuilder, HolderLookup.Provider provider, ResourceKey<LootTable> table) {
		tableBuilder.withPool(LootPool.lootPool().add(NestedLootTable.lootTableReference(provider.getOrThrow(table))));
	}
}
