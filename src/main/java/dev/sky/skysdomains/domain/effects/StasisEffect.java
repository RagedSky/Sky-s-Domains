package dev.sky.skysdomains.domain.effects;

import dev.sky.skysdomains.config.ConfigManager;
import dev.sky.skysdomains.config.SkysDomainsConfig;
import dev.sky.skysdomains.domain.ActiveDomain;
import dev.sky.skysdomains.domain.DomainEffect;
import dev.sky.skysdomains.domain.DomainPhase;
import dev.sky.skysdomains.domain.FreezeManager;
import dev.sky.skysdomains.domain.Targeting;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;

import java.util.ArrayList;
import java.util.List;

/**
 * Stasis: every projectile inside the sphere is deleted, mobs do not tick at all (no AI, movement,
 * gravity, attacks). Players keep moving unless configured otherwise; a "frozen" player gets
 * maximal Slowness, Mining Fatigue and Weakness instead of a tick freeze.
 */
public final class StasisEffect implements DomainEffect {
	@Override
	public void tick(ActiveDomain domain, ServerLevel level) {
		if (!domain.inPhase(DomainPhase.ACTIVE)) return;
		SkysDomainsConfig.Stasis cfg = ConfigManager.get().domains.stasis;

		List<Entity> toFreeze = new ArrayList<>();
		for (LivingEntity entity : Targeting.livingInside(level, domain.center, domain.radius)) {
			if (entity == domain.owner) continue;
			if (entity instanceof Player player) {
				if (cfg.freezePlayers && domain.phaseAge % 10 == 0 && Targeting.isEnemy(domain.owner, player)) {
					EffectUtil.refreshEffect(player, MobEffects.SLOWNESS, 9);
					EffectUtil.refreshEffect(player, MobEffects.MINING_FATIGUE, 9);
					EffectUtil.refreshEffect(player, MobEffects.WEAKNESS, 9);
				}
				continue;
			}
			if (!FreezeManager.isFrozen(level, entity)) {
				toFreeze.add(entity);
				domain.frozenIds.add(entity.getId());
			}
		}
		if (cfg.deleteProjectiles) {
			for (Projectile projectile : Targeting.projectilesInside(level, domain.center, domain.radius)) {
				projectile.discard();
			}
		}
		FreezeManager.freeze(level, toFreeze, domain.center, domain.syncRange());
	}
}
