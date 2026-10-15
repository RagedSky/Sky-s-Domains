package dev.sky.skysdomains.client.hud;

import dev.sky.skysdomains.DomainType;
import dev.sky.skysdomains.SkysDomains;
import dev.sky.skysdomains.common.TalismanItem;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudStatusBarHeightRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.item.ItemStack;

/**
 * Energy bar above the hotbar (left side, reserves its own status-bar row). Shows the energy,
 * the cooldown and, while the own domain is open, its remaining time. Only visible while a
 * talisman is in the offhand or energy is not full.
 */
public final class EnergyHud {
	public static final Identifier ID = SkysDomains.id("energy_bar");
	private static final int WIDTH = 81;
	private static final int HEIGHT = 5;

	private EnergyHud() {
	}

	public static void register() {
		HudStatusBarHeightRegistry.addLeft(ID, player -> shouldShow(Minecraft.getInstance()) ? 10 : 0);
		HudElementRegistry.attachElementAfter(VanillaHudElements.ARMOR_BAR, ID, EnergyHud::extract);
	}

	private static boolean shouldShow(Minecraft client) {
		if (client.player == null || client.gui.hud.isHidden()) return false;
		if (client.player.isSpectator()) return false;
		return client.player.getOffhandItem().getItem() instanceof TalismanItem
				|| HudState.energy < HudState.maxEnergy - 0.01F
				|| HudState.cooldownTicks > 0;
	}

	private static void extract(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft client = Minecraft.getInstance();
		if (!shouldShow(client)) return;

		int x = graphics.guiWidth() / 2 - 91;
		int y = graphics.guiHeight() - HudStatusBarHeightRegistry.getHeight(ID) + 2;

		float fraction = Math.max(0.0F, Math.min(1.0F, HudState.displayedEnergy() / HudState.maxEnergy));
		int fill = Math.round(fraction * (WIDTH - 2));

		ItemStack offhand = client.player.getOffhandItem();
		DomainType domain = TalismanItem.domainOf(offhand);
		int barColor = domain == null ? 0xFF8A8A8A : colorOf(domain);
		boolean onCooldown = HudState.cooldownTicks > 0;

		graphics.fill(x, y, x + WIDTH, y + HEIGHT, 0xA0000000);
		graphics.fill(x + 1, y + 1, x + 1 + fill, y + HEIGHT - 1, onCooldown ? 0xFF5A5A5A : barColor);
		graphics.fill(x, y, x + WIDTH, y + 1, 0xFF1A1A1A);
		graphics.fill(x, y + HEIGHT - 1, x + WIDTH, y + HEIGHT, 0xFF1A1A1A);

		String label;
		if (HudState.ownDomainRemainingTicks > 0) {
			label = Component.translatable("hud.skysdomains.active", String.format("%.1f", HudState.ownDomainRemainingTicks / 20.0)).getString();
		} else if (onCooldown) {
			label = Component.translatable("hud.skysdomains.cooldown", String.format("%.0f", Math.ceil(HudState.cooldownTicks / 20.0))).getString();
		} else {
			label = Math.round(HudState.energy) + " / " + Math.round(HudState.maxEnergy);
		}
		graphics.pose().pushMatrix();
		graphics.pose().scale(0.5F, 0.5F);
		graphics.text(client.font, label, (x + WIDTH + 3) * 2, (y - 1) * 2, ARGB.color(255, 0xFFFFFF));
		graphics.pose().popMatrix();
	}

	public static int colorOf(DomainType domain) {
		return switch (domain) {
			case FROST_VAULT -> 0xFF7FE3FF;
			case UMBRAL_SEA -> 0xFF5B3A8C;
			case GLASS_HEAVEN -> 0xFFF2D27A;
			case EMBER_GARDEN -> 0xFFFF6A2B;
			case STASIS -> 0xFFB8B8B8;
			case STRUCTURAL_COLLAPSE -> 0xFFF0F0FF;
		};
	}
}
