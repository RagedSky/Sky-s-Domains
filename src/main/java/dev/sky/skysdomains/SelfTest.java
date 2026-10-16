package dev.sky.skysdomains;

import com.mojang.authlib.GameProfile;
import dev.sky.skysdomains.domain.ActiveDomain;
import dev.sky.skysdomains.domain.DomainManager;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.UUID;

/**
 * Headless functional test, enabled with {@code -Dskysdomains.selftest=<domain id>} on a dedicated
 * server: opens the domain for a fake player at the spawn, logs what the floor looks like while
 * it runs and whether everything is restored afterwards, then stops the server. Never active in
 * normal play.
 */
final class SelfTest {
	private static DomainType type;
	private static ActiveDomain domain;
	private static ServerPlayer fake;
	private static int ticks;

	private SelfTest() {
	}

	static void register() {
		String id = System.getProperty("skysdomains.selftest");
		if (id == null) return;
		type = DomainType.valueOf(id.toUpperCase());
		ServerLifecycleEvents.SERVER_STARTED.register(SelfTest::start);
		ServerTickEvents.END_SERVER_TICK.register(SelfTest::tick);
	}

	private static void start(MinecraftServer server) {
		dev.sky.skysdomains.config.ConfigManager.get().debugLogging = true;
		ServerLevel level = server.overworld();
		// Find dry, solid land near the spawn (worlds are random: the spawn may be an ocean).
		int bx = 0, bz = 0, y = 0;
		search:
		for (int ring = 0; ring <= 12; ring++) {
			for (int dx = -ring; dx <= ring; dx++) {
				for (int dz = -ring; dz <= ring; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) continue;
					int x = dx * 16, z = dz * 16;
					level.getChunk(x >> 4, z >> 4);
					int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
					BlockState ground = level.getBlockState(new BlockPos(x, top - 1, z));
					if (top > level.getMinY() + 20 && level.getBlockState(new BlockPos(x, top, z)).isAir()
							&& ground.isCollisionShapeFullBlock(level, new BlockPos(x, top - 1, z)) && ground.getFluidState().isEmpty()
							&& !ground.is(Blocks.ICE) && !ground.is(net.minecraft.tags.BlockTags.LEAVES)) {
						bx = x; bz = z; y = top;
						break search;
					}
				}
			}
		}
		// Keep the area loaded like a real player would (force-load chunks around the test spot).
		for (int cx = -2; cx <= 2; cx++) for (int cz = -2; cz <= 2; cz++) level.setChunkForced((bx >> 4) + cx, (bz >> 4) + cz, true);
		fake = new ServerPlayer(server, level, new GameProfile(UUID.randomUUID(), "SelfTest"), ClientInformation.createDefault());
		fake.setPos(bx + 0.5, y + Integer.getInteger("skysdomains.selftest.hover", 0), bz + 0.5);
		SkysDomains.LOGGER.info("[selftest] ground at {} {} {} is {}", bx, y - 1, bz, level.getBlockState(new BlockPos(bx, y - 1, bz)));
		try {
			domain = DomainManager.openDirect(fake, type, TalismanTier.values()[1]);
			SkysDomains.LOGGER.info("[selftest] opened {} radius {} phases {}", type, domain.radius, domain.phases);
		} catch (Throwable t) {
			SkysDomains.LOGGER.error("[selftest] open failed", t);
			server.halt(false);
		}
	}

	private static void tick(MinecraftServer server) {
		if (domain == null) return;
		ticks++;
		if (ticks == 20 || ticks == 60 || ticks == 120 || ticks == domain.totalTicks() + 5 || ticks == domain.totalTicks() + 40) {
			report(server.overworld(), "tick " + ticks + " phase " + domain.phaseName() + " age " + domain.age);
		}
		if (ticks >= domain.totalTicks() + 40) {
			SkysDomains.LOGGER.info("[selftest] done, finished={}", domain.finished);
			server.halt(false);
		}
	}

	private static void report(ServerLevel level, String label) {
		int r = (int) Math.ceil(domain.radius);
		int cx = (int) Math.floor(domain.center.x), cy = (int) Math.floor(domain.center.y), cz = (int) Math.floor(domain.center.z);
		int ice = 0, packed = 0, blue = 0, snow = 0, scorched = 0, molten = 0, lava = 0, grass = 0, aboveFloor = 0;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) for (int dy = -12; dy <= 14; dy++) {
			pos.set(cx + dx, cy + dy, cz + dz);
			BlockState s = level.getBlockState(pos);
			if (s.is(Blocks.ICE)) { ice++; if (dy >= 0) aboveFloor++; }
			else if (s.is(Blocks.PACKED_ICE)) { packed++; if (dy >= 0) aboveFloor++; }
			else if (s.is(Blocks.BLUE_ICE)) { blue++; if (dy >= 0) aboveFloor++; }
			else if (s.is(Blocks.SNOW)) snow++;
			else if (s.is(Blocks.GRASS_BLOCK)) grass++;
			else if (s.is(Blocks.LAVA)) lava++;
			else if (s.is(dev.sky.skysdomains.common.ModBlocks.SCORCHED_GROUND)) scorched++;
			else if (s.is(dev.sky.skysdomains.common.ModBlocks.MOLTEN_GROUND)) molten++;
		}
		SkysDomains.LOGGER.info("[selftest] {}: ice={} packed={} blue={} (at/above owner y: {}) snow={} grass={} scorched={} molten={} lava={}",
				label, ice, packed, blue, aboveFloor, snow, grass, scorched, molten, lava);
	}
}
