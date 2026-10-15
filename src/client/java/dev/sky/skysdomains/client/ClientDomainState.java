package dev.sky.skysdomains.client;

import dev.sky.skysdomains.client.fx.AudioDucker;
import dev.sky.skysdomains.client.fx.CameraEffects;
import dev.sky.skysdomains.client.fx.FogSkyState;
import dev.sky.skysdomains.client.fx.OverlayManager;
import dev.sky.skysdomains.client.fx.PostEffectManager;
import dev.sky.skysdomains.domain.FreezeManager;
import dev.sky.skysdomains.network.DomainClosePayload;
import dev.sky.skysdomains.network.DomainEventPayload;
import dev.sky.skysdomains.network.DomainOpenPayload;
import dev.sky.skysdomains.network.DomainPhasePayload;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** All domains the client knows about, ticked and rendered together. */
public final class ClientDomainState {
	private static final Map<UUID, ClientDomain> DOMAINS = new LinkedHashMap<>();

	private ClientDomainState() {
	}

	public static Collection<ClientDomain> all() {
		return DOMAINS.values();
	}

	public static void onOpen(DomainOpenPayload payload) {
		if (DOMAINS.containsKey(payload.id())) return;
		DOMAINS.put(payload.id(), new ClientDomain(payload));
	}

	public static void onPhase(DomainPhasePayload payload) {
		ClientDomain domain = DOMAINS.get(payload.id());
		if (domain != null) domain.setPhase(payload.phaseIndex());
	}

	public static void onClose(DomainClosePayload payload) {
		ClientDomain domain = DOMAINS.get(payload.id());
		if (domain == null) return;
		domain.sequencer.event("close", domain.center, payload.collapsed() ? 1 : 0, 0);
		domain.close(payload.collapsed());
		FogSkyState.get().clearDomain(domain.id);
		OverlayManager.get().clearDomain(domain.id);
		PostEffectManager.get().clearDomain(domain.id);
		AudioDucker.get().release(20);
	}

	public static void onEvent(DomainEventPayload payload) {
		ClientDomain domain = DOMAINS.get(payload.id());
		if (domain == null) return;
		CollapseClientHandler.handle(domain, payload);
		domain.sequencer.event(payload.event(), payload.pos(), payload.a(), payload.b());
	}

	public static void tick(Minecraft client) {
		if (client.level == null) {
			if (!DOMAINS.isEmpty()) clearAll();
			return;
		}
		Iterator<ClientDomain> iterator = DOMAINS.values().iterator();
		while (iterator.hasNext()) {
			ClientDomain domain = iterator.next();
			if (!domain.tick()) iterator.remove();
		}
		OverlayManager.get().tick();
		CameraEffects.get().tick();
		AudioDucker.get().tick();
		PostEffectManager.get().tick();
	}

	public static void render(LevelRenderContext context) {
		if (DOMAINS.isEmpty()) return;
		float partialTick = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
		for (ClientDomain domain : DOMAINS.values()) {
			domain.scene.render(context, partialTick);
		}
	}

	/** Disconnect / level change: forget everything and restore client state. */
	public static void clearAll() {
		DOMAINS.clear();
		FreezeManager.clientClear();
		OverlayManager.get().clearAll();
		FogSkyState.get().clearAll();
		PostEffectManager.get().clearAll();
		CameraEffects.get().reset();
		AudioDucker.get().reset();
	}
}
