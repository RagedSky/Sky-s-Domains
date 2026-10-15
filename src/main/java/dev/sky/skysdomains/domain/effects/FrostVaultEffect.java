package dev.sky.skysdomains.domain.effects;

import dev.sky.skysdomains.config.ConfigManager;
import dev.sky.skysdomains.config.SkysDomainsConfig;
import dev.sky.skysdomains.domain.ActiveDomain;
import dev.sky.skysdomains.domain.DomainEffect;
import dev.sky.skysdomains.domain.DomainPhase;
import dev.sky.skysdomains.domain.Targeting;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Frost Vault: enemies accumulate freeze ticks fast (like powder snow, but quicker) and get
 * Slowness; once fully frozen they take freeze damage every second. Water inside turns to ice,
 * lava to obsidian (spread over several ticks). The owner never freezes.
 */
public final class FrostVaultEffect implements DomainEffect {
	private static final int FULLY_FROZEN = 140;
	private static final int BLOCKS_PER_TICK = 600;

	private int sweepX;
	private int sweepY;
	private int sweepZ;
	private boolean sweeping;

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
		if (!domain.inPhase(DomainPhase.ACTIVE)) return;
		SkysDomainsConfig.FrostVault cfg = ConfigManager.get().domains.frostVault;

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
