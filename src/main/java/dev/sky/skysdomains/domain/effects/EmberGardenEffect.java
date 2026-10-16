package dev.sky.skysdomains.domain.effects;

import dev.sky.skysdomains.DomainType;
import dev.sky.skysdomains.common.ModBlocks;
import dev.sky.skysdomains.config.ConfigManager;
import dev.sky.skysdomains.config.SkysDomainsConfig;
import dev.sky.skysdomains.domain.ActiveDomain;
import dev.sky.skysdomains.domain.DomainEffect;
import dev.sky.skysdomains.domain.DomainManager;
import dev.sky.skysdomains.domain.DomainPhase;
import dev.sky.skysdomains.domain.EmberGround;
import dev.sky.skysdomains.domain.Targeting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Ember Garden: the floor inside the sphere is "infected" from the centre outwards and turns into
 * scorched ground (glowing cracks), molten ground (magma) and small lava pools. Enemies take fire
 * damage every second, burn, and are hurt by the ground; the owner is immune to fire and lava.
 * Every replaced block is journaled ({@link EmberGround}) and restored from the rim inwards while
 * the domain closes.
 */
public final class EmberGardenEffect implements DomainEffect {
	/** One floor column inside the sphere. */
	private record Cell(BlockPos floor, double distance, long hash) {
	}

	private final List<Cell> cells = new ArrayList<>();
	private final Map<Long, Integer> floorHeight = new HashMap<>();
	private int infectedUpTo;   // cells[0..infectedUpTo) are infected
	private int restoredDownTo; // cells[restoredDownTo..) are restored again
	private boolean infectGround;

	@Override
	public void onOpen(ActiveDomain domain, ServerLevel level) {
		SkysDomainsConfig.EmberGarden cfg = ConfigManager.get().domains.emberGarden;
		infectGround = cfg.infectGround;
		if (!infectGround) return;
		collectCells(domain, level, cfg);
		restoredDownTo = cells.size();
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
		EmberGround.restoreAll(level);
		cells.clear();
		floorHeight.clear();
	}

	// --- ground infection -------------------------------------------------------------------

	/** Finds the floor block of every column inside the sphere (near the owner's height). */
	private void collectCells(ActiveDomain domain, ServerLevel level, SkysDomainsConfig.EmberGarden cfg) {
		int r = (int) Math.ceil(domain.radius);
		double r2 = domain.radius * domain.radius;
		int cx = (int) Math.floor(domain.center.x);
		int cy = (int) Math.floor(domain.center.y);
		int cz = (int) Math.floor(domain.center.z);
		int up = Math.min(r, 4);
		int down = Math.min(r, 10);
		long seed = domain.id.getLeastSignificantBits();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				if (dx * dx + dz * dz > r2) continue;
				for (int y = cy + up; y >= cy - down; y--) {
					int dy = y - cy;
					if (dx * dx + dy * dy + dz * dz > r2) continue;
					pos.set(cx + dx, y, cz + dz);
					if (!level.isLoaded(pos)) break;
					BlockState state = level.getBlockState(pos);
					if (!isFloorCandidate(level, pos, state, cfg)) continue;
					BlockState above = level.getBlockState(pos.above());
					if (above.isCollisionShapeFullBlock(level, pos.above()) || above.liquid()) break; // buried, not a floor
					double horizontal = Math.sqrt(dx * dx + dz * dz);
					long hash = mix(seed ^ pos.asLong());
					double jitter = ((hash >>> 40) & 0xFF) / 255.0 * 2.2 - 1.1;
					cells.add(new Cell(pos.immutable(), horizontal + jitter, hash));
					floorHeight.put(columnKey(cx + dx, cz + dz), y);
					break;
				}
			}
		}
		cells.sort((a, b) -> Double.compare(a.distance(), b.distance()));
	}

	private static boolean isFloorCandidate(ServerLevel level, BlockPos pos, BlockState state, SkysDomainsConfig.EmberGarden cfg) {
		if (state.isAir() || state.liquid() || !state.getFluidState().isEmpty()) return false;
		if (!state.isCollisionShapeFullBlock(level, pos)) return false;
		if (state.hasBlockEntity()) return false;
		if (state.getDestroySpeed(level, pos) < 0.0F) return false;
		if (state.is(BlockTags.FEATURES_CANNOT_REPLACE) || state.is(BlockTags.WITHER_IMMUNE)) return false;
		if (EmberGround.isOurs(state)) return false;
		Identifier id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
		return !cfg.groundBlacklist.contains(id.toString());
	}

	private void infectStep(ActiveDomain domain, ServerLevel level, SkysDomainsConfig.EmberGarden cfg, double front) {
		int particlesLeft = 40;
		while (infectedUpTo < cells.size() && cells.get(infectedUpTo).distance() <= front) {
			Cell cell = cells.get(infectedUpTo++);
			if (infect(domain, level, cfg, cell) && particlesLeft-- > 0) {
				double x = cell.floor().getX() + 0.5;
				double y = cell.floor().getY() + 1.1;
				double z = cell.floor().getZ() + 0.5;
				level.sendParticles(ParticleTypes.LAVA, x, y, z, 2, 0.3, 0.1, 0.3, 0.0);
				level.sendParticles(ParticleTypes.LARGE_SMOKE, x, y, z, 3, 0.3, 0.2, 0.3, 0.01);
				level.sendParticles(ParticleTypes.FLAME, x, y, z, 4, 0.35, 0.2, 0.35, 0.02);
			}
		}
		if (infectedUpTo < cells.size() && domain.age % 7 == 0) {
			Cell edge = cells.get(infectedUpTo);
			level.playSound(null, edge.floor().getX(), edge.floor().getY(), edge.floor().getZ(),
					SoundEvents.LAVA_POP, SoundSource.BLOCKS, 0.8F, 0.6F + level.getRandom().nextFloat() * 0.4F);
		}
	}

	/** Replaces one floor block (and a plant on top of it). Returns true if something changed. */
	private boolean infect(ActiveDomain domain, ServerLevel level, SkysDomainsConfig.EmberGarden cfg, Cell cell) {
		BlockPos pos = cell.floor();
		if (!level.isLoaded(pos)) return false;
		BlockState state = level.getBlockState(pos);
		if (!isFloorCandidate(level, pos, state, cfg)) return false;

		BlockPos abovePos = pos.above();
		BlockState above = level.getBlockState(abovePos);
		if (!above.isAir()) {
			if (!isRemovablePlant(above)) return false;
			EmberGround.record(level, abovePos, above);
			level.setBlock(abovePos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
		}

		double rel = Math.max(0.0, cell.distance()) / Math.max(1.0, domain.radius);
		int roll = (int) ((cell.hash() >>> 20) & 0x3FF); // 0..1023
		BlockState replacement;
		if (cfg.lavaPools && rel < 0.45 && roll < 150 && lavaSafe(level, pos)) {
			replacement = Blocks.LAVA.defaultBlockState();
		} else if (rel < 0.3 ? roll < 700 : rel < 0.6 ? roll < 380 : roll < 120) {
			replacement = ModBlocks.MOLTEN_GROUND.defaultBlockState();
		} else {
			replacement = ModBlocks.SCORCHED_GROUND.defaultBlockState();
		}
		EmberGround.record(level, pos, state);
		level.setBlock(pos, replacement, Block.UPDATE_ALL);
		return true;
	}

	private static boolean isRemovablePlant(BlockState state) {
		return state.canBeReplaced() || state.is(BlockTags.FLOWERS) || state.is(BlockTags.SAPLINGS)
				|| state.is(BlockTags.CROPS) || state.is(BlockTags.SNOW);
	}

	/** Lava only where all eight horizontal neighbours are floor blocks at the same height, so it never flows away. */
	private boolean lavaSafe(ServerLevel level, BlockPos pos) {
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				if (dx == 0 && dz == 0) continue;
				Integer y = floorHeight.get(columnKey(pos.getX() + dx, pos.getZ() + dz));
				if (y == null || y != pos.getY()) return false;
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
		while (restoredDownTo > 0 && cells.get(restoredDownTo - 1).distance() > front) {
			Cell cell = cells.get(--restoredDownTo);
			if (restoredDownTo >= infectedUpTo) continue; // never infected
			BlockPos pos = cell.floor();
			EmberGround.restore(level, pos);
			EmberGround.restore(level, pos.above());
			if (particlesLeft-- > 0) {
				level.sendParticles(ParticleTypes.LARGE_SMOKE, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, 3, 0.3, 0.2, 0.3, 0.01);
			}
		}
	}

	/** Removes lava that flowed or fire that spread from the pools onto neighbouring, non-journaled positions. */
	private void cleanupSpill(ServerLevel level) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int i = 0; i < infectedUpTo; i++) {
			BlockPos floor = cells.get(i).floor();
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					for (int dy = -1; dy <= 2; dy++) {
						pos.set(floor.getX() + dx, floor.getY() + dy, floor.getZ() + dz);
						if (EmberGround.isRecorded(level, pos) || !level.isLoaded(pos)) continue;
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

	private static long columnKey(int x, int z) {
		return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
	}

	private static long mix(long value) {
		value ^= value >>> 33;
		value *= 0xff51afd7ed558ccdL;
		value ^= value >>> 33;
		value *= 0xc4ceb9fe1a85ec53L;
		value ^= value >>> 33;
		return value;
	}
}
