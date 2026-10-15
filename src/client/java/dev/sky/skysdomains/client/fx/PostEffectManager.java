package dev.sky.skysdomains.client.fx;

import dev.sky.skysdomains.SkysDomains;
import dev.sky.skysdomains.config.ConfigManager;
import net.minecraft.resources.Identifier;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Post-processing effects via the vanilla data-driven post-effect pipeline (requested each frame
 * through {@code GameRendererPostEffectMixin})
 * ({@code assets/skysdomains/post_effect/<name>.json} + GLSL under {@code shaders/post}).
 *
 * <p>Available names: {@code invert}, {@code desaturate}, {@code chromatic}, {@code radial_blur},
 * {@code heat}. Only one post effect can be active at a time (last request wins); timed effects
 * expire on their own, untimed ones end with their domain.
 *
 * <p>Falls back to overlay-only behaviour (a soft flash for {@code invert}) when the accessibility
 * option "reduced flashes" is on.
 */
public final class PostEffectManager {
	private static final PostEffectManager INSTANCE = new PostEffectManager();

	private record Active(String name, int remaining) {
	}

	private final Map<UUID, Active> active = new LinkedHashMap<>();
	private String applied;

	private PostEffectManager() {
	}

	public static PostEffectManager get() {
		return INSTANCE;
	}

	public void invert(int ticks) {
		if (ConfigManager.get().accessibility.reducedFlashes) {
			OverlayManager.get().flash(0xFFFFFF, 1, Math.max(4, ticks * 2));
			return;
		}
		apply(new UUID(0L, 0L), "invert", Math.max(1, ticks));
	}

	/** {@code ticks} 0 = until {@link #clearDomain(UUID)}. */
	public void apply(UUID domain, String name, int ticks) {
		active.remove(domain);
		active.put(domain, new Active(name, ticks));
		refresh();
	}

	public void clearDomain(UUID domain) {
		active.remove(domain);
		refresh();
	}

	public void clearAll() {
		active.clear();
		refresh();
	}

	public void tick() {
		boolean changed = false;
		for (Map.Entry<UUID, Active> entry : active.entrySet().toArray(new Map.Entry[0])) {
			Active value = entry.getValue();
			if (value.remaining() > 0) {
				if (value.remaining() - 1 <= 0) {
					active.remove(entry.getKey());
					changed = true;
				} else {
					active.put(entry.getKey(), new Active(value.name(), value.remaining() - 1));
				}
			}
		}
		if (changed) refresh();
	}

	private void refresh() {
		String wanted = null;
		for (Active value : active.values()) wanted = value.name();
		applied = wanted;
	}

	/** Called every frame by the GameRenderer mixin: appends the active domain post effect. */
	public void collectRequested(List<Identifier> requested) {
		if (applied != null) requested.add(SkysDomains.id(applied));
	}
}
