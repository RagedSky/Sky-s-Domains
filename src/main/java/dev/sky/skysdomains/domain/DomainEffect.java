package dev.sky.skysdomains.domain;

import net.minecraft.server.level.ServerLevel;

/** Server-side behaviour of one domain type. One instance per active domain. */
public interface DomainEffect {
	/** Called once when the domain is created (phase 0 starts). */
	default void onOpen(ActiveDomain domain, ServerLevel level) {
	}

	/** Called whenever a new phase begins (including the first). */
	default void onPhaseStart(ActiveDomain domain, ServerLevel level, DomainPhase phase) {
	}

	/** Called every server tick while the domain exists. */
	void tick(ActiveDomain domain, ServerLevel level);

	/** Called once when the domain ends, normally or by a clash collapse. */
	default void onClose(ActiveDomain domain, ServerLevel level, boolean collapsed) {
	}
}
