package dev.sky.skysdomains.domain.effects;

import dev.sky.skysdomains.DomainType;
import dev.sky.skysdomains.common.ModBlocks;
import dev.sky.skysdomains.config.ConfigManager;
import dev.sky.skysdomains.config.SkysDomainsConfig;
import dev.sky.skysdomains.domain.ActiveDomain;
import dev.sky.skysdomains.domain.DomainEffect;
import dev.sky.skysdomains.domain.DomainManager;
import dev.sky.skysdomains.domain.DomainPhase;
import dev.sky.skysdomains.domain.FloorCells;
import dev.sky.skysdomains.domain.GroundJournal;
import dev.sky.skysdomains.domain.Targeting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Ember Garden: the floor inside the sphere is "infected" from the centre outwards and turns into
 * scorched ground (glowing cracks), molten ground (magma) and small lava pools. Enemies take fire
 * damage every second, burn, and are hurt by the ground; the owner is immune to fire and lava.
 * Every replaced block is journaled ({@link GroundJournal}) and restored from the rim inwards
 * while the domain closes.
 */
public final class EmberGardenEffect implements DomainEffect {
	private final FloorCells floor = new FloorCells();
	private int infectedUpTo;   // cells[0..infectedUpTo) are infected
	private int restoredDownTo; // cells[restoredDownTo..) are restored again
	private boolean infectGround;

	@Override
	public void onOpen(ActiveDomain domain, ServerLevel level) {
		SkysDomainsConfig.EmberGarden cfg = ConfigManager.get().domains.emberGarden;
		infectGround = cfg.infectGround;
		if (!infectGround) return;
		floor.collect(domain, level);
		restoredDownTo = floor.size();
	}

	@Override
	public void tick(ActiveDomain domain, ServerLevel level) {
		SkysDomainsConfig.EmberGarden cfg = ConfigManager.get().domains.emberGarden;
		if (domain.inPhase(DomainPhase.CLOSE)) {
			if (infectGround) healStep(domain, level, 20);
			return;
		}
		if (infectGround) {
			int spreadTicks = Math.max(1, cfg.spreadTicks);
			double front = domain.radius * Math.min(1.0, (domain.age + 1) / (double) spreadTicks);
			infectStep(domain, level, cfg, front);
		}
		if (!domain.inPhase(DomainPhase.ACTIVE)) return;

		float damage = EffectUtil.perTier(cfg.fireDamagePerSecond, domain);
		if (domain.phaseAge % 20 == 0) {
			EffectUtil.refreshEffect(domain.owner, MobEffects.FIRE_RESISTANCE, 0);
			for (LivingEntity enemy : Targeting.enemiesInside(level, domain.owner, domain.center, domain.radius)) {
				enemy.hurtServer(level, level.damageSources().onFire(), damage);
				if (enemy.isAlive()) enemy.setRemainingFireTicks(Math.max(enemy.getRemainingFireTicks(), 30));
			}
		}
		domain.owner.clearFire();
	}

	@Override
	public void onClose(ActiveDomain domain, ServerLevel level, boolean collapsed) {
		domain.owner.removeEffect(MobEffects.FIRE_RESISTANCE);
		if (!infectGround) return;
		cleanupSpill(level);
		GroundJournal.restoreAll(level);
		floor.clear();
	}

	// --- ground infection -------------------------------------------------------------------

	private void infectStep(ActiveDomain domain, ServerLevel level, SkysDomainsConfig.EmberGarden cfg, double front) {
		int particlesLeft = 40;
		while (infectedUpTo < floor.size() && floor.get(infectedUpTo).distance() <= front) {
			FloorCells.Cell cell = floor.get(infectedUpTo++);
			if (infect(domain, level, cfg, cell) && particlesLeft-- > 0) {
				double x = cell.floor().getX() + 0.5;
				double y = cell.floor().getY() + 1.1;
				double z = cell.floor().getZ() + 0.5;
				level.sendParticles(ParticleTypes.LAVA, x, y, z, 2, 0.3, 0.1, 0.3, 0.0);
				level.sendParticles(ParticleTypes.LARGE_SMOKE, x, y, z, 3, 0.3, 0.2, 0.3, 0.01);
				level.sendParticles(ParticleTypes.FLAME, x, y, z, 4, 0.35, 0.2, 0.35, 0.02);
			}
		}
		if (infectedUpTo < floor.size() && domain.age % 7 == 0) {
			BlockPos edge = floor.get(infectedUpTo).floor();
			level.playSound(null, edge.getX(), edge.getY(), edge.getZ(),
					SoundEvents.LAVA_POP, SoundSource.BLOCKS, 0.8F, 0.6F + level.getRandom().nextFloat() * 0.4F);
		}
	}

	/** Replaces one floor block (and a plant on top of it). Returns true if something changed. */
	private boolean infect(ActiveDomain domain, ServerLevel level, SkysDomainsConfig.EmberGarden cfg, FloorCells.Cell cell) {
		BlockPos pos = cell.floor();
		if (!level.isLoaded(pos)) return false;
		BlockState state = level.getBlockState(pos);
		if (!FloorCells.isFloorCandidate(level, pos, state)) {
			dev.sky.skysdomains.SkysDomains.debug("Ember: {} at {} is no floor candidate any more", state, pos);
			return false;
		}
		if (!FloorCells.clearAbove(level, pos)) {
			dev.sky.skysdomains.SkysDomains.debug("Ember: {} above {} blocks the floor", level.getBlockState(pos.above()), pos);
			return false;
		}

		double rel = Math.max(0.0, cell.distance()) / Math.max(1.0, domain.radius);
		int roll = cell.roll(10, 20); // 0..1023
		BlockState replacement;
		if (cfg.lavaPools && rel < 0.45 && roll < 150 && lavaSafe(level, pos)) {
			replacement = Blocks.LAVA.defaultBlockState();
		} else if (rel < 0.3 ? roll < 700 : rel < 0.6 ? roll < 380 : roll < 120) {
			replacement = ModBlocks.MOLTEN_GROUND.defaultBlockState();
		} else {
			replacement = ModBlocks.SCORCHED_GROUND.defaultBlockState();
		}
		GroundJournal.place(level, pos, replacement);
		return true;
	}

	/** Lava only where all eight horizontal neighbours are solid floor blocks at the same height, so it never flows away. */
	private boolean lavaSafe(ServerLevel level, BlockPos pos) {
		if (!floor.flatAround(pos)) return false;
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				if (dx == 0 && dz == 0) continue;
				BlockPos n = pos.offset(dx, 0, dz);
				BlockState ns = level.getBlockState(n);
				if (ns.is(Blocks.LAVA) || !ns.isCollisionShapeFullBlock(level, n)) return false;
			}
		}
		return true;
	}

	/** Restores from the rim inwards over {@code ticks} ticks (the close phase). */
	private void healStep(ActiveDomain domain, ServerLevel level, int ticks) {
		double front = domain.radius * (1.0 - Math.min(1.0, (domain.phaseAge + 1) / (double) ticks));
		int particlesLeft = 30;
		while (restoredDownTo > 0 && floor.get(restoredDownTo - 1).distance() > front) {
			FloorCells.Cell cell = floor.get(--restoredDownTo);
			if (restoredDownTo >= infectedUpTo) continue; // never infected
			BlockPos pos = cell.floor();
			GroundJournal.restore(level, pos);
			GroundJournal.restore(level, pos.above());
			if (particlesLeft-- > 0) {
				level.sendParticles(ParticleTypes.LARGE_SMOKE, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, 3, 0.3, 0.2, 0.3, 0.01);
			}
		}
	}

	/** Removes lava that flowed or fire that spread from the pools onto neighbouring, non-journaled positions. */
	private void cleanupSpill(ServerLevel level) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int i = 0; i < infectedUpTo; i++) {
			BlockPos base = floor.get(i).floor();
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					for (int dy = -1; dy <= 2; dy++) {
						pos.set(base.getX() + dx, base.getY() + dy, base.getZ() + dz);
						if (GroundJournal.isRecorded(level, pos) || !level.isLoaded(pos)) continue;
						BlockState state = level.getBlockState(pos);
						if (state.is(Blocks.FIRE) || (state.is(Blocks.LAVA) && !state.getFluidState().isSource())) {
							level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
						}
					}
				}
			}
		}
	}

	// --- helpers used by the ground block ----------------------------------------------------

	/** Damage an entity takes for stepping on Ember Garden ground at {@code pos}; 0 for the owner and allies. */
	public static float groundDamageFor(ServerLevel level, BlockPos pos, LivingEntity entity) {
		SkysDomainsConfig.EmberGarden cfg = ConfigManager.get().domains.emberGarden;
		for (ActiveDomain domain : DomainManager.domainsIn(level)) {
			if (domain.type != DomainType.EMBER_GARDEN || domain.finished) continue;
			double r = domain.radius + 1.5;
			if (domain.center.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > r * r) continue;
			return Targeting.isEnemy(domain.owner, entity) ? cfg.groundDamage : 0.0F;
		}
		return cfg.groundDamage;
	}
}
