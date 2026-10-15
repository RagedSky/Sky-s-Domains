package dev.sky.skysdomains.domain.effects;

import dev.sky.skysdomains.config.ConfigManager;
import dev.sky.skysdomains.config.SkysDomainsConfig;
import dev.sky.skysdomains.domain.ActiveDomain;
import dev.sky.skysdomains.domain.DomainEffect;
import dev.sky.skysdomains.domain.DomainPhase;
import dev.sky.skysdomains.domain.Targeting;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

/**
 * Ember Garden: enemies take fire damage every second and visibly burn; the owner is immune to
 * fire and lava. The burning ground is purely visual (client particles, no fire blocks).
 */
public final class EmberGardenEffect implements DomainEffect {
	@Override
	public void tick(ActiveDomain domain, ServerLevel level) {
		if (!domain.inPhase(DomainPhase.ACTIVE)) return;
		SkysDomainsConfig.EmberGarden cfg = ConfigManager.get().domains.emberGarden;
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
	}
}
