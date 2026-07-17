package com.draconicvelum.justenoughserverlessrecipes.transfer;

import it.unimi.dsi.fastutil.ints.IntArraySet;
import it.unimi.dsi.fastutil.ints.IntSet;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IStackHelper;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import mezz.jei.api.recipe.types.IRecipeType;
import mezz.jei.common.Internal;
import mezz.jei.library.transfer.PlayerRecipeTransferHandler;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;

public class ServerlessPlayerRecipeTransferHandler implements IRecipeTransferHandler<InventoryMenu, RecipeHolder<CraftingRecipe>> {
    private static final IntSet PLAYER_INV_INDEXES = IntArraySet.of(0, 1, 3, 4);

    private final IRecipeTransferHandlerHelper handlerHelper;
    private final IRecipeTransferHandler<InventoryMenu, RecipeHolder<CraftingRecipe>> jeiHandler;
    private final ServerlessRecipeTransferHandler<InventoryMenu, RecipeHolder<CraftingRecipe>> fallbackHandler;

    public ServerlessPlayerRecipeTransferHandler(IRecipeTransferHandlerHelper handlerHelper, IStackHelper stackHelper) {
        this.handlerHelper = handlerHelper;
        this.jeiHandler = new PlayerRecipeTransferHandler(handlerHelper);
        var transferInfo = handlerHelper.createBasicRecipeTransferInfo(InventoryMenu.class, null, RecipeTypes.CRAFTING, 1, 4, 9, 36);
        this.fallbackHandler = new ServerlessRecipeTransferHandler<>(transferInfo, handlerHelper, stackHelper);
    }

    @Override
    public Class<? extends InventoryMenu> getContainerClass() {
        return InventoryMenu.class;
    }

    @Override
    public Optional<MenuType<InventoryMenu>> getMenuType() {
        return Optional.empty();
    }

    @Override
    public IRecipeType<RecipeHolder<CraftingRecipe>> getRecipeType() {
        return RecipeTypes.CRAFTING;
    }

    @Nullable
    @Override
    public IRecipeTransferError transferRecipe(InventoryMenu container, RecipeHolder<CraftingRecipe> recipe, IRecipeSlotsView recipeSlotsView, Player player, boolean maxTransfer, boolean doTransfer) {
        if (Internal.getServerConnection().isJeiOnServer()) {
            return jeiHandler.transferRecipe(container, recipe, recipeSlotsView, player, maxTransfer, doTransfer);
        }

        List<IRecipeSlotView> slotViews = recipeSlotsView.getSlotViews(RecipeIngredientRole.INPUT);
        if (!validateIngredientsOutsidePlayerGridAreEmpty(slotViews)) {
            return handlerHelper.createUserErrorWithTooltip(
                    Component.translatable("jei.tooltip.error.recipe.transfer.too.large.player.inventory")
            );
        }

        List<IRecipeSlotView> filteredSlotViews = filterSlots(slotViews);
        IRecipeSlotsView filteredRecipeSlots = handlerHelper.createRecipeSlotsView(filteredSlotViews);
        return fallbackHandler.transferRecipe(container, recipe, filteredRecipeSlots, player, maxTransfer, doTransfer);
    }

    private static boolean validateIngredientsOutsidePlayerGridAreEmpty(List<IRecipeSlotView> slotViews) {
        for (int i = 0; i < slotViews.size(); i++) {
            if (!PLAYER_INV_INDEXES.contains(i) && !slotViews.get(i).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static List<IRecipeSlotView> filterSlots(List<IRecipeSlotView> slotViews) {
        return PLAYER_INV_INDEXES.intStream()
                .mapToObj(slotViews::get)
                .toList();
    }
}
