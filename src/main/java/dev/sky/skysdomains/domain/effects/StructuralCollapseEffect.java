package dev.sky.skysdomains.domain.effects;

import dev.sky.skysdomains.config.ConfigManager;
import dev.sky.skysdomains.config.SkysDomainsConfig;
import dev.sky.skysdomains.domain.ActiveDomain;
import dev.sky.skysdomains.domain.DomainEffect;
import dev.sky.skysdomains.domain.DomainManager;
import dev.sky.skysdomains.domain.DomainPhase;
import dev.sky.skysdomains.domain.FreezeManager;
import dev.sky.skysdomains.domain.Targeting;
import dev.sky.skysdomains.network.DomainEventPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

/**
 * Structural Collapse, server side. Phases (lengths from config):
 * activation (time freeze), expand, solar_system, collapse, bombardment, end.
 *
 * <p>During the bombardment one planet launches every {@code impactIntervalTicks}. Each planet
 * gets a frozen enemy as target (biggest threat first, then round robin; more planets than enemies
 * means multiple hits; no enemies means decorative ground impacts). The impact is explosion damage
 * only (power per planet from config), block damage optional. Tier 4 ends with the sun falling onto
 * the centre and hitting every enemy still standing inside the sphere.
 */
public final class StructuralCollapseEffect implements DomainEffect {
	public static final String[] PLANETS = {"mercury", "venus", "earth", "mars", "jupiter", "saturn", "uranus", "neptune"};
	public static final int SUN_INDEX = 8;
	private static final String BOMBARDMENT = "bombardment";

	private record PendingImpact(int impactAtPhaseAge, Vec3 pos, int planetIndex, int targetEntityId, float power) {
	}

	private final List<PendingImpact> pending = new ArrayList<>();
	private int roundRobin;

	@Override
	public void onPhaseStart(ActiveDomain domain, ServerLevel level, DomainPhase phase) {
		if (phase.name().equals("activation")) {
			freezeEnemies(domain, level);
		}
	}

	@Override
	public void tick(ActiveDomain domain, ServerLevel level) {
		SkysDomainsConfig.StructuralCollapse cfg = ConfigManager.get().domains.structuralCollapse;
		String phase = domain.phaseName();

		// Newcomers are frozen as well until the bombardment is over.
		if (!phase.equals("end") && domain.age % 10 == 0) {
			freezeEnemies(domain, level);
		}

		if (phase.equals(BOMBARDMENT)) {
			int interval = Math.max(1, cfg.impactIntervalTicks);
			int flight = Math.max(1, cfg.planetFlightTicks);
			int planets = domain.planets;
			if (domain.phaseAge % interval == 0 && domain.phaseAge / interval < planets) {
				launchPlanet(domain, level, domain.phaseAge / interval, flight, cfg);
			}
			if (domain.sunAtEnd && domain.phaseAge == planets * interval + flight + 10) {
				int sunFlight = flight + 10;
				domain.event(DomainEventPayload.SUN_LAUNCH, domain.center, SUN_INDEX, sunFlight);
				pending.add(new PendingImpact(domain.phaseAge + sunFlight, domain.center, SUN_INDEX, -1, cfg.sunExplosionPower));
			}
			Iterator<PendingImpact> iterator = pending.iterator();
			while (iterator.hasNext()) {
				PendingImpact impact = iterator.next();
				if (domain.phaseAge >= impact.impactAtPhaseAge) {
					iterator.remove();
					explode(domain, level, impact, cfg);
				}
			}
		}
	}

	private void freezeEnemies(ActiveDomain domain, ServerLevel level) {
		List<Entity> toFreeze = new ArrayList<>();
		for (LivingEntity enemy : Targeting.enemiesInside(level, domain.owner, domain.center, domain.radius)) {
			if (enemy instanceof Player) continue; // players are never tick-frozen (see Stasis for the soft variant)
			if (domain.frozenIds.add(enemy.getId())) toFreeze.add(enemy);
		}
		FreezeManager.freeze(level, toFreeze, domain.center, domain.syncRange());
	}

	private void launchPlanet(ActiveDomain domain, ServerLevel level, int planetIndex, int flight, SkysDomainsConfig.StructuralCollapse cfg) {
		LivingEntity target = pickTarget(domain, level);
		Vec3 pos;
		int targetId;
		if (target != null) {
			pos = target.position().add(0, target.getBbHeight() * 0.5, 0);
			targetId = target.getId();
		} else {
			pos = decorativeImpactPos(domain, level);
			targetId = -1;
		}
		float power = ConfigManager.get().explosionPower(PLANETS[planetIndex % PLANETS.length]);
		domain.event(DomainEventPayload.PLANET_LAUNCH, pos, planetIndex, flight);
		pending.add(new PendingImpact(domain.phaseAge + flight, pos, planetIndex, targetId, power));
	}

	/** Biggest threat first (players, then by max health), then round robin over the remaining enemies. */
	private LivingEntity pickTarget(ActiveDomain domain, ServerLevel level) {
		List<LivingEntity> candidates = new ArrayList<>();
		for (int id : domain.frozenIds) {
			if (level.getEntity(id) instanceof LivingEntity living && living.isAlive() && Targeting.isEnemy(domain.owner, living)) {
				candidates.add(living);
			}
		}
		for (LivingEntity enemy : Targeting.enemiesInside(level, domain.owner, domain.center, domain.radius)) {
			if (!candidates.contains(enemy)) candidates.add(enemy);
		}
		if (candidates.isEmpty()) return null;
		candidates.sort(Comparator.<LivingEntity>comparingInt(e -> e instanceof Player ? 0 : 1)
				.thenComparing(Comparator.comparingDouble(LivingEntity::getMaxHealth).reversed()));
		LivingEntity target = candidates.get(roundRobin % candidates.size());
		roundRobin++;
		return target;
	}

	private Vec3 decorativeImpactPos(ActiveDomain domain, ServerLevel level) {
		double angle = level.getRandom().nextDouble() * Math.PI * 2;
		double distance = level.getRandom().nextDouble() * domain.radius * 0.8;
		double x = domain.center.x + Math.cos(angle) * distance;
		double z = domain.center.z + Math.sin(angle) * distance;
		int y = (int) Math.floor(domain.center.y) + 2;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos((int) Math.floor(x), y, (int) Math.floor(z));
		for (int i = 0; i < 24; i++) {
			if (!level.getBlockState(pos).isAir()) break;
			pos.move(0, -1, 0);
		}
		return new Vec3(x, pos.getY() + 1.0, z);
	}

	private void explode(ActiveDomain domain, ServerLevel level, PendingImpact impact, SkysDomainsConfig.StructuralCollapse cfg) {
		boolean sun = impact.planetIndex == SUN_INDEX;
		domain.event(sun ? DomainEventPayload.SUN_IMPACT : DomainEventPayload.IMPACT, impact.pos, impact.planetIndex, 0);

		if (cfg.blockDamage) {
			// Vanilla explosion for the block damage; its entity damage is suppressed (we apply our own below).
			DomainManager.withExplosionDamageSuppressed(() ->
					level.explode(domain.owner, impact.pos.x, impact.pos.y, impact.pos.z, impact.power, Level.ExplosionInteraction.TNT));
		}

		double blastRadius = impact.power * 2.0;
		List<LivingEntity> victims = sun
				? Targeting.enemiesInside(level, domain.owner, domain.center, domain.radius)
				: Targeting.enemiesInside(level, domain.owner, impact.pos, blastRadius);
		for (LivingEntity victim : victims) {
			double distance = victim.position().distanceTo(impact.pos);
			double exposure = sun || victim.getId() == impact.targetEntityId ? 1.0 : Math.max(0.0, 1.0 - distance / blastRadius);
			if (exposure <= 0.0) continue;
			float damage = (float) ((exposure * exposure + exposure) / 2.0 * 7.0 * blastRadius + 1.0);
			victim.hurtServer(level, level.damageSources().explosion(null, domain.owner), damage);
			if (domain.frozenIds.remove(victim.getId())) {
				FreezeManager.unfreezeOne(level, victim, domain.center, domain.syncRange());
			}
		}
	}
}
