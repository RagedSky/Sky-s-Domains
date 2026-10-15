package dev.sky.skysdomains.common;

import dev.sky.skysdomains.DomainType;
import net.minecraft.world.item.Item;

/** A sigil inscribes a domain into a talisman (crafting-table transmute recipe). */
public class SigilItem extends Item {
	private final DomainType domain;

	public SigilItem(DomainType domain, Properties properties) {
		super(properties);
		this.domain = domain;
	}

	public DomainType domain() {
		return domain;
	}
}
