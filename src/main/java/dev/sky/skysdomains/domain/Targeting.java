package dev.sky.skysdomains.domain;

import dev.sky.skysdomains.config.ConfigManager;
import dev.sky.skysdomains.config.SkysDomainsConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** Decides who counts as an enemy of a domain owner and collects entities inside the sphere. */
public final class Targeting {
	private Targeting() {
	}

	public static boolean isEnemy(ServerPlayer owner, LivingEntity entity) {
		SkysDomainsConfig.Targeting cfg = ConfigManager.get().targeting;
		if (entity == owner || !entity.isAlive() || entity.isSpectator()) return false;
		if (entity.getType() == EntityTypes.ARMOR_STAND) return false;
		if (entity instanceof Player player) {
			if (player.isCreative()) return false;
			if (!cfg.playersAreEnemies) return false;
			return !(cfg.respectTeams && owner.isAlliedTo(player));
		}
		if (cfg.spareOwnPets && entity instanceof OwnableEntity ownable && ownable.getOwner() == owner) return false;
		if (cfg.respectTeams && owner.isAlliedTo(entity)) return false;
		if (cfg.onlyHostileMobs && !(entity instanceof Enemy)) return false;
		return true;
	}

	/** "Guaranteed hit": every enemy living entity whose feet or eyes are inside the sphere, no line of sight needed. */
	public static List<LivingEntity> enemiesInside(ServerLevel level, ServerPlayer owner, Vec3 center, double radius) {
		AABB box = AABB.ofSize(center, radius * 2 + 2, radius * 2 + 2, radius * 2 + 2);
		return level.getEntitiesOfClass(LivingEntity.class, box,
				entity -> isEnemy(owner, entity) && isInside(entity, center, radius));
	}

	/** Every living entity (friend or foe, except spectators) inside the sphere. */
	public static List<LivingEntity> livingInside(ServerLevel level, Vec3 center, double radius) {
		AABB box = AABB.ofSize(center, radius * 2 + 2, radius * 2 + 2, radius * 2 + 2);
		return level.getEntitiesOfClass(LivingEntity.class, box,
				entity -> entity.isAlive() && !entity.isSpectator() && isInside(entity, center, radius));
	}

	public static List<Projectile> projectilesInside(ServerLevel level, Vec3 center, double radius) {
		AABB box = AABB.ofSize(center, radius * 2 + 2, radius * 2 + 2, radius * 2 + 2);
		return level.getEntitiesOfClass(Projectile.class, box, entity -> entity.isAlive() && isInside(entity, center, radius));
	}

	public static boolean isInside(Entity entity, Vec3 center, double radius) {
		double r2 = radius * radius;
		return entity.position().distanceToSqr(center) <= r2 || entity.getEyePosition().distanceToSqr(center) <= r2;
	}
}
