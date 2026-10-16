package dev.sky.skysdomains.common;

import dev.sky.skysdomains.SkysDomains;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

/**
 * Ore blocks plus the temporary Ember Garden ground. Drops, mining levels and world generation
 * of the ores are fully data driven (loot tables, block tags and worldgen JSON under
 * {@code data/skysdomains}). The ground blocks have no item, drop nothing and cannot be mined.
 */
public final class ModBlocks {
	public static final ResourceKey<Block> ARCANE_ORE_KEY = key("arcane_ore");
	public static final ResourceKey<Block> DEEPSLATE_ARCANE_ORE_KEY = key("deepslate_arcane_ore");
	public static final ResourceKey<Block> ABYSSAL_ORE_KEY = key("abyssal_ore");
	public static final ResourceKey<Block> VOID_ORE_KEY = key("void_ore");
	public static final ResourceKey<Block> SCORCHED_GROUND_KEY = key("scorched_ground");
	public static final ResourceKey<Block> MOLTEN_GROUND_KEY = key("molten_ground");

	// Overworld, iron pickaxe. Strength like diamond ore.
	public static final Block ARCANE_ORE = new Block(BlockBehaviour.Properties.of()
			.strength(3.0F, 3.0F).requiresCorrectToolForDrops().sound(SoundType.STONE).setId(ARCANE_ORE_KEY));
	public static final Block DEEPSLATE_ARCANE_ORE = new Block(BlockBehaviour.Properties.of()
			.strength(4.5F, 3.0F).requiresCorrectToolForDrops().sound(SoundType.DEEPSLATE).setId(DEEPSLATE_ARCANE_ORE_KEY));
	// Nether, diamond pickaxe.
	public static final Block ABYSSAL_ORE = new Block(BlockBehaviour.Properties.of()
			.strength(4.0F, 1200.0F).requiresCorrectToolForDrops().sound(SoundType.NETHER_ORE).setId(ABYSSAL_ORE_KEY));
	// End (outer islands only), netherite pickaxe.
	public static final Block VOID_ORE = new Block(BlockBehaviour.Properties.of()
			.strength(6.0F, 1200.0F).requiresCorrectToolForDrops().sound(SoundType.STONE).setId(VOID_ORE_KEY));

	// Ember Garden ground: glowing cracks (scorched) and magma-like (molten). Unbreakable and temporary.
	public static final EmberGroundBlock SCORCHED_GROUND = new EmberGroundBlock(BlockBehaviour.Properties.of()
			.mapColor(MapColor.COLOR_BLACK).strength(-1.0F, 3600000.0F).noLootTable().sound(SoundType.NETHERRACK)
			.lightLevel(state -> 7).emissiveRendering(state -> true).setId(SCORCHED_GROUND_KEY), false);
	public static final EmberGroundBlock MOLTEN_GROUND = new EmberGroundBlock(BlockBehaviour.Properties.of()
			.mapColor(MapColor.FIRE).strength(-1.0F, 3600000.0F).noLootTable().sound(SoundType.BASALT)
			.lightLevel(state -> 12).emissiveRendering(state -> true).setId(MOLTEN_GROUND_KEY), true);

	private ModBlocks() {
	}

	private static ResourceKey<Block> key(String name) {
		return ResourceKey.create(Registries.BLOCK, SkysDomains.id(name));
	}

	public static void register() {
		registerWithItem(ARCANE_ORE_KEY, ARCANE_ORE);
		registerWithItem(DEEPSLATE_ARCANE_ORE_KEY, DEEPSLATE_ARCANE_ORE);
		registerWithItem(ABYSSAL_ORE_KEY, ABYSSAL_ORE);
		registerWithItem(VOID_ORE_KEY, VOID_ORE);
		Registry.register(BuiltInRegistries.BLOCK, SCORCHED_GROUND_KEY, SCORCHED_GROUND);
		Registry.register(BuiltInRegistries.BLOCK, MOLTEN_GROUND_KEY, MOLTEN_GROUND);
	}

	private static void registerWithItem(ResourceKey<Block> key, Block block) {
		Registry.register(BuiltInRegistries.BLOCK, key, block);
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, key.identifier());
		Item item = new BlockItem(block, new Item.Properties().setId(itemKey));
		Registry.register(BuiltInRegistries.ITEM, itemKey, item);
		ModItems.BLOCK_ITEMS.add(item);
	}
}
