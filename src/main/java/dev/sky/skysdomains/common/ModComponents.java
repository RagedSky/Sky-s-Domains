package dev.sky.skysdomains.common;

import dev.sky.skysdomains.DomainType;
import dev.sky.skysdomains.SkysDomains;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.util.ExtraCodecs;

/**
 * Item data components stored on talismans.
 *
 * <p>They are intentionally two separate components: the transmute recipes that inscribe a sigil
 * only overwrite {@link #DOMAIN}, so {@link #REFINEMENTS} survives a sigil swap, and smithing
 * upgrades copy the whole component patch of the base item, so both survive a tier upgrade.
 */
public final class ModComponents {
	public static final DataComponentType<DomainType> DOMAIN = DataComponentType.<DomainType>builder()
			.persistent(DomainType.CODEC)
			.networkSynchronized(DomainType.STREAM_CODEC)
			.build();

	public static final DataComponentType<Integer> REFINEMENTS = DataComponentType.<Integer>builder()
			.persistent(ExtraCodecs.NON_NEGATIVE_INT)
			.networkSynchronized(ByteBufCodecs.VAR_INT)
			.build();

	private ModComponents() {
	}

	public static void register() {
		Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, SkysDomains.id("domain"), DOMAIN);
		Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, SkysDomains.id("refinements"), REFINEMENTS);
	}
}
