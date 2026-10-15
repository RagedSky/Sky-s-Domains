package dev.sky.skysdomains.client;

import dev.sky.skysdomains.client.hud.HudState;
import dev.sky.skysdomains.domain.FreezeManager;
import dev.sky.skysdomains.network.DomainClosePayload;
import dev.sky.skysdomains.network.DomainEventPayload;
import dev.sky.skysdomains.network.DomainOpenPayload;
import dev.sky.skysdomains.network.DomainPhasePayload;
import dev.sky.skysdomains.network.FreezeSyncPayload;
import dev.sky.skysdomains.network.PlayerStatusPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/** Client-side packet receivers. Handlers run on the client thread. */
public final class ClientNetworking {
	private ClientNetworking() {
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(PlayerStatusPayload.TYPE, (payload, context) -> HudState.apply(payload));
		ClientPlayNetworking.registerGlobalReceiver(DomainOpenPayload.TYPE, (payload, context) -> ClientDomainState.onOpen(payload));
		ClientPlayNetworking.registerGlobalReceiver(DomainPhasePayload.TYPE, (payload, context) -> ClientDomainState.onPhase(payload));
		ClientPlayNetworking.registerGlobalReceiver(DomainClosePayload.TYPE, (payload, context) -> ClientDomainState.onClose(payload));
		ClientPlayNetworking.registerGlobalReceiver(DomainEventPayload.TYPE, (payload, context) -> ClientDomainState.onEvent(payload));
		ClientPlayNetworking.registerGlobalReceiver(FreezeSyncPayload.TYPE, (payload, context) -> FreezeManager.clientApply(payload.entityIds(), payload.frozen()));

		ClientPlayConnectionEvents.DISCONNECT.register((listener, client) -> {
			ClientDomainState.clearAll();
			HudState.reset();
		});
	}
}
