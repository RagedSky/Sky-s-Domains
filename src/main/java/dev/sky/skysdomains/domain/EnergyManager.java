package dev.sky.skysdomains.domain;

import com.mojang.serialization.Codec;
import dev.sky.skysdomains.SkysDomains;
import dev.sky.skysdomains.common.TalismanItem;
import dev.sky.skysdomains.common.TalismanStats;
import dev.sky.skysdomains.config.ConfigManager;
import dev.sky.skysdomains.config.SkysDomainsConfig;
import dev.sky.skysdomains.network.ModNetworking;
import dev.sky.skysdomains.network.PlayerStatusPayload;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Domain energy: a per-player resource stored as a persistent Fabric attachment.
 * It regenerates slowly; a talisman in the offhand multiplies the regeneration (per tier).
 * The HUD receives a compact status packet every few ticks.
 */
public final class EnergyManager {
	public static final AttachmentType<Float> ENERGY = AttachmentRegistry.create(SkysDomains.id("energy"), builder -> builder
			.initializer(() -> ConfigManager.get().energy.max)
			.persistent(Codec.FLOAT)
			.copyOnDeath());

	/** Game time (ticks) at which the next domain may be opened. */
	public static final AttachmentType<Long> COOLDOWN_UNTIL = AttachmentRegistry.create(SkysDomains.id("cooldown_until"), builder -> builder
			.initializer(() -> 0L)
			.persistent(Codec.LONG)
			.copyOnDeath());

	/** Game time until which regeneration is paused (after an activation). */
	public static final AttachmentType<Long> REGEN_PAUSED_UNTIL = AttachmentRegistry.create(SkysDomains.id("regen_paused_until"), builder -> builder
			.initializer(() -> 0L));

	private static final int STATUS_INTERVAL = 4;

	private EnergyManager() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(EnergyManager::tick);
	}

	private static void tick(MinecraftServer server) {
		SkysDomainsConfig config = ConfigManager.get();
		boolean sendStatus = server.getTickCount() % STATUS_INTERVAL == 0;
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			long now = player.level().getGameTime();
			float energy = getEnergy(player);
			float max = config.energy.max;
			if (energy < max && now >= player.getAttachedOrCreate(REGEN_PAUSED_UNTIL)) {
				double multiplier = 1.0;
				ItemStack offhand = player.getOffhandItem();
				if (offhand.getItem() instanceof TalismanItem) {
					multiplier = TalismanStats.of(offhand).regenMultiplier();
				}
				energy = (float) Math.min(max, energy + config.energy.regenPerSecond / 20.0 * multiplier);
				player.setAttached(ENERGY, energy);
			} else if (energy > max) {
				player.setAttached(ENERGY, max);
			}
			if (sendStatus) {
				ModNetworking.send(player, new PlayerStatusPayload(getEnergy(player), max, cooldownTicksRemaining(player),
						DomainManager.ownedDomainRemainingTicks(player)));
			}
		}
	}

	public static float getEnergy(ServerPlayer player) {
		return player.getAttachedOrCreate(ENERGY);
	}

	public static boolean trySpend(ServerPlayer player, double amount) {
		float energy = getEnergy(player);
		if (energy + 1.0E-4 < amount) return false;
		player.setAttached(ENERGY, (float) (energy - amount));
		long now = player.level().getGameTime();
		player.setAttached(REGEN_PAUSED_UNTIL, now + (long) (ConfigManager.get().energy.regenDelaySeconds * 20));
		return true;
	}

	public static int cooldownTicksRemaining(ServerPlayer player) {
		long until = player.getAttachedOrCreate(COOLDOWN_UNTIL);
		long now = player.level().getGameTime();
		return (int) Math.max(0, until - now);
	}

	public static void startCooldown(ServerPlayer player, int ticks) {
		player.setAttached(COOLDOWN_UNTIL, player.level().getGameTime() + ticks);
	}
}
