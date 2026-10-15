package dev.sky.skysdomains.domain;

import dev.sky.skysdomains.DomainType;
import dev.sky.skysdomains.TalismanTier;
import dev.sky.skysdomains.common.TalismanStats;
import dev.sky.skysdomains.network.DomainEventPayload;
import dev.sky.skysdomains.network.DomainOpenPayload;
import dev.sky.skysdomains.network.ModNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Server-side state of one open domain: owner, type, tier, centre, radius, phases, remaining
 * time, frozen entities. The effect object implements the per-domain behaviour.
 */
public final class ActiveDomain {
	public final UUID id = UUID.randomUUID();
	public final ServerLevel level;
	public final ServerPlayer owner;
	public final DomainType type;
	public final TalismanTier tier;
	public final TalismanStats stats;
	public final Vec3 center;
	public final double radius;
	public final double energySpent;
	public final List<DomainPhase> phases;
	public final int planets;
	public final boolean sunAtEnd;
	public final DomainEffect effect;

	/** Ids of entities this domain froze (released on close). */
	public final Set<Integer> frozenIds = new HashSet<>();
	/** Players that already received the open packet. */
	public final Set<UUID> informedPlayers = new HashSet<>();

	public int age;
	public int phaseIndex;
	public int phaseAge;
	public boolean finished;
	public boolean collapsed;

	public ActiveDomain(ServerLevel level, ServerPlayer owner, TalismanStats stats, Vec3 center, double energySpent,
						List<DomainPhase> phases, int planets, boolean sunAtEnd, DomainEffect effect) {
		this.level = level;
		this.owner = owner;
		this.type = stats.domain();
		this.tier = stats.tier();
		this.stats = stats;
		this.center = center;
		this.radius = stats.radius();
		this.energySpent = energySpent;
		this.phases = new ArrayList<>(phases);
		this.planets = planets;
		this.sunAtEnd = sunAtEnd;
		this.effect = effect;
	}

	public DomainPhase phase() {
		return phases.get(Math.min(phaseIndex, phases.size() - 1));
	}

	public String phaseName() {
		return phase().name();
	}

	public boolean inPhase(String name) {
		return phase().name().equals(name);
	}

	public int totalTicks() {
		int total = 0;
		for (DomainPhase phase : phases) total += phase.ticks();
		return total;
	}

	public int remainingTicks() {
		return Math.max(0, totalTicks() - age);
	}

	/** Range in which players receive packets about this domain. */
	public double syncRange() {
		return radius + 128.0;
	}

	public void broadcast(CustomPacketPayload payload) {
		ModNetworking.sendAround(level, center, syncRange(), payload);
	}

	public void event(String name, Vec3 pos, int a, int b) {
		broadcast(new DomainEventPayload(id, name, pos, a, b));
	}

	public DomainOpenPayload openPayload() {
		return new DomainOpenPayload(id, owner.getId(), type, tier.level(), stats.refinements(), center, radius,
				phases, phaseIndex, phaseAge, planets, sunAtEnd);
	}

	/** Advances age/phase. Returns true when a new phase started this tick. */
	boolean advance() {
		age++;
		phaseAge++;
		if (phaseAge >= phase().ticks()) {
			phaseIndex++;
			phaseAge = 0;
			if (phaseIndex >= phases.size()) {
				finished = true;
				return false;
			}
			return true;
		}
		return false;
	}

	/** Jump straight to the closing phase (clash loser, owner left). */
	void collapse() {
		collapsed = true;
		int last = phases.size() - 1;
		if (phaseIndex < last) {
			phaseIndex = last;
			phaseAge = 0;
		}
	}

	public boolean overlaps(ActiveDomain other) {
		if (other.level != level) return false;
		double distance = other.center.distanceTo(center);
		return distance < radius + other.radius;
	}
}
