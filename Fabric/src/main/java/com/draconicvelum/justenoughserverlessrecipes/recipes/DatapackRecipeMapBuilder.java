package com.draconicvelum.justenoughserverlessrecipes.recipes;

import com.draconicvelum.justenoughserverlessrecipes.JustEnoughServerlessRecipesLog;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.Recipe.CommonInfo;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeMap;
import net.minecraft.world.item.crafting.SmithingTrimRecipe;
import net.minecraft.world.item.equipment.trim.TrimPattern;
import net.minecraft.world.item.equipment.trim.TrimPatterns;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class DatapackRecipeMapBuilder {
    private static final String BUNDLED_RECIPE_PATH = "/data/justenoughserverlessrecipes/recipe_fallback/vanilla_recipes.json";
    private static final List<String> TRIM_MATERIAL_ITEMS = List.of(
            "minecraft:amethyst_shard",
            "minecraft:copper_ingot",
            "minecraft:diamond",
            "minecraft:emerald",
            "minecraft:gold_ingot",
            "minecraft:iron_ingot",
            "minecraft:lapis_lazuli",
            "minecraft:netherite_ingot",
            "minecraft:quartz",
            "minecraft:redstone",
            "minecraft:resin_brick"
    );
    private static final List<String> TRIMMABLE_ARMOR_ITEMS = List.of(
            "minecraft:leather_boots",
            "minecraft:copper_boots",
            "minecraft:chainmail_boots",
            "minecraft:golden_boots",
            "minecraft:iron_boots",
            "minecraft:diamond_boots",
            "minecraft:netherite_boots",
            "minecraft:leather_leggings",
            "minecraft:copper_leggings",
            "minecraft:chainmail_leggings",
            "minecraft:golden_leggings",
            "minecraft:iron_leggings",
            "minecraft:diamond_leggings",
            "minecraft:netherite_leggings",
            "minecraft:leather_chestplate",
            "minecraft:copper_chestplate",
            "minecraft:chainmail_chestplate",
            "minecraft:golden_chestplate",
            "minecraft:iron_chestplate",
            "minecraft:diamond_chestplate",
            "minecraft:netherite_chestplate",
            "minecraft:leather_helmet",
            "minecraft:copper_helmet",
            "minecraft:chainmail_helmet",
            "minecraft:golden_helmet",
            "minecraft:iron_helmet",
            "minecraft:diamond_helmet",
            "minecraft:netherite_helmet",
            "minecraft:turtle_helmet"
    );

    public static RecipeMap build() {
        RecipeMap integratedServerMap = getIntegratedServerRecipeMap();
        if (integratedServerMap != null) {
            return integratedServerMap;
        }

        RecipeMap bundledMap = getBundledFallbackRecipeMap();
        if (bundledMap != null) {
            return bundledMap;
        }

        JustEnoughServerlessRecipesLog.LOGGER.warn("No bundled fallback recipe dataset found");
        return RecipeMap.EMPTY;
    }

    private static RecipeMap getBundledFallbackRecipeMap() {
        try (InputStream stream = DatapackRecipeMapBuilder.class.getResourceAsStream(BUNDLED_RECIPE_PATH)) {
            if (stream == null) {
                return null;
            }

            JsonObject root = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonArray recipesJson = root.getAsJsonArray("recipes");
            List<RecipeHolder<?>> holders = new ArrayList<>(recipesJson.size());
            List<String> skipped = new ArrayList<>();
            RegistryAccess.Frozen registryAccess = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);

            for (JsonElement recipeElement : recipesJson) {
                if (!recipeElement.isJsonObject()) {
                    continue;
                }

                JsonObject recipeJson = recipeElement.getAsJsonObject();
                RecipeHolder<?> holder = parseBundledRecipe(recipeJson, registryAccess);
                if (holder != null) {
                    holders.add(holder);
                } else if (recipeJson.has("id")) {
                    skipped.add(recipeJson.get("id").getAsString());
                }
            }

            JustEnoughServerlessRecipesLog.LOGGER.info("Loaded {} bundled fallback recipes", holders.size());
            if (!skipped.isEmpty()) {
                JustEnoughServerlessRecipesLog.LOGGER.warn("Skipped {} bundled recipes: {}",
                        skipped.size(),
                        skipped.stream().collect(Collectors.joining(", ")));
            }
            return holders.isEmpty() ? RecipeMap.EMPTY : RecipeMap.create(holders);
        } catch (Exception e) {
            JustEnoughServerlessRecipesLog.LOGGER.error("Failed to load bundled fallback recipe dataset", e);
            return RecipeMap.EMPTY;
        }
    }

    private static RecipeHolder<?> parseBundledRecipe(JsonObject json, RegistryAccess.Frozen registryAccess) {
        Identifier recipeId = null;
        try {
            if (!json.has("id")) {
                return null;
            }

            recipeId = Identifier.parse(json.get("id").getAsString());
            ResourceKey<Recipe<?>> key = ResourceKey.create(Registries.RECIPE, recipeId);

            JsonObject recipeJson = json.deepCopy();
            recipeJson.remove("id");

            Method fromJson = Class.forName("net.minecraft.world.item.crafting.RecipeManager")
                    .getDeclaredMethod("fromJson", ResourceKey.class, JsonObject.class, Class.forName("net.minecraft.core.HolderLookup$Provider"));
            fromJson.setAccessible(true);
            return (RecipeHolder<?>) fromJson.invoke(null, key, recipeJson, registryAccess);
        } catch (Exception ignored) {
            if (recipeId != null && json.has("type") && "minecraft:smithing_trim".equals(json.get("type").getAsString())) {
                return buildSmithingTrimRecipe(recipeId, json, registryAccess);
            }
            return null;
        }
    }

    private static RecipeHolder<?> buildSmithingTrimRecipe(Identifier recipeId, JsonObject json, RegistryAccess.Frozen registryAccess) {
        try {
            ResourceKey<Recipe<?>> key = ResourceKey.create(Registries.RECIPE, recipeId);
            Ingredient template = parseIngredient(json.get("template"));
            Ingredient base = parseIngredient(json.get("base"));
            Ingredient addition = parseIngredient(json.get("addition"));
            if (template == null || template.isEmpty() || base == null || base.isEmpty() || addition == null || addition.isEmpty()) {
                logTrimFailure(recipeId, "ingredient parsing returned empty", null);
                return null;
            }

            if (!json.has("pattern")) {
                logTrimFailure(recipeId, "missing pattern", null);
                return null;
            }
            Identifier patternId = Identifier.parse(json.get("pattern").getAsString());

            ResourceKey<TrimPattern> patternKey = ResourceKey.create(Registries.TRIM_PATTERN, patternId);
            Holder<TrimPattern> pattern = Holder.direct(new TrimPattern(
                    TrimPatterns.defaultAssetId(patternKey),
                    Component.translatable("trim_pattern." + patternId.getNamespace() + "." + patternId.getPath()),
                    false
            ));
            if (pattern == null) {
                logTrimFailure(recipeId, "pattern holder is null", null);
                return null;
            }

            SmithingTrimRecipe recipe = new SmithingTrimRecipe(
                    new CommonInfo(getBoolean(json, "show_notification", true)),
                    template,
                    base,
                    addition,
                    pattern
            );
            return new RecipeHolder<>(key, recipe);
        } catch (Exception e) {
            logTrimFailure(recipeId, "exception while building smithing trim", e);
            return null;
        }
    }

    private static Ingredient parseIngredient(JsonElement ingredientJson) {
        try {
            if (ingredientJson == null) {
                return null;
            }

            if (ingredientJson.isJsonPrimitive()) {
                String value = ingredientJson.getAsString();
                if (value.startsWith("#")) {
                    Identifier tagId = Identifier.parse(value.substring(1));
                    Ingredient hardcoded = resolveKnownItemTag(tagId);
                    if (hardcoded != null) {
                        return hardcoded;
                    }
                    TagKey<Item> tagKey = TagKey.create(Registries.ITEM, tagId);
                    return Ingredient.of(StreamSupport.stream(
                            BuiltInRegistries.ITEM.getTagOrEmpty(tagKey).spliterator(),
                            false
                    ).map(Holder::value));
                }

                Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(value));
                return item == null ? null : Ingredient.of(item);
            }

            JsonObject object = ingredientJson.getAsJsonObject();
            if (object.has("item")) {
                Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(object.get("item").getAsString()));
                return item == null ? null : Ingredient.of(item);
            }

            if (object.has("tag")) {
                Identifier tagId = Identifier.parse(object.get("tag").getAsString());
                Ingredient hardcoded = resolveKnownItemTag(tagId);
                if (hardcoded != null) {
                    return hardcoded;
                }
                TagKey<Item> tagKey = TagKey.create(Registries.ITEM, tagId);
                return Ingredient.of(StreamSupport.stream(
                        BuiltInRegistries.ITEM.getTagOrEmpty(tagKey).spliterator(),
                        false
                ).map(Holder::value));
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static Ingredient resolveKnownItemTag(Identifier tagId) {
        if (tagId.equals(Identifier.parse("minecraft:trim_materials"))) {
            return ingredientFromItemIds(TRIM_MATERIAL_ITEMS);
        }
        if (tagId.equals(Identifier.parse("minecraft:trimmable_armor"))) {
            return ingredientFromItemIds(TRIMMABLE_ARMOR_ITEMS);
        }
        return null;
    }

    private static Ingredient ingredientFromItemIds(List<String> itemIds) {
        return Ingredient.of(itemIds.stream()
                .map(Identifier::parse)
                .map(BuiltInRegistries.ITEM::getValue)
                .filter(item -> item != null));
    }

    private static RecipeMap getIntegratedServerRecipeMap() {
        try {
            Class<?> minecraftClass = Class.forName("net.minecraft.client.Minecraft");
            Method getInstance = minecraftClass.getMethod("getInstance");
            Object minecraft = getInstance.invoke(null);

            Method hasSingleplayerServer = minecraftClass.getMethod("hasSingleplayerServer");
            boolean singleplayer = (boolean) hasSingleplayerServer.invoke(minecraft);
            if (!singleplayer) {
                return null;
            }

            Method getSingleplayerServer = minecraftClass.getMethod("getSingleplayerServer");
            Object server = getSingleplayerServer.invoke(minecraft);
            if (server == null) {
                return null;
            }

            Method getRecipeManager = server.getClass().getMethod("getRecipeManager");
            Object recipeManager = getRecipeManager.invoke(server);
            Method getRecipes = recipeManager.getClass().getMethod("getRecipes");
            @SuppressWarnings("unchecked")
            Iterable<RecipeHolder<?>> recipes = (Iterable<RecipeHolder<?>>) getRecipes.invoke(recipeManager);

            List<RecipeHolder<?>> holders = new ArrayList<>();
            for (RecipeHolder<?> recipe : recipes) {
                holders.add(recipe);
            }

            JustEnoughServerlessRecipesLog.LOGGER.info("Using integrated server RecipeManager with {} recipes", holders.size());
            return holders.isEmpty() ? RecipeMap.EMPTY : RecipeMap.create(holders);
        } catch (ClassNotFoundException ignored) {
            return null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static boolean getBoolean(JsonObject json, String key, boolean fallback) {
        return json.has(key) ? json.get(key).getAsBoolean() : fallback;
    }

    private static void logTrimFailure(Identifier recipeId, String message, Exception e) {
        if (e != null) {
            JustEnoughServerlessRecipesLog.LOGGER.warn("Smithing trim fallback failed for {}: {}", recipeId, message, e);
        } else {
            JustEnoughServerlessRecipesLog.LOGGER.warn("Smithing trim fallback failed for {}: {}", recipeId, message);
        }
    }
}
