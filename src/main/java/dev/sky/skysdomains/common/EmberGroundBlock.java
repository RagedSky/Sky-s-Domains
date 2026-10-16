package dev.sky.skysdomains.common;

import dev.sky.skysdomains.domain.effects.EmberGardenEffect;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Temporary ground placed by Ember Garden (scorched = glowing cracks, molten = magma-like).
 * Unbreakable, drops nothing and is always restored to the original block when the domain closes.
 * Enemies that step on it take hot-floor damage and catch fire; the owner and allies are spared.
 */
public final class EmberGroundBlock extends Block {
	private final boolean molten;

	public EmberGroundBlock(Properties properties, boolean molten) {
		super(properties);
		this.molten = molten;
	}

	public boolean isMolten() {
		return molten;
	}

	@Override
	public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
		if (level instanceof ServerLevel server && entity instanceof LivingEntity living
				&& !entity.isSteppingCarefully() && !entity.fireImmune()) {
			float damage = EmberGardenEffect.groundDamageFor(server, pos, living);
			if (damage > 0.0F) {
				living.hurtServer(server, server.damageSources().hotFloor(), damage);
				if (living.isAlive()) living.igniteForSeconds(molten ? 4.0F : 2.0F);
			}
		}
		super.stepOn(level, pos, state, entity);
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (!level.getBlockState(pos.above()).isAir()) return;
		int chance = molten ? 6 : 14;
		if (random.nextInt(chance) == 0) {
			double x = pos.getX() + random.nextDouble();
			double z = pos.getZ() + random.nextDouble();
			double y = pos.getY() + 1.02;
			if (molten && random.nextBoolean()) {
				level.addParticle(ParticleTypes.LAVA, x, y, z, 0.0, 0.0, 0.0);
			} else {
				level.addParticle(ParticleTypes.SMALL_FLAME, x, y, z, 0.0, 0.02 + random.nextDouble() * 0.03, 0.0);
			}
		}
		if (random.nextInt(24) == 0) {
			level.addParticle(ParticleTypes.LARGE_SMOKE, pos.getX() + random.nextDouble(), pos.getY() + 1.05,
					pos.getZ() + random.nextDouble(), 0.0, 0.04, 0.0);
		}
	}
}
