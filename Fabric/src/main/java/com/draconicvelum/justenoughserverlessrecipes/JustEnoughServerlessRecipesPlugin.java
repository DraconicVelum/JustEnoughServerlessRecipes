package com.draconicvelum.justenoughserverlessrecipes;

import com.draconicvelum.justenoughserverlessrecipes.transfer.ServerlessPlayerRecipeTransferHandler;
import com.draconicvelum.justenoughserverlessrecipes.transfer.ServerlessRecipeTransferHandler;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.BlastFurnaceMenu;
import net.minecraft.world.inventory.BrewingStandMenu;
import net.minecraft.world.inventory.CrafterMenu;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.FurnaceMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.inventory.SmokerMenu;

@JeiPlugin
public class JustEnoughServerlessRecipesPlugin implements IModPlugin {
    @Override
    public Identifier getPluginUid() {
        return Identifier.fromNamespaceAndPath("justenoughserverlessrecipes", "plugin");
    }

    @Override
    public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration) {
        var transferHelper = registration.getTransferHelper();
        var stackHelper = registration.getJeiHelpers().getStackHelper();

        registerBasicTransferHandler(registration, transferHelper, stackHelper, CraftingMenu.class, MenuType.CRAFTING, RecipeTypes.CRAFTING, 1, 9, 10, 36);
        registerBasicTransferHandler(registration, transferHelper, stackHelper, CrafterMenu.class, MenuType.CRAFTER_3x3, RecipeTypes.CRAFTING, 0, 9, 9, 36);
        registerBasicTransferHandler(registration, transferHelper, stackHelper, FurnaceMenu.class, MenuType.FURNACE, RecipeTypes.SMELTING, 0, 1, 3, 36);
        registerBasicTransferHandler(registration, transferHelper, stackHelper, FurnaceMenu.class, MenuType.FURNACE, RecipeTypes.SMELTING_FUEL, 1, 1, 3, 36);
        registerBasicTransferHandler(registration, transferHelper, stackHelper, SmokerMenu.class, MenuType.SMOKER, RecipeTypes.SMOKING, 0, 1, 3, 36);
        registerBasicTransferHandler(registration, transferHelper, stackHelper, SmokerMenu.class, MenuType.SMOKER, RecipeTypes.SMOKING_FUEL, 1, 1, 3, 36);
        registerBasicTransferHandler(registration, transferHelper, stackHelper, BlastFurnaceMenu.class, MenuType.BLAST_FURNACE, RecipeTypes.BLASTING, 0, 1, 3, 36);
        registerBasicTransferHandler(registration, transferHelper, stackHelper, BlastFurnaceMenu.class, MenuType.BLAST_FURNACE, RecipeTypes.BLASTING_FUEL, 1, 1, 3, 36);
        registerBasicTransferHandler(registration, transferHelper, stackHelper, BrewingStandMenu.class, MenuType.BREWING_STAND, RecipeTypes.BREWING, 0, 4, 5, 36);
        registerBasicTransferHandler(registration, transferHelper, stackHelper, AnvilMenu.class, MenuType.ANVIL, RecipeTypes.ANVIL, 0, 2, 3, 36);
        registerBasicTransferHandler(registration, transferHelper, stackHelper, SmithingMenu.class, MenuType.SMITHING, RecipeTypes.SMITHING, 0, 3, 3, 36);

        var playerHandler = new ServerlessPlayerRecipeTransferHandler(transferHelper, stackHelper);
        registration.addRecipeTransferHandler(playerHandler, RecipeTypes.CRAFTING);
    }

    private static <C extends net.minecraft.world.inventory.AbstractContainerMenu, R> void registerBasicTransferHandler(
            IRecipeTransferRegistration registration,
            mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper transferHelper,
            mezz.jei.api.helpers.IStackHelper stackHelper,
            Class<? extends C> containerClass,
            MenuType<C> menuType,
            mezz.jei.api.recipe.types.IRecipeType<R> recipeType,
            int recipeSlotStart,
            int recipeSlotCount,
            int inventorySlotStart,
            int inventorySlotCount
    ) {
        var transferInfo = transferHelper.createBasicRecipeTransferInfo(
                containerClass,
                menuType,
                recipeType,
                recipeSlotStart,
                recipeSlotCount,
                inventorySlotStart,
                inventorySlotCount
        );
        var handler = new ServerlessRecipeTransferHandler<>(transferInfo, transferHelper, stackHelper);
        registration.addRecipeTransferHandler(handler, recipeType);
    }
}
