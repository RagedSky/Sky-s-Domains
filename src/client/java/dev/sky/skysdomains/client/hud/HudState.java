package dev.sky.skysdomains.client.hud;

import dev.sky.skysdomains.network.PlayerStatusPayload;

/** Last HUD status received from the server. */
public final class HudState {
	public static float energy;
	public static float maxEnergy = 100.0F;
	public static int cooldownTicks;
	public static int ownDomainRemainingTicks;
	private static float displayedEnergy;

	private HudState() {
	}

	public static void apply(PlayerStatusPayload payload) {
		energy = payload.energy();
		maxEnergy = Math.max(1.0F, payload.maxEnergy());
		cooldownTicks = payload.cooldownTicks();
		ownDomainRemainingTicks = payload.ownDomainRemainingTicks();
	}

	/** Smoothed value for the bar. */
	public static float displayedEnergy() {
		displayedEnergy += (energy - displayedEnergy) * 0.25F;
		if (Math.abs(displayedEnergy - energy) < 0.05F) displayedEnergy = energy;
		return displayedEnergy;
	}

	public static void reset() {
		energy = 0;
		cooldownTicks = 0;
		ownDomainRemainingTicks = 0;
		displayedEnergy = 0;
	}
}
