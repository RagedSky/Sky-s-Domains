package dev.sky.skysdomains.client.render;

/** How a living entity inside a domain is drawn on top of / instead of its normal model. */
public enum DomainLook {
	/** Stasis / Structural Collapse time freeze: glitchy chromatic ghost copies. */
	FROZEN,
	/** Frost Vault: ice shell growing with the entity's freeze ticks. */
	ICE,
	/** Glass Heaven: the entity itself becomes translucent glass. */
	GLASS
}
