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

/**
 * Journal of every block Ember Garden replaced, so the ground can always be restored: on domain
 * close, on server stop, and (via a persistent level attachment) after a crash or an unloaded
 * chunk. Entries are keyed by position; the stored state is the original block.
 */
public final class EmberGround {
	public record Entry(BlockPos pos, BlockState original) {
		public static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				BlockPos.CODEC.fieldOf("pos").forGetter(Entry::pos),
				BlockState.CODEC.fieldOf("original").forGetter(Entry::original)
		).apply(instance, Entry::new));
	}

	/** Persistent per-level journal (written back whenever it changed, before the level is saved). */
	public static final AttachmentType<List<Entry>> JOURNAL = AttachmentRegistry.create(SkysDomains.id("ember_ground"), builder -> builder
			.initializer(List::of)
			.persistent(Entry.CODEC.listOf()));

	private static final Map<ResourceKey<Level>, Map<Long, Entry>> LIVE = new HashMap<>();
	private static final Map<ResourceKey<Level>, Boolean> DIRTY = new HashMap<>();
	/** Leftovers found in the journal on level load, restored as soon as their chunks are loaded. */
	private static final Map<ResourceKey<Level>, List<Entry>> LEFTOVERS = new HashMap<>();

	private EmberGround() {
	}

	public static void register() {
		ServerLevelEvents.LOAD.register((server, level) -> {
			List<Entry> stored = level.getAttachedOrElse(JOURNAL, List.of());
			LIVE.put(level.dimension(), new LinkedHashMap<>());
			DIRTY.put(level.dimension(), false);
			if (!stored.isEmpty()) {
				LEFTOVERS.put(level.dimension(), new ArrayList<>(stored));
				SkysDomains.LOGGER.info("Ember Garden: {} ground blocks in {} were not restored last time, restoring", stored.size(), level.dimension().identifier());
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

	/** Remembers the original block at {@code pos} (only the first time; later changes keep the first original). */
	public static void record(ServerLevel level, BlockPos pos, BlockState original) {
		Map<Long, Entry> map = LIVE.computeIfAbsent(level.dimension(), k -> new LinkedHashMap<>());
		map.putIfAbsent(pos.asLong(), new Entry(pos.immutable(), original));
		DIRTY.put(level.dimension(), true);
	}

	public static boolean isRecorded(ServerLevel level, BlockPos pos) {
		Map<Long, Entry> map = LIVE.get(level.dimension());
		return map != null && map.containsKey(pos.asLong());
	}

	/**
	 * Puts the original block back if the position still holds something Ember Garden created
	 * (or air, e.g. a removed plant). Player-placed blocks are left alone. Always forgets the entry.
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
		// Lower blocks first so plants find their support block again.
		entries.sort((a, b) -> Integer.compare(a.pos().getY(), b.pos().getY()));
		for (Entry entry : entries) applyRestore(level, entry);
		flush(level);
	}

	private static void applyRestore(ServerLevel level, Entry entry) {
		if (!level.isLoaded(entry.pos())) return;
		BlockState current = level.getBlockState(entry.pos());
		if (isOurs(current) || current.isAir()) {
			level.setBlock(entry.pos(), entry.original(), Block.UPDATE_ALL);
		}
	}

	public static boolean isOurs(BlockState state) {
		return state.is(ModBlocks.SCORCHED_GROUND) || state.is(ModBlocks.MOLTEN_GROUND)
				|| state.is(Blocks.LAVA) || state.is(Blocks.FIRE);
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
		leftovers.sort((a, b) -> Integer.compare(a.pos().getY(), b.pos().getY()));
		Iterator<Entry> iterator = leftovers.iterator();
		boolean changed = false;
		while (iterator.hasNext()) {
			Entry entry = iterator.next();
			if (!level.isLoaded(entry.pos())) continue;
			applyRestore(level, entry);
			iterator.remove();
			changed = true;
		}
		if (changed) DIRTY.put(level.dimension(), true);
	}
}
