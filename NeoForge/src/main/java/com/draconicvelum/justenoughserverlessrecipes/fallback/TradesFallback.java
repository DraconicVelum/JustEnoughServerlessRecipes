package com.draconicvelum.justenoughserverlessrecipes.fallback;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import mezz.jei.api.runtime.IJeiRuntime;
import mezz.jei.api.recipe.types.IRecipeType;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.trading.TradeSet;
import net.minecraft.world.item.trading.TradeSets;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootTable;
import java.util.*;

final class TradesFallback {
    @SuppressWarnings("unchecked")
    static void apply(IJeiRuntime runtime, LocalDataLoader.Data localData) throws ReflectiveOperationException {
        var registries = localData.registries();
        var rebinder = new LocalDataRebinder(localData);
        var manager = runtime.getRecipeManager();
        Class<?> helper = Class.forName("de.larsensmods.jeitrades.jei.VillagerTradeTypeHelper");
        var type = (IRecipeType<Object>) helper.getField("RECIPE_TYPE").get(null);
        if (manager.createRecipeLookup(type).includeHidden().get().findAny().isEmpty()) {
            Class<?> transformed = Class.forName("de.larsensmods.jeitrades.data.transformed.TransformedTradeSet");
            var transform = transformed.getMethod("fromTradeSet", TradeSet.class);
            var sets = registries.lookupOrThrow(Registries.TRADE_SET);
            Map<Identifier, Object> trades = new HashMap<>();
            for (var holder : sets.listElements().toList()) {
                trades.put(holder.key().identifier(), transform.invoke(null, holder.value()));
            }
            Class<?> professionClass = Class.forName("de.larsensmods.jeitrades.data.VillagerTradeData$ProfessionData");
            var professionConstructor = professionClass.getConstructor(Identifier.class, ItemStackTemplate.class,
                    Component.class, it.unimi.dsi.fastutil.ints.Int2ObjectMap.class);
            Set<Object> professions = new HashSet<>();
            var pois = registries.lookupOrThrow(Registries.POINT_OF_INTEREST_TYPE).listElements().toList();
            for (var holder : registries.lookupOrThrow(Registries.VILLAGER_PROFESSION).listElements().toList()) {
                var profession = holder.value();
                if (profession.tradeSetsByLevel().isEmpty()) continue;
                var poi = pois.stream().filter(p -> profession.heldJobSite().test(p))
                        .filter(p -> !p.value().matchingStates().isEmpty()).findFirst();
                if (poi.isEmpty()) continue;
                var block = poi.get().value().matchingStates().iterator().next().getBlock().asItem().getDefaultInstance();
                if (block.isEmpty()) continue;
                var levels = new Int2ObjectOpenHashMap<Identifier>();
                profession.tradeSetsByLevel().forEach((level, key) -> levels.put(level, key.identifier()));
                professions.add(professionConstructor.newInstance(holder.key().identifier(),
                        ItemStackTemplate.fromNonEmptyStack(block), profession.name(), levels));
            }
            Class<?> dataClass = Class.forName("de.larsensmods.jeitrades.data.VillagerTradeData");
            Object data = dataClass.getConstructor(transformed, transformed, transformed, Map.class, Set.class).newInstance(
                    trades.get(TradeSets.WANDERING_TRADER_BUYING.identifier()),
                    trades.get(TradeSets.WANDERING_TRADER_COMMON.identifier()),
                    trades.get(TradeSets.WANDERING_TRADER_UNCOMMON.identifier()), trades, professions);
            var recipes = (List<Object>) helper.getMethod("buildRecipes", dataClass).invoke(null, rebinder.rebind(data));
            manager.addRecipes(type, recipes);
            AddonFallbacks.LOG.info("Added {} locally loaded JEI Trades recipes", recipes.size());
        }

        Class<?> barteringHelper = Class.forName("de.larsensmods.jeitrades.jei.BarteringTypeHelper");
        var barteringType = (IRecipeType<Object>) barteringHelper.getField("RECIPE_TYPE").get(null);
        if (manager.createRecipeLookup(barteringType).includeHidden().get().findAny().isEmpty()) {
            Class<?> mod = Class.forName("de.larsensmods.jeitrades.JEITradesMod");
            var build = mod.getDeclaredMethod("buildBarteringData", LootTable.class);
            build.setAccessible(true);
            var table = registries.lookupOrThrow(Registries.LOOT_TABLE).getOrThrow(BuiltInLootTables.PIGLIN_BARTERING).value();
            Object data = rebinder.rebind(build.invoke(null, table));
            Class<?> dataClass = Class.forName("de.larsensmods.jeitrades.data.BarteringData");
            var recipes = (List<Object>) barteringHelper.getMethod("buildRecipes", dataClass).invoke(null, data);
            manager.addRecipes(barteringType, recipes);
            AddonFallbacks.LOG.info("Added {} locally loaded piglin bartering recipes", recipes.size());
        }
    }
}
