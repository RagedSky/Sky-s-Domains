package dev.sky.skysdomains.domain.effects;

import dev.sky.skysdomains.common.ModSounds;
import dev.sky.skysdomains.config.ConfigManager;
import dev.sky.skysdomains.config.SkysDomainsConfig;
import dev.sky.skysdomains.domain.ActiveDomain;
import dev.sky.skysdomains.domain.DomainEffect;
import dev.sky.skysdomains.domain.DomainPhase;
import dev.sky.skysdomains.domain.Targeting;
import dev.sky.skysdomains.network.DomainEventPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;

/**
 * Glass Heaven: pure defence. Owner gets Resistance + Regeneration, enemies Weakness, hostile
 * projectiles shatter on the sphere wall. No direct damage.
 */
public final class GlassHeavenEffect implements DomainEffect {
	@Override
	public void tick(ActiveDomain domain, ServerLevel level) {
		if (!domain.inPhase(DomainPhase.ACTIVE)) return;
		SkysDomainsConfig.GlassHeaven cfg = ConfigManager.get().domains.glassHeaven;

		if (domain.phaseAge % 20 == 0) {
			EffectUtil.refreshEffect(domain.owner, MobEffects.RESISTANCE, cfg.resistanceAmplifier);
			EffectUtil.refreshEffect(domain.owner, MobEffects.REGENERATION, cfg.regenerationAmplifier);
			for (LivingEntity enemy : Targeting.enemiesInside(level, domain.owner, domain.center, domain.radius)) {
				EffectUtil.refreshEffect(enemy, MobEffects.WEAKNESS, cfg.weaknessAmplifier);
			}
		}

		if (cfg.shatterProjectiles) {
			for (Projectile projectile : Targeting.projectilesInside(level, domain.center, domain.radius + 1.0)) {
				if (projectile.getOwner() == domain.owner) continue;
				domain.event(DomainEventPayload.SHATTER, projectile.position(), 0, 0);
				level.playSound(null, projectile.getX(), projectile.getY(), projectile.getZ(), ModSounds.GLASS_SHATTER, SoundSource.PLAYERS, 1.0F, 1.2F);
				projectile.discard();
			}
		}
	}
}
