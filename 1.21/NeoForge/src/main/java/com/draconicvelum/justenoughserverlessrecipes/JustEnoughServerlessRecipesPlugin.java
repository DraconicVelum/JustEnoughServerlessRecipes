package com.draconicvelum.justenoughserverlessrecipes;

import com.draconicvelum.justenoughserverlessrecipes.recipes.DatapackRecipeMapBuilder;
import com.draconicvelum.justenoughserverlessrecipes.transfer.ServerlessPlayerRecipeTransferHandler;
import com.draconicvelum.justenoughserverlessrecipes.transfer.ServerlessRecipeTransferHandler;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;
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
import net.minecraft.world.item.crafting.BlastingRecipe;
import net.minecraft.world.item.crafting.CampfireCookingRecipe;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.item.crafting.SmokingRecipe;
import net.minecraft.world.item.crafting.StonecutterRecipe;

import java.util.ArrayList;
import java.util.List;

@JeiPlugin
public class JustEnoughServerlessRecipesPlugin implements IModPlugin {
    public static IJeiRuntime runtime;

    @Override
    public ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath("justenoughserverlessrecipes", "plugin");
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        registerFallbackRecipes(registration, DatapackRecipeMapBuilder.build());
    }

    @Override
    public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration) {
        var transferHelper = registration.getTransferHelper();
        var stackHelper = registration.getJeiHelpers().getStackHelper();

        registerBasicTransferHandler(registration, transferHelper, stackHelper, CraftingMenu.class, MenuType.CRAFTING, RecipeTypes.CRAFTING, 1, 9, 10, 36);
        registerBasicTransferHandler(registration, transferHelper, stackHelper, CrafterMenu.class, MenuType.CRAFTER_3x3, RecipeTypes.CRAFTING, 0, 9, 9, 36);
        registerBasicTransferHandler(registration, transferHelper, stackHelper, FurnaceMenu.class, MenuType.FURNACE, RecipeTypes.SMELTING, 0, 1, 3, 36);
        registerBasicTransferHandler(registration, transferHelper, stackHelper, SmokerMenu.class, MenuType.SMOKER, RecipeTypes.SMOKING, 0, 1, 3, 36);
        registerBasicTransferHandler(registration, transferHelper, stackHelper, BlastFurnaceMenu.class, MenuType.BLAST_FURNACE, RecipeTypes.BLASTING, 0, 1, 3, 36);
        registerBasicTransferHandler(registration, transferHelper, stackHelper, BrewingStandMenu.class, MenuType.BREWING_STAND, RecipeTypes.BREWING, 0, 4, 5, 36);
        registerBasicTransferHandler(registration, transferHelper, stackHelper, AnvilMenu.class, MenuType.ANVIL, RecipeTypes.ANVIL, 0, 2, 3, 36);
        registerBasicTransferHandler(registration, transferHelper, stackHelper, SmithingMenu.class, MenuType.SMITHING, RecipeTypes.SMITHING, 0, 3, 3, 36);

        var playerHandler = new ServerlessPlayerRecipeTransferHandler(transferHelper, stackHelper);
        registration.addRecipeTransferHandler(playerHandler, RecipeTypes.CRAFTING);
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        runtime = jeiRuntime;
    }

    @Override
    public void onRuntimeUnavailable() {
        runtime = null;
    }

    public static boolean tryInjectDatapackRecipes(String source) {
        return false;
    }

    private static void registerFallbackRecipes(IRecipeRegistration registration, List<RecipeHolder<?>> recipes) {
        if (recipes.isEmpty()) {
            return;
        }

        List<RecipeHolder<CraftingRecipe>> craftingRecipes = new ArrayList<>();
        List<RecipeHolder<StonecutterRecipe>> stonecuttingRecipes = new ArrayList<>();
        List<RecipeHolder<SmeltingRecipe>> smeltingRecipes = new ArrayList<>();
        List<RecipeHolder<SmokingRecipe>> smokingRecipes = new ArrayList<>();
        List<RecipeHolder<BlastingRecipe>> blastingRecipes = new ArrayList<>();
        List<RecipeHolder<CampfireCookingRecipe>> campfireRecipes = new ArrayList<>();
        List<RecipeHolder<SmithingRecipe>> smithingRecipes = new ArrayList<>();

        for (RecipeHolder<?> holder : recipes) {
            if (holder.value() instanceof CraftingRecipe) {
                craftingRecipes.add(castRecipeHolder(holder));
            } else if (holder.value() instanceof StonecutterRecipe) {
                stonecuttingRecipes.add(castRecipeHolder(holder));
            } else if (holder.value() instanceof SmeltingRecipe) {
                smeltingRecipes.add(castRecipeHolder(holder));
            } else if (holder.value() instanceof SmokingRecipe) {
                smokingRecipes.add(castRecipeHolder(holder));
            } else if (holder.value() instanceof BlastingRecipe) {
                blastingRecipes.add(castRecipeHolder(holder));
            } else if (holder.value() instanceof CampfireCookingRecipe) {
                campfireRecipes.add(castRecipeHolder(holder));
            } else if (holder.value() instanceof SmithingRecipe) {
                smithingRecipes.add(castRecipeHolder(holder));
            }
        }

        registration.addRecipes(RecipeTypes.CRAFTING, craftingRecipes);
        registration.addRecipes(RecipeTypes.STONECUTTING, stonecuttingRecipes);
        registration.addRecipes(RecipeTypes.SMELTING, smeltingRecipes);
        registration.addRecipes(RecipeTypes.SMOKING, smokingRecipes);
        registration.addRecipes(RecipeTypes.BLASTING, blastingRecipes);
        registration.addRecipes(RecipeTypes.CAMPFIRE_COOKING, campfireRecipes);
        registration.addRecipes(RecipeTypes.SMITHING, smithingRecipes);
        JustEnoughServerlessRecipesLog.LOGGER.info(
                "Registered fallback recipes directly with JEI: crafting={}, stonecutting={}, smelting={}, smoking={}, blasting={}, campfire={}, smithing={}",
                craftingRecipes.size(), stonecuttingRecipes.size(), smeltingRecipes.size(), smokingRecipes.size(),
                blastingRecipes.size(), campfireRecipes.size(), smithingRecipes.size()
        );
    }

    @SuppressWarnings("unchecked")
    private static <T extends Recipe<?>> RecipeHolder<T> castRecipeHolder(RecipeHolder<?> holder) {
        return (RecipeHolder<T>) holder;
    }

    private static <C extends net.minecraft.world.inventory.AbstractContainerMenu, R> void registerBasicTransferHandler(
            IRecipeTransferRegistration registration,
            mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper transferHelper,
            mezz.jei.api.helpers.IStackHelper stackHelper,
            Class<? extends C> containerClass,
            MenuType<C> menuType,
            RecipeType<R> recipeType,
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
