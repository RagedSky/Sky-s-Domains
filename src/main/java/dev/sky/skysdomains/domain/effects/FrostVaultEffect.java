package dev.sky.skysdomains.domain.effects;

import dev.sky.skysdomains.config.ConfigManager;
import dev.sky.skysdomains.config.SkysDomainsConfig;
import dev.sky.skysdomains.domain.ActiveDomain;
import dev.sky.skysdomains.domain.DomainEffect;
import dev.sky.skysdomains.domain.DomainPhase;
import dev.sky.skysdomains.domain.FloorCells;
import dev.sky.skysdomains.domain.GroundJournal;
import dev.sky.skysdomains.domain.Targeting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

/**
 * Frost Vault: enemies accumulate freeze ticks fast (like powder snow, but quicker) and get
 * Slowness; once fully frozen they take freeze damage every second. Water inside turns to ice,
 * lava to obsidian (spread over several ticks). The owner never freezes.
 * <p>
 * The floor freezes over from the centre outwards into ice, packed ice and blue ice (with snow
 * dusting), and ice spikes shoot out of the ground like stalagmites in different sizes. Everything
 * is journaled ({@link GroundJournal}) and thaws back to the original blocks while the domain closes.
 */
public final class FrostVaultEffect implements DomainEffect {
	private static final int FULLY_FROZEN = 140;
	private static final int BLOCKS_PER_TICK = 600;

	/** One stalagmite: base cell, planned layers (bottom to top, each a list of positions), grown so far. */
	private static final class Spike {
		final List<List<BlockPos>> layers = new ArrayList<>();
		int grown;
		boolean stopped;

		Spike(BlockPos base, int height, boolean thick, int lean) {
			for (int i = 0; i < height; i++) {
				List<BlockPos> layer = new ArrayList<>();
				int shift = lean == 0 ? 0 : i / 3;
				BlockPos centre = base.above(i + 1).offset(lean == 1 ? shift : lean == 2 ? -shift : 0, 0, lean == 3 ? shift : lean == 4 ? -shift : 0);
				layer.add(centre);
				if (thick && i < height / 2) { // a plus-shaped foot for the big ones
					layer.add(centre.north());
					layer.add(centre.south());
					layer.add(centre.east());
					layer.add(centre.west());
				}
				if (thick && i < height / 5) {
					layer.add(centre.north().east());
					layer.add(centre.north().west());
					layer.add(centre.south().east());
					layer.add(centre.south().west());
				}
				layers.add(layer);
			}
		}

		BlockState material(int layer) {
			int height = layers.size();
			if (layer < height / 3) return Blocks.BLUE_ICE.defaultBlockState();
			if (layer < height * 2 / 3) return Blocks.PACKED_ICE.defaultBlockState();
			return Blocks.ICE.defaultBlockState();
		}
	}

	private final FloorCells floor = new FloorCells();
	private final List<Spike> spikes = new ArrayList<>();
	private int frozenUpTo;
	private int thawedDownTo;
	private int spikeFront;       // spikes[0..spikeFront) are growing
	private boolean freezeGround;
	private boolean iceSpikes;

	private int sweepX;
	private int sweepY;
	private int sweepZ;
	private boolean sweeping;

	@Override
	public void onOpen(ActiveDomain domain, ServerLevel level) {
		SkysDomainsConfig.FrostVault cfg = ConfigManager.get().domains.frostVault;
		freezeGround = cfg.freezeGround;
		iceSpikes = cfg.iceSpikes && cfg.freezeGround;
		if (!freezeGround) return;
		floor.collect(domain, level);
		thawedDownTo = floor.size();
		if (iceSpikes) planSpikes(domain, level, cfg);
	}

	@Override
	public void onPhaseStart(ActiveDomain domain, ServerLevel level, DomainPhase phase) {
		if (phase.name().equals(DomainPhase.ACTIVE)) {
			int r = (int) Math.ceil(domain.radius);
			sweepX = -r;
			sweepY = -r;
			sweepZ = -r;
			sweeping = true;
		}
	}

	@Override
	public void tick(ActiveDomain domain, ServerLevel level) {
		SkysDomainsConfig.FrostVault cfg = ConfigManager.get().domains.frostVault;
		if (domain.inPhase(DomainPhase.CLOSE)) {
			if (freezeGround) thawStep(domain, level, 20);
			return;
		}
		if (freezeGround) {
			int spreadTicks = Math.max(1, cfg.spreadTicks);
			double front = domain.radius * Math.min(1.0, (domain.age + 1) / (double) spreadTicks);
			freezeStep(domain, level, front);
			if (iceSpikes) growSpikes(domain, level, front);
		}
		if (!domain.inPhase(DomainPhase.ACTIVE)) return;

		int freezePerTick = EffectUtil.perTier(cfg.freezeTicksPerTick, domain);
		int slowness = EffectUtil.perTier(cfg.slownessAmplifier, domain);
		float damage = EffectUtil.perTier(cfg.freezeDamagePerSecond, domain);
		boolean second = domain.phaseAge % 20 == 0;

		for (LivingEntity enemy : Targeting.enemiesInside(level, domain.owner, domain.center, domain.radius)) {
			enemy.setTicksFrozen(Math.min(FULLY_FROZEN + 40, enemy.getTicksFrozen() + freezePerTick));
			if (second) {
				EffectUtil.refreshEffect(enemy, MobEffects.SLOWNESS, slowness);
				if (enemy.getTicksFrozen() >= FULLY_FROZEN) {
					enemy.hurtServer(level, level.damageSources().freeze(), damage);
				}
			}
		}
		// Owner immunity against the cold.
		domain.owner.setTicksFrozen(0);

		if (sweeping && (cfg.convertWaterToIce || cfg.convertLavaToObsidian)) {
			sweep(domain, level, cfg);
		}
	}

	@Override
	public void onClose(ActiveDomain domain, ServerLevel level, boolean collapsed) {
		if (!freezeGround) return;
		GroundJournal.restoreAll(level);
		floor.clear();
		spikes.clear();
	}

	// --- frozen floor -----------------------------------------------------------------------

	private void freezeStep(ActiveDomain domain, ServerLevel level, double front) {
		int particlesLeft = 40;
		while (frozenUpTo < floor.size() && floor.get(frozenUpTo).distance() <= front) {
			FloorCells.Cell cell = floor.get(frozenUpTo++);
			if (freeze(domain, level, cell) && particlesLeft-- > 0) {
				double x = cell.floor().getX() + 0.5;
				double y = cell.floor().getY() + 1.1;
				double z = cell.floor().getZ() + 0.5;
				level.sendParticles(ParticleTypes.SNOWFLAKE, x, y, z, 4, 0.3, 0.2, 0.3, 0.02);
				level.sendParticles(ParticleTypes.CLOUD, x, y, z, 1, 0.2, 0.05, 0.2, 0.0);
			}
		}
		if (frozenUpTo < floor.size() && domain.age % 6 == 0) {
			BlockPos edge = floor.get(frozenUpTo).floor();
			level.playSound(null, edge.getX(), edge.getY(), edge.getZ(),
					SoundEvents.GLASS_PLACE, SoundSource.BLOCKS, 0.7F, 0.5F + level.getRandom().nextFloat() * 0.3F);
		}
	}

	/** Freezes one floor block: ice at the rim, packed ice in the middle, blue ice at the core, snow dusting on top. */
	private boolean freeze(ActiveDomain domain, ServerLevel level, FloorCells.Cell cell) {
		BlockPos pos = cell.floor();
		if (!level.isLoaded(pos)) return false;
		BlockState state = level.getBlockState(pos);
		if (!FloorCells.isFloorCandidate(level, pos, state)) {
			dev.sky.skysdomains.SkysDomains.debug("Frost: {} at {} is no floor candidate any more", state, pos);
			return false;
		}
		if (!FloorCells.clearAbove(level, pos)) {
			dev.sky.skysdomains.SkysDomains.debug("Frost: {} above {} blocks the floor", level.getBlockState(pos.above()), pos);
			return false;
		}

		double rel = Math.max(0.0, cell.distance()) / Math.max(1.0, domain.radius);
		int roll = cell.roll(10, 20); // 0..1023
		BlockState replacement;
		if (rel < 0.3 ? roll < 650 : rel < 0.55 ? roll < 250 : roll < 60) {
			replacement = Blocks.BLUE_ICE.defaultBlockState();
		} else if (rel < 0.75 ? roll < 800 : roll < 450) {
			replacement = Blocks.PACKED_ICE.defaultBlockState();
		} else {
			replacement = Blocks.ICE.defaultBlockState();
		}
		GroundJournal.place(level, pos, replacement);
		int snowRoll = cell.roll(6, 8); // 0..63
		if (snowRoll < 18 && level.getBlockState(pos.above()).isAir()) {
			int layers = 1 + (snowRoll % 2);
			GroundJournal.place(level, pos.above(), Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, layers));
		}
		return true;
	}

	/** Thaws from the rim inwards over {@code ticks} ticks (the close phase); spikes crumble top-down first. */
	private void thawStep(ActiveDomain domain, ServerLevel level, int ticks) {
		shrinkSpikes(level);
		double front = domain.radius * (1.0 - Math.min(1.0, (domain.phaseAge + 1) / (double) ticks));
		int particlesLeft = 30;
		while (thawedDownTo > 0 && floor.get(thawedDownTo - 1).distance() > front) {
			FloorCells.Cell cell = floor.get(--thawedDownTo);
			if (thawedDownTo >= frozenUpTo) continue;
			BlockPos pos = cell.floor();
			GroundJournal.restore(level, pos);
			GroundJournal.restore(level, pos.above());
			if (particlesLeft-- > 0) {
				level.sendParticles(ParticleTypes.CLOUD, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, 2, 0.3, 0.1, 0.3, 0.01);
			}
		}
	}

	// --- ice spikes -------------------------------------------------------------------------

	/** Picks stalagmite bases spread over the floor: a few big ones, many small, never touching each other. */
	private void planSpikes(ActiveDomain domain, ServerLevel level, SkysDomainsConfig.FrostVault cfg) {
		int maxHeight = Math.max(2, Math.min(12, cfg.maxSpikeHeight));
		int chance = (int) Math.round(Math.max(0.0, Math.min(1.0, cfg.spikeDensity)) * 1023);
		List<BlockPos> taken = new ArrayList<>();
		for (FloorCells.Cell cell : floor.cells) {
			if (cell.roll(10, 44) >= chance) continue;
			BlockPos base = cell.floor();
			if (!floor.flatAround(base)) continue;
			int sizeRoll = cell.roll(8, 30); // 0..255
			boolean thick = sizeRoll < 80;
			int height = thick ? maxHeight / 2 + sizeRoll % (maxHeight / 2 + 1) : 2 + sizeRoll % Math.max(1, maxHeight / 2);
			height = Math.min(height, maxHeight);
			int spacing = thick ? 3 : 1;
			boolean clash = false;
			for (BlockPos other : taken) {
				if (Math.abs(other.getX() - base.getX()) <= spacing && Math.abs(other.getZ() - base.getZ()) <= spacing) {
					clash = true;
					break;
				}
			}
			if (clash) continue;
			if (!columnFree(level, base, height, thick)) continue;
			int lean = thick ? 0 : cell.roll(3, 54) % 5; // 0 = straight, 1..4 = leans a little
			taken.add(base);
			spikes.add(new Spike(base, height, thick, lean));
		}
	}

	private static boolean columnFree(ServerLevel level, BlockPos base, int height, boolean thick) {
		int r = thick ? 1 : 0;
		for (int i = 1; i <= height; i++) {
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					BlockPos p = base.offset(dx, i, dz);
					if (!level.isLoaded(p) || !level.getBlockState(p).isAir()) return false;
				}
			}
		}
		return true;
	}

	/** Spikes start once the frost front has passed their base and grow one layer per tick. */
	private void growSpikes(ActiveDomain domain, ServerLevel level, double front) {
		while (spikeFront < spikes.size()) {
			Spike next = spikes.get(spikeFront);
			BlockPos base = next.layers.get(0).get(0).below();
			double d = base.distSqr(BlockPos.containing(domain.center.x, base.getY(), domain.center.z));
			if (Math.sqrt(d) > front) break;
			spikeFront++;
		}
		if (domain.age % 2 != 0) return; // half speed looks more like growing than popping
		int sounds = 3;
		for (int i = 0; i < spikeFront; i++) {
			Spike spike = spikes.get(i);
			if (spike.stopped || spike.grown >= spike.layers.size()) continue;
			List<BlockPos> layer = spike.layers.get(spike.grown);
			BlockState material = spike.material(spike.grown);
			for (BlockPos pos : layer) {
				if (!level.isLoaded(pos) || !growable(level, pos) || entityInside(level, pos)) {
					spike.stopped = true;
					break;
				}
			}
			if (spike.stopped) continue;
			for (BlockPos pos : layer) GroundJournal.place(level, pos, material);
			spike.grown++;
			BlockPos top = layer.get(0);
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, material), top.getX() + 0.5, top.getY() + 0.5, top.getZ() + 0.5,
					12, 0.4, 0.3, 0.4, 0.05);
			level.sendParticles(ParticleTypes.SNOWFLAKE, top.getX() + 0.5, top.getY() + 0.8, top.getZ() + 0.5, 6, 0.3, 0.3, 0.3, 0.03);
			if (sounds-- > 0) {
				level.playSound(null, top.getX(), top.getY(), top.getZ(), SoundEvents.AMETHYST_CLUSTER_PLACE, SoundSource.BLOCKS,
						1.0F, 0.6F + level.getRandom().nextFloat() * 0.5F);
			}
		}
	}

	/** Air, or a snow dusting the frost itself put there. */
	private static boolean growable(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		return state.isAir() || (state.is(Blocks.SNOW) && GroundJournal.isRecorded(level, pos));
	}

	private static boolean entityInside(ServerLevel level, BlockPos pos) {
		return !level.getEntitiesOfClass(LivingEntity.class, new AABB(pos).inflate(0.05)).isEmpty();
	}

	/** Removes the top layer of every spike (called once per close tick). */
	private void shrinkSpikes(ServerLevel level) {
		int sounds = 3;
		for (Spike spike : spikes) {
			if (spike.grown <= 0) continue;
			List<BlockPos> layer = spike.layers.get(--spike.grown);
			BlockState material = spike.material(spike.grown);
			for (BlockPos pos : layer) GroundJournal.restore(level, pos);
			BlockPos top = layer.get(0);
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, material), top.getX() + 0.5, top.getY() + 0.5, top.getZ() + 0.5,
					8, 0.3, 0.3, 0.3, 0.05);
			if (sounds-- > 0) {
				level.playSound(null, top.getX(), top.getY(), top.getZ(), SoundEvents.GLASS_BREAK, SoundSource.BLOCKS, 0.6F, 1.2F);
			}
		}
	}

	// --- water/lava sweep (unchanged ability) ----------------------------------------------------

	/** Converts a slice of the sphere per tick so big radii do not stall the server. */
	private void sweep(ActiveDomain domain, ServerLevel level, SkysDomainsConfig.FrostVault cfg) {
		int r = (int) Math.ceil(domain.radius);
		double r2 = domain.radius * domain.radius;
		int cx = (int) Math.floor(domain.center.x);
		int cy = (int) Math.floor(domain.center.y);
		int cz = (int) Math.floor(domain.center.z);
		int budget = BLOCKS_PER_TICK;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		while (budget-- > 0) {
			if (sweepX * sweepX + sweepY * sweepY + sweepZ * sweepZ <= r2) {
				pos.set(cx + sweepX, cy + sweepY, cz + sweepZ);
				BlockState state = level.getBlockState(pos);
				if (cfg.convertWaterToIce && state.is(Blocks.WATER)) {
					level.setBlockAndUpdate(pos, Blocks.ICE.defaultBlockState());
				} else if (cfg.convertLavaToObsidian && state.is(Blocks.LAVA)) {
					level.setBlockAndUpdate(pos, Blocks.OBSIDIAN.defaultBlockState());
				}
			}
			if (++sweepZ > r) {
				sweepZ = -r;
				if (++sweepY > r) {
					sweepY = -r;
					if (++sweepX > r) {
						sweeping = false;
						return;
					}
				}
			}
		}
	}
}
