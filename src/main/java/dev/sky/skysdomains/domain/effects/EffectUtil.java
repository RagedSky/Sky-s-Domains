package dev.sky.skysdomains.domain.effects;

import dev.sky.skysdomains.domain.ActiveDomain;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

/** Small helpers shared by the domain effects. */
final class EffectUtil {
	private EffectUtil() {
	}

	/** Re-applies a short effect every second so it ends immediately when the domain closes. */
	static void refreshEffect(LivingEntity entity, Holder<MobEffect> effect, int amplifier) {
		entity.addEffect(new MobEffectInstance(effect, 45, amplifier, true, false));
	}

	static <T> T perTier(T[] values, ActiveDomain domain) {
		return values[Math.min(domain.tier.index(), values.length - 1)];
	}

	static float perTier(float[] values, ActiveDomain domain) {
		return values[Math.min(domain.tier.index(), values.length - 1)];
	}

	static int perTier(int[] values, ActiveDomain domain) {
		return values[Math.min(domain.tier.index(), values.length - 1)];
	}

	static double perTier(double[] values, ActiveDomain domain) {
		return values[Math.min(domain.tier.index(), values.length - 1)];
	}
}
