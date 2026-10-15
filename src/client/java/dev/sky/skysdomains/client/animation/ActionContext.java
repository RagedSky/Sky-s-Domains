package dev.sky.skysdomains.client.animation;

import dev.sky.skysdomains.client.ClientDomain;
import dev.sky.skysdomains.client.fx.AudioDucker;
import dev.sky.skysdomains.client.fx.CameraEffects;
import dev.sky.skysdomains.client.fx.FogSkyState;
import dev.sky.skysdomains.client.fx.OverlayManager;
import dev.sky.skysdomains.client.fx.PostEffectManager;
import dev.sky.skysdomains.client.render.SceneRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Everything an action may touch. {@code eventPos} is set when actions run because of a server
 * event (impact position, shattered projectile, ...), otherwise it is the domain centre.
 */
public record ActionContext(Minecraft client, ClientDomain domain, Vec3 eventPos, int eventA, int eventB,
							@Nullable String eventName, int eventCounter) {
	public static ActionContext phase(Minecraft client, ClientDomain domain) {
		return new ActionContext(client, domain, domain.center, 0, 0, null, 0);
	}

	public static ActionContext event(Minecraft client, ClientDomain domain, String name, Vec3 pos, int a, int b, int counter) {
		return new ActionContext(client, domain, pos, a, b, name, counter);
	}

	public SceneRenderer scene() {
		return domain.scene;
	}

	public OverlayManager overlays() {
		return OverlayManager.get();
	}

	public CameraEffects camera() {
		return CameraEffects.get();
	}

	public AudioDucker audio() {
		return AudioDucker.get();
	}

	public FogSkyState fogSky() {
		return FogSkyState.get();
	}

	public PostEffectManager post() {
		return PostEffectManager.get();
	}

	/** Resolves "center" / "player" / "event" position keywords. */
	public Vec3 resolve(String at) {
		if (at == null) return eventPos;
		return switch (at) {
			case "center" -> domain.center;
			case "player" -> client.player != null ? client.player.position() : domain.center;
			default -> eventPos;
		};
	}
}
