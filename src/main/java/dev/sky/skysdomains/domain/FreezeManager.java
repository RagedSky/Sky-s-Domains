package dev.sky.skysdomains.domain;

import dev.sky.skysdomains.network.FreezeSyncPayload;
import dev.sky.skysdomains.network.ModNetworking;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Time freeze. Frozen entities skip {@code Entity#tick()} entirely (mixin), so there is no AI,
 * movement, gravity or attack. The server owns the authoritative set per dimension and mirrors
 * it to nearby clients so animations stop there as well.
 */
public final class FreezeManager {
	private static final Map<ResourceKey<Level>, Set<Integer>> SERVER_FROZEN = new HashMap<>();
	/** Entity ids frozen on this client (filled by the network handler). */
	private static final Set<Integer> CLIENT_FROZEN = Collections.synchronizedSet(new HashSet<>());

	private FreezeManager() {
	}

	/** Called from the {@code Entity#tick} mixin on both sides. */
	public static boolean shouldSkipTick(Entity entity) {
		if (entity.isRemoved()) return false;
		if (entity instanceof LivingEntity living && living.isDeadOrDying()) return false;
		if (entity.level().isClientSide()) {
			return !CLIENT_FROZEN.isEmpty() && CLIENT_FROZEN.contains(entity.getId());
		}
		Set<Integer> set = SERVER_FROZEN.get(entity.level().dimension());
		return set != null && set.contains(entity.getId());
	}

	public static boolean isFrozen(ServerLevel level, Entity entity) {
		Set<Integer> set = SERVER_FROZEN.get(level.dimension());
		return set != null && set.contains(entity.getId());
	}

	public static void freeze(ServerLevel level, Collection<? extends Entity> entities, Vec3 center, double syncRange) {
		if (entities.isEmpty()) return;
		Set<Integer> set = SERVER_FROZEN.computeIfAbsent(level.dimension(), k -> new HashSet<>());
		List<Integer> added = new ArrayList<>();
		for (Entity entity : entities) {
			if (set.add(entity.getId())) added.add(entity.getId());
		}
		if (!added.isEmpty()) {
			ModNetworking.sendAround(level, center, syncRange, new FreezeSyncPayload(added, true));
		}
	}

	public static void unfreeze(ServerLevel level, Collection<Integer> ids, Vec3 center, double syncRange) {
		Set<Integer> set = SERVER_FROZEN.get(level.dimension());
		if (set == null || ids.isEmpty()) return;
		List<Integer> removed = new ArrayList<>();
		for (Integer id : ids) {
			if (set.remove(id)) removed.add(id);
		}
		if (!removed.isEmpty()) {
			ModNetworking.sendAround(level, center, syncRange, new FreezeSyncPayload(removed, false));
		}
	}

	public static void unfreezeOne(ServerLevel level, Entity entity, Vec3 center, double syncRange) {
		unfreeze(level, List.of(entity.getId()), center, syncRange);
	}

	public static void clearServer() {
		SERVER_FROZEN.clear();
	}

	// --- client side mirror -------------------------------------------------------------

	public static void clientApply(Collection<Integer> ids, boolean frozen) {
		if (frozen) CLIENT_FROZEN.addAll(ids);
		else CLIENT_FROZEN.removeAll(ids);
	}

	public static void clientClear() {
		CLIENT_FROZEN.clear();
	}

	public static boolean clientHasFrozen() {
		return !CLIENT_FROZEN.isEmpty();
	}
}
