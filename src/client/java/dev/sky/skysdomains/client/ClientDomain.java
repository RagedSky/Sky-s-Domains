package dev.sky.skysdomains.client;

import dev.sky.skysdomains.DomainType;
import dev.sky.skysdomains.client.animation.Sequence;
import dev.sky.skysdomains.client.animation.SequenceLoader;
import dev.sky.skysdomains.client.animation.Sequencer;
import dev.sky.skysdomains.client.render.SceneRenderer;
import dev.sky.skysdomains.domain.DomainPhase;
import dev.sky.skysdomains.network.DomainOpenPayload;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;

/** Client mirror of an active domain plus its animation runtime. */
public final class ClientDomain {
	public final UUID id;
	public final int ownerEntityId;
	public final DomainType type;
	public final int tierLevel;
	public final int refinements;
	public final Vec3 center;
	public final double radius;
	public final List<DomainPhase> phases;
	public final int planets;
	public final boolean sunAtEnd;
	public final SceneRenderer scene;
	public final Sequencer sequencer;

	public int phaseIndex;
	public int phaseAge;
	public boolean closing;
	public int closeAge;

	public ClientDomain(DomainOpenPayload payload) {
		this.id = payload.id();
		this.ownerEntityId = payload.ownerEntityId();
		this.type = payload.domainType();
		this.tierLevel = payload.tierLevel();
		this.refinements = payload.refinements();
		this.center = payload.center();
		this.radius = payload.radius();
		this.phases = payload.phases();
		this.planets = payload.planets();
		this.sunAtEnd = payload.sunAtEnd();
		Sequence sequence = SequenceLoader.get(type);
		this.scene = new SceneRenderer(this, sequence.sphere());
		this.sequencer = new Sequencer(this, sequence);
		this.phaseIndex = payload.phaseIndex();
		this.phaseAge = payload.phaseAge();
		// Late join: replay what already happened in this phase (and the opening phases) instantly.
		for (int i = 0; i < phaseIndex && i < phases.size(); i++) {
			sequencer.startPhase(phases.get(i).name(), phases.get(i).ticks());
		}
		sequencer.startPhase(phaseName(), phaseAge);
	}

	public String phaseName() {
		if (phases.isEmpty()) return DomainPhase.ACTIVE;
		return phases.get(Math.min(phaseIndex, phases.size() - 1)).name();
	}

	public void setPhase(int index) {
		if (index == phaseIndex) return;
		phaseIndex = index;
		phaseAge = 0;
		sequencer.startPhase(phaseName(), 0);
	}

	/** Returns false once the domain is fully gone. */
	public boolean tick() {
		scene.tick();
		if (closing) {
			closeAge++;
			return closeAge < 20;
		}
		sequencer.tick(phaseAge);
		phaseAge++;
		return true;
	}

	/** The server removed the domain: shrink the sphere, drop objects, let overlays fade. */
	public void close(boolean collapsed) {
		if (closing) return;
		closing = true;
		scene.removeAll();
		scene.sphereScale(scene.sphereScaleNow(), 0.0F, 12, dev.sky.skysdomains.client.animation.Easing.EASE_IN_CUBIC);
	}
}
