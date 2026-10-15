package dev.sky.skysdomains.common;

import dev.sky.skysdomains.DomainType;
import dev.sky.skysdomains.SkysDomains;
import dev.sky.skysdomains.TalismanTier;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * All items of the mod. Recipes live in {@code data/skysdomains/recipe}.
 */
public final class ModItems {
	/** Every item in creative-tab order (filled while registering). */
	public static final List<Item> ALL = new ArrayList<>();
	/** Block items, appended by {@link ModBlocks}. */
	public static final List<Item> BLOCK_ITEMS = new ArrayList<>();

	// --- ore drops ---
	public static final Item ARCANE_DUST = simple("arcane_dust", new Item.Properties());
	public static final Item ABYSSAL_SHARD = simple("abyssal_shard", new Item.Properties().fireResistant());
	public static final Item VOID_CRYSTAL = simple("void_crystal", new Item.Properties().rarity(Rarity.RARE));

	// --- mob drops ---
	public static final Item CURSED_FRAGMENT = simple("cursed_fragment", new Item.Properties().rarity(Rarity.EPIC).fireResistant());
	public static final Item ECHOED_MATTER = simple("echoed_matter", new Item.Properties().rarity(Rarity.EPIC)
			.component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true));

	// --- intermediates ---
	public static final Item DOMAIN_CORE = simple("domain_core", new Item.Properties().rarity(Rarity.UNCOMMON));
	public static final Item SEALING_INK = simple("sealing_ink", new Item.Properties());
	public static final Item REFINEMENT = simple("refinement", new Item.Properties().rarity(Rarity.UNCOMMON));

	// --- smithing catalysts: bundle the tier-upgrade materials into the single smithing addition slot ---
	public static final Item SEALING_CATALYST = simple("sealing_catalyst", new Item.Properties().rarity(Rarity.UNCOMMON).fireResistant());
	public static final Item ABSOLUTE_CATALYST = simple("absolute_catalyst", new Item.Properties().rarity(Rarity.RARE).fireResistant());
	public static final Item TRANSCENDENT_CATALYST = simple("transcendent_catalyst", new Item.Properties().rarity(Rarity.EPIC).fireResistant());

	// --- smithing templates (plain items; the smithing recipes only need an ingredient) ---
	public static final Item SEALING_TEMPLATE = simple("sealing_template", new Item.Properties().rarity(Rarity.UNCOMMON));
	public static final Item ABSOLUTE_TEMPLATE = simple("absolute_template", new Item.Properties().rarity(Rarity.RARE));
	public static final Item TRANSCENDENT_TEMPLATE = simple("transcendent_template", new Item.Properties().rarity(Rarity.EPIC));

	// --- talismans & sigils ---
	public static final Map<TalismanTier, TalismanItem> TALISMANS = new EnumMap<>(TalismanTier.class);
	public static final Map<DomainType, SigilItem> SIGILS = new EnumMap<>(DomainType.class);

	private ModItems() {
	}

	public static ResourceKey<Item> key(String name) {
		return ResourceKey.create(Registries.ITEM, SkysDomains.id(name));
	}

	private static Item simple(String name, Item.Properties properties) {
		return new Item(properties.setId(key(name)));
	}

	public static void register() {
		registerItem("arcane_dust", ARCANE_DUST);
		registerItem("abyssal_shard", ABYSSAL_SHARD);
		registerItem("void_crystal", VOID_CRYSTAL);
		registerItem("cursed_fragment", CURSED_FRAGMENT);
		registerItem("echoed_matter", ECHOED_MATTER);
		registerItem("domain_core", DOMAIN_CORE);
		registerItem("sealing_ink", SEALING_INK);
		registerItem("refinement", REFINEMENT);
		registerItem("sealing_catalyst", SEALING_CATALYST);
		registerItem("absolute_catalyst", ABSOLUTE_CATALYST);
		registerItem("transcendent_catalyst", TRANSCENDENT_CATALYST);
		registerItem("sealing_template", SEALING_TEMPLATE);
		registerItem("absolute_template", ABSOLUTE_TEMPLATE);
		registerItem("transcendent_template", TRANSCENDENT_TEMPLATE);

		Rarity[] tierRarity = {Rarity.UNCOMMON, Rarity.RARE, Rarity.EPIC, Rarity.EPIC};
		for (TalismanTier tier : TalismanTier.values()) {
			TalismanItem item = new TalismanItem(tier, new Item.Properties()
					.stacksTo(1).fireResistant().rarity(tierRarity[tier.index()]).setId(key(tier.itemName())));
			TALISMANS.put(tier, item);
			registerItem(tier.itemName(), item);
		}
		for (DomainType type : DomainType.values()) {
			SigilItem item = new SigilItem(type, new Item.Properties().rarity(Rarity.RARE).setId(key(type.sigilItemName())));
			SIGILS.put(type, item);
			registerItem(type.sigilItemName(), item);
		}
	}

	private static void registerItem(String name, Item item) {
		Registry.register(BuiltInRegistries.ITEM, key(name), item);
		ALL.add(item);
	}

	public static TalismanItem talisman(TalismanTier tier) {
		return TALISMANS.get(tier);
	}

	public static SigilItem sigil(DomainType type) {
		return SIGILS.get(type);
	}
}
