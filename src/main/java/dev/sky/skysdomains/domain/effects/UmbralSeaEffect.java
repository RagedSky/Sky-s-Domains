package dev.sky.skysdomains.domain.effects;

import dev.sky.skysdomains.config.ConfigManager;
import dev.sky.skysdomains.config.SkysDomainsConfig;
import dev.sky.skysdomains.domain.ActiveDomain;
import dev.sky.skysdomains.domain.DomainEffect;
import dev.sky.skysdomains.domain.DomainPhase;
import dev.sky.skysdomains.domain.Targeting;
import dev.sky.skysdomains.network.DomainEventPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Umbral Sea: Darkness for everyone but the owner, enemies are slowly pulled towards the centre
 * and "drown in shadows" there (armour-ignoring magic damage every second).
 */
public final class UmbralSeaEffect implements DomainEffect {
	@Override
	public void tick(ActiveDomain domain, ServerLevel level) {
		if (!domain.inPhase(DomainPhase.ACTIVE)) return;
		SkysDomainsConfig.UmbralSea cfg = ConfigManager.get().domains.umbralSea;
		double pull = EffectUtil.perTier(cfg.pullStrength, domain);
		float centerDamage = EffectUtil.perTier(cfg.centerDamagePerSecond, domain);
		boolean second = domain.phaseAge % 20 == 0;
		double centerR2 = cfg.centerRadius * cfg.centerRadius;

		for (LivingEntity entity : Targeting.livingInside(level, domain.center, domain.radius)) {
			if (entity == domain.owner) continue;
			boolean enemy = Targeting.isEnemy(domain.owner, entity);
			if (second && (enemy || cfg.darknessForOtherPlayers)) {
				EffectUtil.refreshEffect(entity, MobEffects.DARKNESS, 0);
			}
			if (!enemy) continue;

			Vec3 toCenter = domain.center.subtract(entity.position());
			double distance = toCenter.length();
			if (distance > 0.5) {
				Vec3 velocity = entity.getDeltaMovement().add(toCenter.scale(pull / distance));
				entity.setDeltaMovement(velocity);
				if (entity instanceof ServerPlayer) entity.syncVelocity = true;
			}
			if (second && entity.position().distanceToSqr(domain.center) <= centerR2) {
				entity.hurtServer(level, level.damageSources().magic(), centerDamage);
				domain.event(DomainEventPayload.CENTER_HIT, entity.position(), entity.getId(), 0);
			}
		}
	}
}
