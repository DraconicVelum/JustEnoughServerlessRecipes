package com.draconicvelum.justenoughserverlessrecipes.transfer;

import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IStackHelper;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import mezz.jei.api.recipe.transfer.IRecipeTransferInfo;
import mezz.jei.api.recipe.types.IRecipeType;
import mezz.jei.common.Internal;
import mezz.jei.common.transfer.RecipeTransferOperationsResult;
import mezz.jei.common.transfer.RecipeTransferUtil;
import mezz.jei.library.transfer.BasicRecipeTransferHandler;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class ServerlessRecipeTransferHandler<C extends AbstractContainerMenu, R> implements IRecipeTransferHandler<C, R> {
    private final IStackHelper stackHelper;
    private final IRecipeTransferHandlerHelper handlerHelper;
    private final IRecipeTransferInfo<C, R> transferInfo;
    private final IRecipeTransferHandler<C, R> jeiHandler;

    public ServerlessRecipeTransferHandler(
            IRecipeTransferInfo<C, R> transferInfo,
            IRecipeTransferHandlerHelper handlerHelper,
            IStackHelper stackHelper
    ) {
        this.stackHelper = stackHelper;
        this.handlerHelper = handlerHelper;
        this.transferInfo = transferInfo;
        this.jeiHandler = handlerHelper.createUnregisteredRecipeTransferHandler(transferInfo);
    }

    @Override
    public Class<? extends C> getContainerClass() {
        return transferInfo.getContainerClass();
    }

    @Override
    public Optional<MenuType<C>> getMenuType() {
        return transferInfo.getMenuType();
    }

    @Override
    public IRecipeType<R> getRecipeType() {
        return transferInfo.getRecipeType();
    }

    @Nullable
    @Override
    public IRecipeTransferError transferRecipe(C container, R recipe, IRecipeSlotsView recipeSlotsView, Player player, boolean maxTransfer, boolean doTransfer) {
        if (Internal.getServerConnection().isJeiOnServer()) {
            return jeiHandler.transferRecipe(container, recipe, recipeSlotsView, player, maxTransfer, doTransfer);
        }

        if (!transferInfo.canHandle(container, recipe)) {
            IRecipeTransferError handlingError = transferInfo.getHandlingError(container, recipe);
            if (handlingError != null) {
                return handlingError;
            }
            return handlerHelper.createInternalError();
        }

        if (!container.getCarried().isEmpty()) {
            return handlerHelper.createUserErrorWithTooltip(Component.literal("Clear the carried item before transferring a recipe"));
        }

        List<Slot> craftingSlots = Collections.unmodifiableList(transferInfo.getRecipeSlots(container, recipe));
        List<Slot> inventorySlots = Collections.unmodifiableList(transferInfo.getInventorySlots(container, recipe));
        if (!BasicRecipeTransferHandler.validateTransferInfo(transferInfo, container, craftingSlots, inventorySlots)) {
            return handlerHelper.createInternalError();
        }

        List<IRecipeSlotView> inputItemSlotViews = recipeSlotsView.getSlotViews(RecipeIngredientRole.INPUT);
        if (!BasicRecipeTransferHandler.validateRecipeView(transferInfo, container, craftingSlots, inputItemSlotViews)) {
            return handlerHelper.createInternalError();
        }

        BasicRecipeTransferHandler.InventoryState inventoryState = BasicRecipeTransferHandler.getInventoryState(
                craftingSlots,
                inventorySlots,
                player,
                container,
                transferInfo
        );
        if (inventoryState == null) {
            return handlerHelper.createInternalError();
        }

        int inputCount = inputItemSlotViews.size();
        if (!inventoryState.hasRoom(inputCount)) {
            return handlerHelper.createUserErrorWithTooltip(Component.translatable("jei.tooltip.error.recipe.transfer.inventory.full"));
        }

        RecipeTransferOperationsResult transferOperations = RecipeTransferUtil.getRecipeTransferOperations(
                stackHelper,
                inventoryState.availableItemStacks(),
                inputItemSlotViews,
                craftingSlots
        );
        if (!transferOperations.missingItems.isEmpty()) {
            return handlerHelper.createUserErrorForMissingSlots(
                    Component.translatable("jei.tooltip.error.recipe.transfer.missing"),
                    transferOperations.missingItems
            );
        }

        if (!RecipeTransferUtil.validateSlots(player, transferOperations.results, craftingSlots, inventorySlots)) {
            return handlerHelper.createInternalError();
        }

        if (doTransfer) {
            boolean requireCompleteSets = transferInfo.requireCompleteSets(container, recipe);
            boolean transferred = VanillaMenuTransferExecutor.execute(
                    container,
                    player,
                    stackHelper,
                    recipeSlotsView,
                    craftingSlots,
                    inventorySlots,
                    maxTransfer,
                    requireCompleteSets
            );
            if (!transferred) {
                return handlerHelper.createInternalError();
            }
        }

        return null;
    }
}
