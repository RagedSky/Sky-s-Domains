package dev.sky.skysdomains.domain;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.sky.skysdomains.SkysDomains;
import dev.sky.skysdomains.common.ModBlocks;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Journal of every block a domain replaced in the world (Ember Garden ground, Frost Vault ice
 * floor and spikes), so the world can always be put back: on domain close, on server stop, and
 * (via a persistent level attachment) after a crash or when a chunk was unloaded meanwhile.
 */
public final class GroundJournal {
	/** {@code placed} is the block the domain put there (so melted ice = water is still recognised as ours). */
	public record Entry(BlockPos pos, BlockState original, Optional<BlockState> placed) {
		public static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				BlockPos.CODEC.fieldOf("pos").forGetter(Entry::pos),
				BlockState.CODEC.fieldOf("original").forGetter(Entry::original),
				BlockState.CODEC.optionalFieldOf("placed").forGetter(Entry::placed)
		).apply(instance, Entry::new));
	}

	public static final AttachmentType<List<Entry>> JOURNAL = AttachmentRegistry.create(SkysDomains.id("ground_journal"), builder -> builder
			.initializer(List::of)
			.persistent(Entry.CODEC.listOf()));

	private static final Map<ResourceKey<Level>, Map<Long, Entry>> LIVE = new HashMap<>();
	private static final Map<ResourceKey<Level>, Boolean> DIRTY = new HashMap<>();
	/** Entries found in the journal on level load, restored as soon as their chunks are loaded. */
	private static final Map<ResourceKey<Level>, List<Entry>> LEFTOVERS = new HashMap<>();

	private GroundJournal() {
	}

	public static void register() {
		ServerLevelEvents.LOAD.register((server, level) -> {
			List<Entry> stored = level.getAttachedOrElse(JOURNAL, List.of());
			LIVE.put(level.dimension(), new LinkedHashMap<>());
			DIRTY.put(level.dimension(), false);
			if (!stored.isEmpty()) {
				LEFTOVERS.put(level.dimension(), new ArrayList<>(stored));
				SkysDomains.LOGGER.info("{} domain blocks in {} were not restored last time, restoring", stored.size(), level.dimension().identifier());
			}
		});
		ServerLevelEvents.UNLOAD.register((server, level) -> {
			flush(level);
			LIVE.remove(level.dimension());
			DIRTY.remove(level.dimension());
			LEFTOVERS.remove(level.dimension());
		});
		ServerTickEvents.END_LEVEL_TICK.register(level -> {
			if (level.getGameTime() % 20 == 0) restoreLeftovers(level);
			if (Boolean.TRUE.equals(DIRTY.get(level.dimension()))) flush(level);
		});
		ServerLifecycleEvents.BEFORE_SAVE.register((server, flush, force) -> {
			for (ServerLevel level : server.getAllLevels()) flush(level);
		});
	}

	// --- journal ----------------------------------------------------------------------------

	/** Replaces the block at {@code pos}, remembering the original (only the first time for a position). */
	public static void place(ServerLevel level, BlockPos pos, BlockState replacement) {
		Map<Long, Entry> map = LIVE.computeIfAbsent(level.dimension(), k -> new LinkedHashMap<>());
		BlockState original = level.getBlockState(pos);
		Entry existing = map.get(pos.asLong());
		// Keep the very first original, but remember the latest thing we placed (plant -> air -> snow).
		map.put(pos.asLong(), new Entry(pos.immutable(), existing == null ? original : existing.original(), Optional.of(replacement)));
		DIRTY.put(level.dimension(), true);
		level.setBlock(pos, replacement, Block.UPDATE_ALL);
	}

	public static boolean isRecorded(ServerLevel level, BlockPos pos) {
		Map<Long, Entry> map = LIVE.get(level.dimension());
		return map != null && map.containsKey(pos.asLong());
	}

	/**
	 * Puts the original block back if the position still holds what the domain placed (or air,
	 * or water from melted ice). Player-placed blocks are left alone. Always forgets the entry.
	 */
	public static void restore(ServerLevel level, BlockPos pos) {
		Map<Long, Entry> map = LIVE.get(level.dimension());
		if (map == null) return;
		Entry entry = map.remove(pos.asLong());
		if (entry == null) return;
		DIRTY.put(level.dimension(), true);
		applyRestore(level, entry);
	}

	/** Restores everything still journaled for the level (domain close, server stop). */
	public static void restoreAll(ServerLevel level) {
		Map<Long, Entry> map = LIVE.get(level.dimension());
		if (map == null || map.isEmpty()) return;
		List<Entry> entries = new ArrayList<>(map.values());
		map.clear();
		DIRTY.put(level.dimension(), true);
		restoreSorted(level, entries);
		flush(level);
	}

	/** Spikes top-down (nothing left floating), then floors bottom-up (plants find their support). */
	private static void restoreSorted(ServerLevel level, List<Entry> entries) {
		List<Entry> toAir = new ArrayList<>();
		List<Entry> toBlock = new ArrayList<>();
		for (Entry entry : entries) (entry.original().isAir() ? toAir : toBlock).add(entry);
		toAir.sort((a, b) -> Integer.compare(b.pos().getY(), a.pos().getY()));
		toBlock.sort((a, b) -> Integer.compare(a.pos().getY(), b.pos().getY()));
		for (Entry entry : toAir) applyRestore(level, entry);
		for (Entry entry : toBlock) applyRestore(level, entry);
	}

	private static void applyRestore(ServerLevel level, Entry entry) {
		if (!level.isLoaded(entry.pos())) {
			// Chunk gone meanwhile: keep the entry, it is restored when the chunk comes back.
			LEFTOVERS.computeIfAbsent(level.dimension(), k -> new ArrayList<>()).add(entry);
			DIRTY.put(level.dimension(), true);
			return;
		}
		BlockState current = level.getBlockState(entry.pos());
		boolean ours = current.isAir() || isDomainBlock(current)
				|| entry.placed().map(placed -> placed.is(current.getBlock())).orElse(false)
				|| (current.is(Blocks.WATER) && entry.placed().map(GroundJournal::isMeltable).orElse(false));
		if (ours) {
			level.setBlock(entry.pos(), entry.original(), Block.UPDATE_ALL);
		}
	}

	/** Blocks that only domains place in this mod's context (and vanilla by-products of them). */
	public static boolean isDomainBlock(BlockState state) {
		return state.is(ModBlocks.SCORCHED_GROUND) || state.is(ModBlocks.MOLTEN_GROUND)
				|| state.is(Blocks.LAVA) || state.is(Blocks.FIRE);
	}

	private static boolean isMeltable(BlockState placed) {
		return placed.is(Blocks.ICE) || placed.is(Blocks.FROSTED_ICE);
	}

	private static void flush(ServerLevel level) {
		Map<Long, Entry> map = LIVE.get(level.dimension());
		List<Entry> list = map == null || map.isEmpty() ? List.of() : List.copyOf(map.values());
		List<Entry> leftovers = LEFTOVERS.get(level.dimension());
		if (leftovers != null && !leftovers.isEmpty()) {
			List<Entry> merged = new ArrayList<>(list);
			merged.addAll(leftovers);
			list = merged;
		}
		if (list.isEmpty()) {
			level.removeAttached(JOURNAL);
		} else {
			level.setAttached(JOURNAL, list);
		}
		DIRTY.put(level.dimension(), false);
	}

	private static void restoreLeftovers(ServerLevel level) {
		List<Entry> leftovers = LEFTOVERS.get(level.dimension());
		if (leftovers == null || leftovers.isEmpty()) return;
		List<Entry> ready = new ArrayList<>();
		Iterator<Entry> iterator = leftovers.iterator();
		while (iterator.hasNext()) {
			Entry entry = iterator.next();
			if (!level.isLoaded(entry.pos())) continue;
			ready.add(entry);
			iterator.remove();
		}
		if (!ready.isEmpty()) {
			restoreSorted(level, ready);
			DIRTY.put(level.dimension(), true);
			SkysDomains.debug("Restored {} journaled blocks in {}", ready.size(), level.dimension().identifier());
		}
	}
}
