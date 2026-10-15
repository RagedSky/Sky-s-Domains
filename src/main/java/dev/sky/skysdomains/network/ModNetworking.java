package dev.sky.skysdomains.network;

import dev.sky.skysdomains.domain.DomainManager;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Payload registration and send helpers. Everything a client sees (domain open/close/phase,
 * freezes, impacts, HUD status) is driven by these packets so that every player in range sees
 * the same animation at the same time.
 */
public final class ModNetworking {
	private ModNetworking() {
	}

	public static void registerCommon() {
		PayloadTypeRegistry.serverboundPlay().register(ActivateDomainPayload.TYPE, ActivateDomainPayload.CODEC);

		PayloadTypeRegistry.clientboundPlay().register(PlayerStatusPayload.TYPE, PlayerStatusPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(DomainOpenPayload.TYPE, DomainOpenPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(DomainPhasePayload.TYPE, DomainPhasePayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(DomainClosePayload.TYPE, DomainClosePayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(FreezeSyncPayload.TYPE, FreezeSyncPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(DomainEventPayload.TYPE, DomainEventPayload.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(ActivateDomainPayload.TYPE,
				(payload, context) -> DomainManager.handleActivate(context.player()));
	}

	public static void send(ServerPlayer player, CustomPacketPayload payload) {
		if (ServerPlayNetworking.canSend(player, payload.type())) {
			ServerPlayNetworking.send(player, payload);
		}
	}

	public static void sendAround(ServerLevel level, Vec3 center, double range, CustomPacketPayload payload) {
		for (ServerPlayer player : PlayerLookup.around(level, center, range)) {
			send(player, payload);
		}
	}
}
