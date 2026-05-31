package com.draconicvelum.justenoughserverlessrecipes.mixin;

import net.minecraft.core.Holder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ItemStack.class)
public abstract class ItemStackCompatibilityMixin {
    public boolean supportsEnchantment(Holder<Enchantment> enchantment) {
        return enchantment.value().canEnchant((ItemStack) (Object) this);
    }
}
