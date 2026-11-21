package com.draconicvelum.justenoughserverlessrecipes.fallback;

import com.mojang.serialization.JsonOps;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.Ingredient;
import java.util.*;

/** Keeps local tag expansion but publishes ingredients using the live client's item holders. */
final class LocalDataRebinder {
    private final LocalDataLoader.Data data;
    LocalDataRebinder(LocalDataLoader.Data data) { this.data = data; }

    @SuppressWarnings({"unchecked", "rawtypes"})
    Object rebind(Object value) throws ReflectiveOperationException {
        if (value == null) return null;
        if (value instanceof Ingredient ingredient) {
            return Ingredient.of(HolderSet.direct(ingredient.items()
                    .map(holder -> BuiltInRegistries.ITEM.wrapAsHolder(holder.value())).toList()));
        }
        if (value instanceof ItemStackTemplate template) {
            if (template.components().isEmpty() && template.item().unwrapKey().isPresent()
                    && data.publishingRegistries().lookupOrThrow(net.minecraft.core.registries.Registries.ITEM)
                    .getOrThrow(template.item().unwrapKey().orElseThrow()) == template.item()) return template;
            var json = ItemStackTemplate.CODEC.encodeStart(RegistryOps.create(JsonOps.INSTANCE, data.registries()), template).getOrThrow();
            return ItemStackTemplate.CODEC.parse(RegistryOps.create(JsonOps.INSTANCE, data.publishingRegistries()), json).getOrThrow();
        }
        if (value instanceof Holder<?> holder && holder.unwrapKey().isPresent()) {
            var key = holder.unwrapKey().orElseThrow();
            return data.publishingRegistries().lookupOrThrow(key.registryKey()).getOrThrow((net.minecraft.resources.ResourceKey) key);
        }
        if (value instanceof Optional<?> optional) {
            return optional.isPresent() ? Optional.of(rebind(optional.get())) : Optional.empty();
        }
        if (value instanceof Set<?> set) {
            Set<Object> result = new LinkedHashSet<>();
            for (Object element : set) result.add(rebind(element));
            return result;
        }
        if (value instanceof Map<?, ?> map && !(value instanceof it.unimi.dsi.fastutil.ints.Int2ObjectMap<?>)) {
            Map<Object, Object> result = new LinkedHashMap<>();
            for (var entry : map.entrySet()) result.put(entry.getKey(), rebind(entry.getValue()));
            return result;
        }
        if (value.getClass().isRecord() && (value.getClass().getName().startsWith("de.larsensmods.jeitrades.")
                || value.getClass().getName().startsWith("com.christofmeg.justenoughbreeding."))) {
            var components = value.getClass().getRecordComponents();
            Class<?>[] types = new Class<?>[components.length];
            Object[] arguments = new Object[components.length];
            for (int i = 0; i < components.length; i++) {
                types[i] = components[i].getType();
                arguments[i] = rebind(components[i].getAccessor().invoke(value));
            }
            return value.getClass().getConstructor(types).newInstance(arguments);
        }
        return value;
    }
}
