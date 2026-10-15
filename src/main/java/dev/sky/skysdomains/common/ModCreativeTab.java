package dev.sky.skysdomains.common;

import dev.sky.skysdomains.SkysDomains;
import dev.sky.skysdomains.TalismanTier;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** One creative tab with every block and item of the mod. */
public final class ModCreativeTab {
	public static final ResourceKey<CreativeModeTab> KEY = ResourceKey.create(Registries.CREATIVE_MODE_TAB, SkysDomains.id("main"));

	private ModCreativeTab() {
	}

	public static void register() {
		CreativeModeTab tab = FabricCreativeModeTab.builder()
				.title(Component.translatable("itemGroup." + SkysDomains.MOD_ID + ".main"))
				.icon(() -> new ItemStack(ModItems.talisman(TalismanTier.TRANSCENDENT)))
				.displayItems((context, output) -> {
					for (Item item : ModItems.BLOCK_ITEMS) output.accept(item);
					for (Item item : ModItems.ALL) output.accept(item);
				})
				.build();
		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, KEY, tab);
	}
}
