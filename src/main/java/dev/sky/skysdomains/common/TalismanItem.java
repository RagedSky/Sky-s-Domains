package dev.sky.skysdomains.common;

import dev.sky.skysdomains.DomainType;
import dev.sky.skysdomains.TalismanTier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * A talisman of a fixed tier. The inscribed domain and the refinement count are stored as
 * data components (see {@link ModComponents}); the tier is the item itself.
 */
public class TalismanItem extends Item {
	public static final int MAX_REFINEMENTS = 3;

	private final TalismanTier tier;

	public TalismanItem(TalismanTier tier, Properties properties) {
		super(properties);
		this.tier = tier;
	}

	public TalismanTier tier() {
		return tier;
	}

	@Nullable
	public static DomainType domainOf(ItemStack stack) {
		if (!(stack.getItem() instanceof TalismanItem)) return null;
		return stack.get(ModComponents.DOMAIN);
	}

	public static int refinementsOf(ItemStack stack) {
		if (!(stack.getItem() instanceof TalismanItem)) return 0;
		Integer value = stack.get(ModComponents.REFINEMENTS);
		return value == null ? 0 : Math.min(MAX_REFINEMENTS, Math.max(0, value));
	}

	@Nullable
	public static TalismanTier tierOf(ItemStack stack) {
		return stack.getItem() instanceof TalismanItem talisman ? talisman.tier : null;
	}

	/** A talisman is "active" when a sigil has been inscribed. */
	public static boolean isActive(ItemStack stack) {
		return domainOf(stack) != null;
	}

	/** Enchantment-like glow once a sigil is inscribed. */
	@Override
	public boolean isFoil(ItemStack stack) {
		return isActive(stack) || super.isFoil(stack);
	}
}
