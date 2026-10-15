package dev.sky.skysdomains.client;

import dev.sky.skysdomains.DomainType;
import dev.sky.skysdomains.SkysDomains;
import dev.sky.skysdomains.TalismanTier;
import dev.sky.skysdomains.client.animation.SequenceLoader;
import dev.sky.skysdomains.client.fx.CameraEffects;
import dev.sky.skysdomains.client.fx.OverlayManager;
import dev.sky.skysdomains.client.hud.EnergyHud;
import dev.sky.skysdomains.common.ModItems;
import dev.sky.skysdomains.common.SigilItem;
import dev.sky.skysdomains.common.TalismanItem;
import dev.sky.skysdomains.common.TalismanStats;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/** Client entrypoint: key binding, HUD, overlays, scene rendering, sequences, tooltips, packets. */
public final class SkysDomainsClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ClientNetworking.register();
		KeyHandler.register();
		SequenceLoader.register();
		EnergyHud.register();
		HudElementRegistry.addLast(SkysDomains.id("overlays"), OverlayManager.get()::extract);

		ClientTickEvents.END_CLIENT_TICK.register(ClientDomainState::tick);
		LevelRenderEvents.COLLECT_SUBMITS.register(ClientDomainState::render);
		LevelExtractionEvents.END_EXTRACTION.register(CameraEffects.get()::afterExtraction);

		registerTooltips();
	}

	private static void registerTooltips() {
		ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> {
			if (stack.getItem() instanceof TalismanItem talisman) {
				TalismanStats stats = TalismanStats.of(stack);
				DomainType domain = stats.domain();
				lines.add(Component.translatable("tooltip.skysdomains.tier", Component.translatable(talisman.tier().translationKey())).withStyle(ChatFormatting.GRAY));
				if (domain == null) {
					lines.add(Component.translatable("tooltip.skysdomains.no_sigil").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
				} else {
					lines.add(Component.translatable("tooltip.skysdomains.domain", Component.translatable(domain.translationKey())).withStyle(ChatFormatting.AQUA));
				}
				lines.add(Component.translatable("tooltip.skysdomains.refinements", stats.refinements(), TalismanItem.MAX_REFINEMENTS).withStyle(ChatFormatting.GRAY));
				lines.add(Component.translatable("tooltip.skysdomains.values",
						String.format("%.1f", stats.radius()),
						String.format("%.1f", stats.durationTicks() / 20.0),
						String.format("%.0f", stats.cooldownTicks() / 20.0),
						String.format("%.0f", stats.energyCost())).withStyle(ChatFormatting.DARK_AQUA));
				lines.add(Component.translatable("tooltip.skysdomains.regen", String.format("%.1f", stats.regenMultiplier())).withStyle(ChatFormatting.DARK_GRAY));
				lines.add(Component.translatable("tooltip.skysdomains.usage").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
			} else if (stack.getItem() instanceof SigilItem sigil) {
				lines.add(Component.translatable("tooltip.skysdomains.sigil", Component.translatable(sigil.domain().translationKey())).withStyle(ChatFormatting.AQUA));
				lines.add(Component.translatable("tooltip.skysdomains.sigil_usage").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
			} else if (stack.is(ModItems.REFINEMENT)) {
				lines.add(Component.translatable("tooltip.skysdomains.refinement").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
			} else if (stack.is(ModItems.SEALING_CATALYST)) {
				lines.add(Component.translatable("tooltip.skysdomains.catalyst", Component.translatable(TalismanTier.SEALED.translationKey())).withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
			} else if (stack.is(ModItems.ABSOLUTE_CATALYST)) {
				lines.add(Component.translatable("tooltip.skysdomains.catalyst", Component.translatable(TalismanTier.ABSOLUTE.translationKey())).withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
			} else if (stack.is(ModItems.TRANSCENDENT_CATALYST)) {
				lines.add(Component.translatable("tooltip.skysdomains.catalyst", Component.translatable(TalismanTier.TRANSCENDENT.translationKey())).withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
			} else if (stack.is(ModItems.SEALING_TEMPLATE)) {
				templateTooltip(lines, TalismanTier.FRACTURED, TalismanTier.SEALED);
			} else if (stack.is(ModItems.ABSOLUTE_TEMPLATE)) {
				templateTooltip(lines, TalismanTier.SEALED, TalismanTier.ABSOLUTE);
			} else if (stack.is(ModItems.TRANSCENDENT_TEMPLATE)) {
				templateTooltip(lines, TalismanTier.ABSOLUTE, TalismanTier.TRANSCENDENT);
			}
		});
	}

	private static void templateTooltip(java.util.List<Component> lines, TalismanTier from, TalismanTier to) {
		lines.add(Component.translatable("tooltip.skysdomains.template", Component.translatable(from.translationKey()),
				Component.translatable(to.translationKey())).withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
	}
}
