package dev.sky.skysdomains.common;

import dev.sky.skysdomains.SkysDomains;
import dev.sky.skysdomains.TalismanTier;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.RegistryOps;
import org.jspecify.annotations.Nullable;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
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

	/** The Domain Codex (written book) as defined by the result of the codex recipe. */
	@Nullable
	private static ItemStack codex(HolderLookup.Provider registries) {
		try (InputStream in = ModCreativeTab.class.getResourceAsStream("/data/skysdomains/recipe/domain_codex.json")) {
			if (in == null) return null;
			JsonObject recipe = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
			RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, registries);
			return ItemStack.CODEC.parse(ops, recipe.get("result")).result().orElse(null);
		} catch (Exception e) {
			SkysDomains.LOGGER.warn("Could not build the Domain Codex for the creative tab: {}", e.toString());
			return null;
		}
	}

	public static void register() {
		CreativeModeTab tab = FabricCreativeModeTab.builder()
				.title(Component.translatable("itemGroup." + SkysDomains.MOD_ID + ".main"))
				.icon(() -> new ItemStack(ModItems.talisman(TalismanTier.TRANSCENDENT)))
				.displayItems((context, output) -> {
					ItemStack codex = codex(context.holders());
					if (codex != null) output.accept(codex);
					for (Item item : ModItems.BLOCK_ITEMS) output.accept(item);
					for (Item item : ModItems.ALL) output.accept(item);
				})
				.build();
		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, KEY, tab);
	}
}
