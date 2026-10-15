package dev.sky.skysdomains.mixin;

import dev.sky.skysdomains.common.ModComponents;
import dev.sky.skysdomains.common.ModItems;
import dev.sky.skysdomains.common.TalismanItem;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.ItemCombinerMenu;
import net.minecraft.world.inventory.ItemCombinerMenuSlotDefinition;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Anvil: Talisman + Refinement -> talisman with one more refinement (max {@link TalismanItem#MAX_REFINEMENTS}).
 * Vanilla's {@code onTake} consumes {@code repairItemCountCost} items from the second slot.
 */
@Mixin(AnvilMenu.class)
abstract class AnvilMenuRefinementMixin extends ItemCombinerMenu {
	@Shadow
	@Final
	private DataSlot cost;
	@Shadow
	private int repairItemCountCost;

	AnvilMenuRefinementMixin(@Nullable MenuType<?> type, int syncId, Inventory playerInventory, ContainerLevelAccess context, ItemCombinerMenuSlotDefinition slotDefinition) {
		super(type, syncId, playerInventory, context, slotDefinition);
	}

	@Inject(method = "createResult", at = @At("RETURN"))
	private void skysdomains$applyRefinement(CallbackInfo ci) {
		ItemStack base = this.inputSlots.getItem(0);
		ItemStack addition = this.inputSlots.getItem(1);
		if (!(base.getItem() instanceof TalismanItem) || !addition.is(ModItems.REFINEMENT)) {
			return;
		}
		int current = TalismanItem.refinementsOf(base);
		if (current >= TalismanItem.MAX_REFINEMENTS) {
			this.resultSlots.setItem(0, ItemStack.EMPTY);
			this.cost.set(0);
			return;
		}
		ItemStack result = base.copy();
		result.set(ModComponents.REFINEMENTS, current + 1);
		this.resultSlots.setItem(0, result);
		this.cost.set(5 + current * 4);
		this.repairItemCountCost = 1;
	}
}
