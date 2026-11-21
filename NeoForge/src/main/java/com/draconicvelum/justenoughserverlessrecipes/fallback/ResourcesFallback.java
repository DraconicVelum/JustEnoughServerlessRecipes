package com.draconicvelum.justenoughserverlessrecipes.fallback;

import mezz.jei.api.recipe.types.IRecipeType;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.ReloadableServerRegistries;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.functions.SmeltItemFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceWithEnchantedBonusCondition;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

final class ResourcesFallback {
    @SuppressWarnings("unchecked")
    static void apply(IJeiRuntime runtime, HolderLookup.Provider registries) throws ReflectiveOperationException {
        Class<?> config = Class.forName("jeresources.jei.JEIConfig");
        var type = (IRecipeType<Object>) config.getField("MOB_TYPE").get(null);
        var originals = runtime.getRecipeManager().createRecipeLookup(type).get().toList();
        Class<?> entryClass = Class.forName("jeresources.entry.MobEntry");
        Class<?> helper = Class.forName("jeresources.util.LootTableHelper");
        var parse = helper.getMethod("toDrops", LootTable.class);
        var getDrops = entryClass.getMethod("getDrops");
        var getEntity = entryClass.getMethod("getEntity");
        var constructor = entryClass.getDeclaredConstructor(Supplier.class, Class.forName("jeresources.api.conditionals.LightLevel"),
                Class.forName("jeresources.compatibility.minecraft.ExperienceRange"), List.class, List.class);
        constructor.setAccessible(true);
        var fetcherField = helper.getDeclaredField("lootTableFetcher");
        fetcherField.setAccessible(true);
        Object previousFetcher = fetcherField.get(null);
        Class<?> fetcherClass = Class.forName("jeresources.util.LootTableFetcher");
        Object localFetcher = fetcherClass.getConstructor(ReloadableServerRegistries.Holder.class)
                .newInstance(new ReloadableServerRegistries.Holder(registries));
        List<Object> replaced = new ArrayList<>();
        List<Object> replacements = new ArrayList<>();
        try {
            // JER's parser also resolves nested tables through this fetcher. Restore it before returning.
            fetcherField.set(null, localFetcher);
            for (Object original : originals) {
                if (!((List<?>) getDrops.invoke(original)).isEmpty()) continue;
                var entity = (LivingEntity) getEntity.invoke(original);
                if (entity == null || entity.getType().getDefaultLootTable().isEmpty()) continue;
                var table = registries.lookupOrThrow(Registries.LOOT_TABLE)
                        .get(entity.getType().getDefaultLootTable().orElseThrow());
                if (table.isEmpty()) continue;
                var drops = repairDrops((List<?>) parse.invoke(null, table.get().value()), table.get().value());
                if (drops.isEmpty()) continue;
                Object replacement = constructor.newInstance(field(original, "entitySupplier"), field(original, "lightLevel"),
                        field(original, "experience"), field(original, "biomes"), drops);
                replaced.add(original);
                replacements.add(replacement);
            }
        } finally {
            fetcherField.set(null, previousFetcher);
        }
        if (!replacements.isEmpty()) {
            runtime.getRecipeManager().addRecipes(type, replacements);
            runtime.getRecipeManager().hideRecipes(type, replaced);
        }
        AddonFallbacks.LOG.info("Filled {} JER mob entries from local loot tables", replacements.size());
    }

    private static List<?> repairDrops(List<?> parsed, LootTable table) throws ReflectiveOperationException {
        var entries = new ArrayList<>(LootModifiers.directItems(table));
        List<Object> result = new ArrayList<>();
        for (Object original : parsed) {
            Class<?> dropClass = original.getClass();
            ItemStack item = (ItemStack) dropClass.getField("item").get(original);
            int match = -1;
            for (int i = 0; i < entries.size(); i++) if (entries.get(i).item() == item.getItem()) { match = i; break; }
            if (match < 0) { result.add(original); continue; }
            var entry = entries.remove(match);
            Object drop = dropClass.getConstructor(ItemStack.class).newInstance(new ItemStack(entry.item()));
            var chanceField = dropClass.getField("chance");
            chanceField.setFloat(drop, entry.weight());
            for (var function : entry.functions()) {
                dropClass.getMethod("addLootFunction", LootItemFunction.class).invoke(drop, function);
                if (function instanceof SmeltItemFunction) {
                    ItemStack raw = (ItemStack) dropClass.getField("item").get(drop);
                    for (var recipe : mezz.jei.common.Internal.getClientSyncedRecipes().byType(RecipeType.SMELTING)) {
                        if (!recipe.value().input().test(raw)) continue;
                        dropClass.getField("smeltedItem").set(drop, recipe.value().assemble(new SingleRecipeInput(raw)));
                        break;
                    }
                }
            }
            for (var condition : entry.conditions()) {
                float before = chanceField.getFloat(drop);
                dropClass.getMethod("addLootCondition", LootItemCondition.class).invoke(drop, condition);
                if (condition instanceof LootItemRandomChanceWithEnchantedBonusCondition enchanted) {
                    chanceField.setFloat(drop, before * enchanted.unenchantedChance());
                } else if (condition instanceof LootItemRandomChanceCondition) {
                    chanceField.setFloat(drop, before * chanceField.getFloat(drop));
                }
            }
            result.add(drop);
        }
        return result;
    }

    private static Object field(Object instance, String name) throws ReflectiveOperationException {
        var field = instance.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(instance);
    }
}
