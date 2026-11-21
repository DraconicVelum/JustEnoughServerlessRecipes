package com.draconicvelum.justenoughserverlessrecipes.fallback;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import mezz.jei.api.recipe.types.IRecipeType;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import java.util.ArrayList;
import java.util.List;

final class BreedingFallback {
    @SuppressWarnings("unchecked")
    static void apply(IJeiRuntime runtime, LocalDataLoader.Data data) throws ReflectiveOperationException {
        Class<?> mod = Class.forName("com.christofmeg.justenoughbreeding.JustEnoughBreeding");
        var isLoaded = mod.getMethod("isModLoaded", String.class);
        var ops = RegistryOps.create(JsonOps.INSTANCE, data.registries());
        List<Recipe<?>> recipes = new ArrayList<>();
        for (var entry : data.resources().listResources("recipe", id -> id.getPath().endsWith(".json")).entrySet()) {
            try (var reader = entry.getValue().openAsReader()) {
                var json = JsonParser.parseReader(reader).getAsJsonObject();
                if (!json.has("type") || !json.get("type").getAsString().startsWith("justenoughbreeding:")) continue;
                if (!json.has("mod") || !Boolean.TRUE.equals(isLoaded.invoke(null, json.get("mod").getAsString()))) continue;
                var recipe = Recipe.DIRECT_CODEC.parse(ops, json).getOrThrow();
                var inputs = (Ingredient) recipe.getClass().getMethod("inputs").invoke(recipe);
                String entityAccessor = recipe.getClass().getSimpleName().equals("TransformationRecipe") ? "outputEntityType" : "entityType";
                if (!inputs.isEmpty() && recipe.getClass().getMethod(entityAccessor).invoke(recipe) != null) {
                    recipes.add((Recipe<?>) new LocalDataRebinder(data).rebind(recipe));
                }
            } catch (java.io.IOException | RuntimeException e) {
                AddonFallbacks.LOG.debug("Skipping unavailable local breeding recipe {}", entry.getKey(), e);
            }
        }
        for (String category : List.of("AllayDuplication", "Breeding", "Taming", "Temper", "Transformation", "Trusting")) {
            var categoryClass = Class.forName("com.christofmeg.justenoughbreeding.jei." + category + "Category");
            var type = (IRecipeType<Object>) categoryClass.getField("TYPE").get(null);
            if (runtime.getRecipeManager().createRecipeLookup(type).includeHidden().get().findAny().isPresent()) continue;
            var matches = recipes.stream().filter(type.getRecipeClass()::isInstance).map(recipe -> (Object) recipe).toList();
            if (!matches.isEmpty()) runtime.getRecipeManager().addRecipes(type, matches);
            AddonFallbacks.LOG.info("Added {} locally loaded {} recipes", matches.size(), category);
        }
    }
}
