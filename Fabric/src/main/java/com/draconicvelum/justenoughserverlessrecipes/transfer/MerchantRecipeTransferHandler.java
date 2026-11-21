package com.draconicvelum.justenoughserverlessrecipes.transfer;

import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import mezz.jei.api.recipe.transfer.IUniversalRecipeTransferHandler;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundSelectTradePacket;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import java.util.List;
import java.util.Optional;
final class MerchantRecipeTransferHandler implements IUniversalRecipeTransferHandler<MerchantMenu> {
    private final IRecipeTransferHandlerHelper helper;

    MerchantRecipeTransferHandler(IRecipeTransferHandlerHelper helper) {
        this.helper = helper;
    }

    @Override public Class<MerchantMenu> getContainerClass() { return MerchantMenu.class; }
    @Override public Optional<MenuType<MerchantMenu>> getMenuType() { return Optional.of(MenuType.MERCHANT); }
    @Override
    public IRecipeTransferError transferRecipe(MerchantMenu menu, Object recipe, IRecipeSlotsView slots,
                                               Player player, boolean maximum, boolean doTransfer) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gameMode == null || minecraft.getConnection() == null || player.containerMenu != menu) {
            return helper.createInternalError();
        }
        boolean matching = false;
        boolean inStock = false;
        for (int index = 0; index < menu.getOffers().size(); index++) {
            MerchantOffer offer = menu.getOffers().get(index);
            if (!matches(slots, offer)) continue;
            if (!menu.getCarried().isEmpty()) return error("clear_cursor");
            matching = true;
            if (offer.isOutOfStock()) continue;
            inStock = true;
            List<ItemStack> original = snapshot(menu);
            MerchantTransferPlan.Plan plan = MerchantTransferPlan.create(original, offer, maximum);
            if (plan == null) continue;
            if (!doTransfer) return null;

            // Same local prediction and packet as MerchantScreen.postButtonClick.
            menu.setSelectionHint(index);
            menu.tryMoveItems(index);
            minecraft.getConnection().send(new ServerboundSelectTradePacket(index));
            if (!sameSlots(snapshot(menu), plan.selectedSlots())) return error("changed");
            for (var click : plan.clicks()) {
                minecraft.gameMode.handleContainerInput(menu.containerId, click.slot(), click.button(), ContainerInput.PICKUP, player);
            }
            if (!menu.getCarried().isEmpty()) return error("changed");
            return null;
        }
        return !matching ? helper.createInternalError() : error(!inStock ? "out_of_stock" : "ingredients_or_space");
    }

    static boolean matches(IRecipeSlotsView slots, MerchantOffer offer) {
        boolean outputMatches = slots.getSlotViews(RecipeIngredientRole.OUTPUT).stream()
                .flatMap(slot -> slot.getItemStacks())
                .anyMatch(stack -> ItemStack.isSameItemSameComponents(stack, offer.getResult())
                        && stack.getCount() == offer.getResult().getCount());
        if (!outputMatches) return false;
        var inputs = slots.getSlotViews(RecipeIngredientRole.INPUT);
        for (int a = 0; a < inputs.size(); a++) {
            if (inputs.get(a).getItemStacks().noneMatch(offer.getItemCostA()::test)) continue;
            if (offer.getItemCostB().isEmpty()) return true;
            for (int b = 0; b < inputs.size(); b++) {
                if (a != b && inputs.get(b).getItemStacks().anyMatch(offer.getItemCostB().orElseThrow()::test)) return true;
            }
        }
        return false;
    }

    private static List<ItemStack> snapshot(MerchantMenu menu) {
        return menu.slots.stream().map(slot -> slot.getItem().copy()).toList();
    }

    private static boolean sameSlots(List<ItemStack> a, List<ItemStack> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            if (i != 2 && !ItemStack.matches(a.get(i), b.get(i))) return false;
        }
        return true;
    }

    private IRecipeTransferError error(String key) {
        return helper.createUserErrorWithTooltip(Component.translatable("jesr.trade_transfer." + key));
    }
}
