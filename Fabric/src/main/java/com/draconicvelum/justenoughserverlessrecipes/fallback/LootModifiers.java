package com.draconicvelum.justenoughserverlessrecipes.fallback;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.entries.SingleEntryContainerBase;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.functions.SequenceFunction;
import net.minecraft.world.level.storage.loot.predicates.AllOfCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Expands the composite modifiers introduced by the modern loot format before handing them to JER. */
final class LootModifiers {
    record Entry(Item item, float weight, List<LootItemFunction> functions, List<LootItemCondition> conditions) {}

    @SuppressWarnings("unchecked")
    static List<Entry> directItems(LootTable table) throws ReflectiveOperationException {
        List<Entry> result = new ArrayList<>();
        for (Object pool : (List<?>) field(table, "pools")) {
            var entries = (List<?>) field(pool, "entries");
            int totalWeight = 0;
            for (Object entry : entries) if (entry instanceof SingleEntryContainerBase) totalWeight += (int) field(entry, "weight");
            for (Object entry : entries) {
                if (!(entry instanceof LootItem)) continue;
                List<LootItemFunction> functions = new ArrayList<>();
                appendFunctions(functions, field(entry, "modifier"));
                appendFunctions(functions, field(pool, "modifier"));
                appendFunctions(functions, field(table, "modifier"));
                List<LootItemCondition> conditions = new ArrayList<>();
                appendConditions(conditions, field(entry, "condition"));
                appendConditions(conditions, field(pool, "condition"));
                result.add(new Entry(((Holder<Item>) field(entry, "item")).value(),
                        totalWeight == 0 ? 0 : (float) (int) field(entry, "weight") / totalWeight,
                        List.copyOf(functions), List.copyOf(conditions)));
            }
        }
        return result;
    }

    private static void appendFunctions(List<LootItemFunction> result, Object value) throws ReflectiveOperationException {
        if (value instanceof Optional<?> optional) {
            if (optional.isPresent()) appendFunctions(result, optional.get());
        } else if (value instanceof Holder<?> holder) {
            appendFunctions(result, holder.value());
        } else if (value instanceof SequenceFunction sequence) {
            for (var child : (HolderSet<?>) field(sequence, "functions")) appendFunctions(result, child);
        } else if (value instanceof LootItemFunction function) result.add(function);
    }

    private static void appendConditions(List<LootItemCondition> result, Object value) throws ReflectiveOperationException {
        if (value instanceof Optional<?> optional) {
            if (optional.isPresent()) appendConditions(result, optional.get());
        } else if (value instanceof Holder<?> holder) {
            appendConditions(result, holder.value());
        } else if (value instanceof AllOfCondition all) {
            for (var child : (HolderSet<?>) field(all, "terms")) appendConditions(result, child);
        } else if (value instanceof LootItemCondition condition) result.add(condition);
    }

    static Object field(Object object, String name) throws ReflectiveOperationException {
        for (Class<?> type = object.getClass(); type != null; type = type.getSuperclass()) {
            try {
                var field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(object);
            } catch (NoSuchFieldException ignored) {}
        }
        throw new NoSuchFieldException(name);
    }
}
