package dev.sky.skysdomains.client;

import dev.sky.skysdomains.DomainType;
import dev.sky.skysdomains.SkysDomains;
import dev.sky.skysdomains.client.animation.Easing;
import dev.sky.skysdomains.client.render.SceneObject;
import dev.sky.skysdomains.domain.effects.StructuralCollapseEffect;
import dev.sky.skysdomains.network.DomainEventPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

/**
 * Structural Collapse: the server decides when a planet launches and where it hits; this
 * handler moves the matching scene object ({@code mercury} ... {@code neptune}, {@code sun}) from
 * the white hole to the target. Everything else (flash, shake, sounds, particles, impact frames)
 * comes from the {@code events} section of the sequence file.
 */
public final class CollapseClientHandler {
	private CollapseClientHandler() {
	}

	public static void handle(ClientDomain domain, DomainEventPayload payload) {
		if (domain.type != DomainType.STRUCTURAL_COLLAPSE) return;
		switch (payload.event()) {
			case DomainEventPayload.PLANET_LAUNCH -> launch(domain, planetId(payload.a()), payload.pos(), payload.b(), "white_hole");
			case DomainEventPayload.SUN_LAUNCH -> launch(domain, "sun", payload.pos(), payload.b(), null);
			case DomainEventPayload.IMPACT -> domain.scene.remove(planetId(payload.a()));
			case DomainEventPayload.SUN_IMPACT -> domain.scene.remove("sun");
			default -> { }
		}
	}

	private static String planetId(int index) {
		String[] planets = StructuralCollapseEffect.PLANETS;
		return planets[Math.floorMod(index, planets.length)];
	}

	private static void launch(ClientDomain domain, String objectId, Vec3 target, int flightTicks, String fromObjectId) {
		SceneObject object = domain.scene.get(objectId);
		if (object == null) {
			// The sequence file did not spawn it (e.g. lower tier) – create a stand-in so the hit is visible.
			object = domain.scene.spawn(objectId, SceneObject.Kind.SPHERE, SkysDomains.id("scene/planet_" + objectId));
			object.scale = 0.8F;
		}
		SceneObject from = fromObjectId == null ? null : domain.scene.get(fromObjectId);
		if (from != null) object.pos = from.pos;
		object.visible = true;
		object.alpha = 1.0F;
		object.stopOrbit();
		object.trailParticle = Identifier.withDefaultNamespace("end_rod");
		Vec3 relativeTarget = target.subtract(domain.center);
		object.tweenTo(relativeTarget, Math.max(0.3F, object.scale), 1.0F, Math.max(1, flightTicks), Easing.EASE_IN_QUAD);
	}
}
