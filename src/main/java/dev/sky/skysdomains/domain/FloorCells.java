package dev.sky.skysdomains.domain;

import dev.sky.skysdomains.config.ConfigManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The floor of a domain: for every column inside the sphere the highest solid block (inside the
 * sphere) that has free space above it. Sorted by jittered distance from the centre so an
 * effect can "spread" over the floor with an irregular front. Shared by Ember Garden and Frost Vault.
 */
public final class FloorCells {
	/** One floor column; {@code hash} is a per-domain random value for deterministic variation. */
	public record Cell(BlockPos floor, double distance, long hash) {
		public int roll(int bits, int shift) {
			return (int) ((hash >>> shift) & ((1 << bits) - 1));
		}
	}

	public final List<Cell> cells = new ArrayList<>();
	private final Map<Long, Integer> floorHeight = new HashMap<>();

	public void collect(ActiveDomain domain, ServerLevel level) {
		int r = (int) Math.ceil(domain.radius);
		double r2 = domain.radius * domain.radius;
		int cx = (int) Math.floor(domain.center.x);
		int cy = (int) Math.floor(domain.center.y);
		int cz = (int) Math.floor(domain.center.z);
		int up = r;   // the whole sphere: the owner may hover above the ground or stand on a ledge
		int down = r;
		long seed = domain.id.getLeastSignificantBits();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				if (dx * dx + dz * dz > r2) continue;
				for (int y = cy + up; y >= cy - down; y--) {
					int dy = y - cy;
					if (dx * dx + dy * dy + dz * dz > r2) continue;
					pos.set(cx + dx, y, cz + dz);
					if (!level.isLoaded(pos)) break;
					BlockState state = level.getBlockState(pos);
					if (!isFloorCandidate(level, pos, state)) continue;
					BlockState above = level.getBlockState(pos.above());
					if (isSolidCover(level, pos.above(), above)) break; // buried, not a floor
					double horizontal = Math.sqrt(dx * dx + dz * dz);
					long hash = mix(seed ^ pos.asLong());
					double jitter = ((hash >>> 40) & 0xFF) / 255.0 * 2.2 - 1.1;
					cells.add(new Cell(pos.immutable(), horizontal + jitter, hash));
					floorHeight.put(columnKey(cx + dx, cz + dz), y);
					break;
				}
			}
		}
		cells.sort((a, b) -> Double.compare(a.distance(), b.distance()));
		dev.sky.skysdomains.SkysDomains.debug("Floor scan: {} columns inside radius {} around {} (y {}..{})", cells.size(), domain.radius, domain.center, cy - down, cy + up);
	}

	public int size() {
		return cells.size();
	}

	public Cell get(int index) {
		return cells.get(index);
	}

	public void clear() {
		cells.clear();
		floorHeight.clear();
	}

	/** Floor height of the column, or null if the column has no floor inside the sphere. */
	public Integer heightAt(int x, int z) {
		return floorHeight.get(columnKey(x, z));
	}

	/** True if all eight horizontal neighbours are floor blocks at the same height. */
	public boolean flatAround(BlockPos pos) {
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				if (dx == 0 && dz == 0) continue;
				Integer y = heightAt(pos.getX() + dx, pos.getZ() + dz);
				if (y == null || y != pos.getY()) return false;
			}
		}
		return true;
	}

	/** Something that hides the block below it: a full solid block or liquid (tree canopies do not count). */
	private static boolean isSolidCover(ServerLevel level, BlockPos pos, BlockState state) {
		if (state.liquid()) return true;
		if (state.is(BlockTags.LEAVES)) return false;
		return state.isCollisionShapeFullBlock(level, pos);
	}

	/** A full, breakable, container-free solid block that is not on the config blacklist (trees are left alone). */
	public static boolean isFloorCandidate(ServerLevel level, BlockPos pos, BlockState state) {
		if (state.isAir() || state.liquid() || !state.getFluidState().isEmpty()) return false;
		if (!state.isCollisionShapeFullBlock(level, pos)) return false;
		if (state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS)) return false;
		if (state.hasBlockEntity()) return false;
		if (state.getDestroySpeed(level, pos) < 0.0F) return false;
		if (state.is(BlockTags.FEATURES_CANNOT_REPLACE) || state.is(BlockTags.WITHER_IMMUNE)) return false;
		if (GroundJournal.isDomainBlock(state)) return false;
		String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
		return !ConfigManager.get().domains.groundBlacklist.contains(id);
	}

	/** Grass, flowers, crops, snow layers: things that would pop off a replaced floor block. */
	public static boolean isRemovablePlant(BlockState state) {
		return state.canBeReplaced() || state.is(BlockTags.FLOWERS) || state.is(BlockTags.SAPLINGS)
				|| state.is(BlockTags.CROPS) || state.is(BlockTags.SNOW);
	}

	/**
	 * Clears a plant above the floor (journaled). Returns false if something non-removable is there,
	 * in which case the floor block must be left alone.
	 */
	public static boolean clearAbove(ServerLevel level, BlockPos floor) {
		BlockPos abovePos = floor.above();
		BlockState above = level.getBlockState(abovePos);
		if (above.isAir()) return true;
		if (!isRemovablePlant(above)) return false;
		GroundJournal.place(level, abovePos, Blocks.AIR.defaultBlockState());
		return true;
	}

	private static long columnKey(int x, int z) {
		return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
	}

	private static long mix(long value) {
		value ^= value >>> 33;
		value *= 0xff51afd7ed558ccdL;
		value ^= value >>> 33;
		value *= 0xc4ceb9fe1a85ec53L;
		value ^= value >>> 33;
		return value;
	}
}
