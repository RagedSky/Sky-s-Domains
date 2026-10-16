package dev.sky.skysdomains.domain;

import dev.sky.skysdomains.DomainType;
import dev.sky.skysdomains.SkysDomains;
import dev.sky.skysdomains.TalismanTier;
import dev.sky.skysdomains.common.ModSounds;
import dev.sky.skysdomains.common.TalismanItem;
import dev.sky.skysdomains.common.TalismanStats;
import dev.sky.skysdomains.config.ConfigManager;
import dev.sky.skysdomains.config.SkysDomainsConfig;
import dev.sky.skysdomains.domain.effects.EmberGardenEffect;
import dev.sky.skysdomains.domain.effects.FrostVaultEffect;
import dev.sky.skysdomains.domain.effects.GlassHeavenEffect;
import dev.sky.skysdomains.domain.effects.StasisEffect;
import dev.sky.skysdomains.domain.effects.StructuralCollapseEffect;
import dev.sky.skysdomains.domain.effects.UmbralSeaEffect;
import dev.sky.skysdomains.network.DomainClosePayload;
import dev.sky.skysdomains.network.DomainEventPayload;
import dev.sky.skysdomains.network.DomainPhasePayload;
import dev.sky.skysdomains.network.ModNetworking;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Owns all active domains on the server, handles activation requests, ticking, phase changes,
 * owner immunity, the clash rule and late-join synchronisation.
 */
public final class DomainManager {
	private static final Map<ResourceKey<Level>, List<ActiveDomain>> DOMAINS = new HashMap<>();

	/** Set while a domain applies its own damage so the owner can be made immune. */
	private static ActiveDomain damagingDomain;
	/** Set while a block-damage explosion runs so vanilla explosion damage is suppressed (we apply our own). */
	private static boolean suppressExplosionDamage;

	private DomainManager() {
	}

	public static void register() {
		ServerTickEvents.END_LEVEL_TICK.register(DomainManager::tickLevel);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			// Close every domain properly so effects undo their world changes before the levels are saved.
			for (List<ActiveDomain> list : DOMAINS.values()) {
				for (ActiveDomain domain : list) {
					if (!domain.finished) {
						domain.collapsed = true;
						domain.finished = true;
						close(domain);
					}
				}
			}
			DOMAINS.clear();
			FreezeManager.clearServer();
		});
		ServerPlayerEvents.LEAVE.register(player -> collapseOwnedBy(player.getUUID(), player.level()));
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(DomainManager::allowDamage);
	}

	// --- activation -------------------------------------------------------------------------

	public static void handleActivate(ServerPlayer player) {
		ItemStack offhand = player.getOffhandItem();
		if (!(offhand.getItem() instanceof TalismanItem)) {
			deny(player, "message.skysdomains.no_talisman");
			return;
		}
		DomainType type = TalismanItem.domainOf(offhand);
		if (type == null) {
			deny(player, "message.skysdomains.no_sigil");
			return;
		}
		if (ownedBy(player) != null) {
			deny(player, "message.skysdomains.already_open");
			return;
		}
		int cooldown = EnergyManager.cooldownTicksRemaining(player);
		if (cooldown > 0) {
			player.sendOverlayMessage(Component.translatable("message.skysdomains.cooldown", String.format("%.1f", cooldown / 20.0)));
			return;
		}
		TalismanStats stats = TalismanStats.of(offhand);
		if (!EnergyManager.trySpend(player, stats.energyCost())) {
			deny(player, "message.skysdomains.no_energy");
			return;
		}

		ServerLevel level = player.level() instanceof ServerLevel serverLevel ? serverLevel : null;
		if (level == null) return;
		ActiveDomain domain = create(level, player, stats, player.position(), stats.energyCost());
		open(domain);
	}

	private static void deny(ServerPlayer player, String key) {
		player.sendOverlayMessage(Component.translatable(key));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.DENIED, SoundSource.PLAYERS, 0.8F, 1.0F);
	}

	private static ActiveDomain create(ServerLevel level, ServerPlayer owner, TalismanStats stats, Vec3 center, double energySpent) {
		SkysDomainsConfig config = ConfigManager.get();
		DomainType type = stats.domain();
		TalismanTier tier = stats.tier();
		List<DomainPhase> phases = new ArrayList<>();
		int planets = 0;
		boolean sun = false;
		DomainEffect effect;
		if (type == DomainType.STRUCTURAL_COLLAPSE) {
			SkysDomainsConfig.StructuralCollapse cfg = config.domains.structuralCollapse;
			planets = Math.max(1, Math.min(8, cfg.planetsPerTier[Math.min(tier.index(), cfg.planetsPerTier.length - 1)]));
			sun = cfg.sunAtTier4 && tier == TalismanTier.TRANSCENDENT;
			int bombardment = planets * cfg.impactIntervalTicks + cfg.planetFlightTicks + 10;
			if (sun) bombardment += cfg.impactIntervalTicks + cfg.planetFlightTicks + 10;
			phases.add(new DomainPhase("activation", cfg.activationTicks));
			phases.add(new DomainPhase("expand", cfg.expandTicks));
			phases.add(new DomainPhase("solar_system", cfg.solarSystemTicks));
			phases.add(new DomainPhase("collapse", cfg.collapseTicks));
			phases.add(new DomainPhase("bombardment", bombardment));
			phases.add(new DomainPhase("end", cfg.endTicks));
			effect = new StructuralCollapseEffect();
		} else {
			phases.add(new DomainPhase(DomainPhase.OPEN, 30));
			phases.add(new DomainPhase(DomainPhase.ACTIVE, stats.durationTicks()));
			phases.add(new DomainPhase(DomainPhase.CLOSE, 20));
			effect = switch (type) {
				case FROST_VAULT -> new FrostVaultEffect();
				case UMBRAL_SEA -> new UmbralSeaEffect();
				case GLASS_HEAVEN -> new GlassHeavenEffect();
				case EMBER_GARDEN -> new EmberGardenEffect();
				case STASIS -> new StasisEffect();
				case STRUCTURAL_COLLAPSE -> new StructuralCollapseEffect();
			};
		}
		return new ActiveDomain(level, owner, stats, center, energySpent, phases, planets, sun, effect);
	}

	private static void open(ActiveDomain domain) {
		resolveClash(domain);
		DOMAINS.computeIfAbsent(domain.level.dimension(), k -> new ArrayList<>()).add(domain);
		syncToPlayersInRange(domain);
		domain.effect.onOpen(domain, domain.level);
		domain.effect.onPhaseStart(domain, domain.level, domain.phase());
		SkysDomains.debug("Domain {} opened by {} at {} (tier {}, radius {})", domain.type.id(), domain.owner.getName().getString(), domain.center, domain.tier.level(), domain.radius);
	}

	/**
	 * Domain clash: if the new domain overlaps an existing one, the higher tier wins, on equal tier
	 * the one with more energy spent; the loser collapses immediately.
	 */
	private static void resolveClash(ActiveDomain incoming) {
		List<ActiveDomain> list = DOMAINS.get(incoming.level.dimension());
		if (list == null) return;
		for (ActiveDomain other : list) {
			if (other.finished || !other.overlaps(incoming)) continue;
			boolean incomingWins = incoming.tier.level() > other.tier.level()
					|| (incoming.tier.level() == other.tier.level() && incoming.energySpent > other.energySpent);
			ActiveDomain loser = incomingWins ? other : incoming;
			ActiveDomain winner = incomingWins ? incoming : other;
			loser.event(DomainEventPayload.CLASH, winner.center, winner.tier.level(), 0);
			winner.event(DomainEventPayload.CLASH, loser.center, loser.tier.level(), 1);
			loser.level.playSound(null, loser.center.x, loser.center.y, loser.center.z, ModSounds.CLASH, SoundSource.PLAYERS, 2.0F, 1.0F);
			loser.collapse();
			loser.owner.sendOverlayMessage(Component.translatable("message.skysdomains.clash_lost"));
			winner.owner.sendOverlayMessage(Component.translatable("message.skysdomains.clash_won"));
			if (!incomingWins) return; // the new domain is already collapsing, no need to check others
		}
	}

	// --- ticking ------------------------------------------------------------------------------

	private static void tickLevel(ServerLevel level) {
		List<ActiveDomain> list = DOMAINS.get(level.dimension());
		if (list == null || list.isEmpty()) return;
		Iterator<ActiveDomain> iterator = list.iterator();
		while (iterator.hasNext()) {
			ActiveDomain domain = iterator.next();
			if (!domain.owner.isAlive() || domain.owner.isRemoved() || domain.owner.level() != level) {
				domain.collapse();
			}
			domainTick(domain);
			if (domain.finished) {
				close(domain);
				iterator.remove();
			}
		}
	}

	private static void domainTick(ActiveDomain domain) {
		boolean newPhase = domain.advance();
		if (domain.finished) return;
		if (newPhase) {
			domain.broadcast(new DomainPhasePayload(domain.id, domain.phaseIndex));
			domain.effect.onPhaseStart(domain, domain.level, domain.phase());
		}
		damagingDomain = domain;
		try {
			domain.effect.tick(domain, domain.level);
		} finally {
			damagingDomain = null;
		}
		if (domain.age % 20 == 0) {
			syncToPlayersInRange(domain);
		}
	}

	private static void close(ActiveDomain domain) {
		damagingDomain = domain;
		try {
			domain.effect.onClose(domain, domain.level, domain.collapsed);
		} finally {
			damagingDomain = null;
		}
		FreezeManager.unfreeze(domain.level, new ArrayList<>(domain.frozenIds), domain.center, domain.syncRange());
		domain.frozenIds.clear();
		domain.broadcast(new DomainClosePayload(domain.id, domain.collapsed));
		EnergyManager.startCooldown(domain.owner, domain.stats.cooldownTicks());
		SkysDomains.debug("Domain {} closed (collapsed={})", domain.type.id(), domain.collapsed);
	}

	/** Players that moved into range after the domain opened get the open packet with the current phase. */
	private static void syncToPlayersInRange(ActiveDomain domain) {
		for (ServerPlayer player : PlayerLookup.around(domain.level, domain.center, domain.syncRange())) {
			if (domain.informedPlayers.add(player.getUUID())) {
				ModNetworking.send(player, domain.openPayload());
			}
		}
	}

	// --- queries ------------------------------------------------------------------------------

	public static ActiveDomain ownedBy(ServerPlayer player) {
		for (List<ActiveDomain> list : DOMAINS.values()) {
			for (ActiveDomain domain : list) {
				if (!domain.finished && domain.owner == player) return domain;
			}
		}
		return null;
	}

	public static int ownedDomainRemainingTicks(ServerPlayer player) {
		ActiveDomain domain = ownedBy(player);
		return domain == null ? 0 : domain.remainingTicks();
	}

	public static List<ActiveDomain> domainsIn(ServerLevel level) {
		List<ActiveDomain> list = DOMAINS.get(level.dimension());
		return list == null ? List.of() : list;
	}

	public static void collapseOwnedBy(UUID owner, Level level) {
		for (List<ActiveDomain> list : DOMAINS.values()) {
			for (ActiveDomain domain : list) {
				if (domain.owner.getUUID().equals(owner)) domain.collapse();
			}
		}
	}

	// --- damage rules -------------------------------------------------------------------------

	/**
	 * Owner immunity against the own domain and suppression of vanilla explosion damage while a
	 * block-damage explosion of Structural Collapse runs (the domain applies its own, guaranteed damage).
	 */
	private static boolean allowDamage(LivingEntity entity, net.minecraft.world.damagesource.DamageSource source, float amount) {
		if (suppressExplosionDamage) return false;
		ActiveDomain domain = damagingDomain;
		if (domain != null && entity == domain.owner) return false;
		return true;
	}

	/** Runs {@code action} with vanilla entity damage suppressed (used for block-damage explosions). */
	public static void withExplosionDamageSuppressed(Runnable action) {
		boolean previous = suppressExplosionDamage;
		suppressExplosionDamage = true;
		try {
			action.run();
		} finally {
			suppressExplosionDamage = previous;
		}
	}
}
