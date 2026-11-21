package com.draconicvelum.justenoughserverlessrecipes.fallback;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.server.packs.*;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.tags.TagLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/** Reads local data without creating a server, touching addon caches, or replacing live registry tags. */
final class LocalDataLoader {
    static List<PackResources> openPacks(List<Path> roots) {
        List<PackResources> packs = new ArrayList<>();
        packs.add(ServerPacksSource.createVanillaPackSource().fullResources());
        for (Path root : roots) {
            if (Files.isDirectory(root.resolve("data"))) {
                var info = new PackLocationInfo("jesr-local-" + packs.size(), Component.literal("Local mod data"),
                        PackSource.BUILT_IN, java.util.Optional.empty());
                packs.add(new PathPackResources(info, root));
            }
        }
        return packs;
    }

    record Data(HolderLookup.Provider registries, HolderLookup.Provider publishingRegistries,
                MultiPackResourceManager resources) implements AutoCloseable {
        @Override public void close() { resources.close(); }
    }

    static Data load(RegistryAccess base, List<PackResources> packs) {
        var resources = new MultiPackResourceManager(PackType.SERVER_DATA, packs);
        try {
            var frozenBase = new RegistryAccess.ImmutableRegistryAccess(base.registries()
                    .map(entry -> copyRegistry(entry.value())).toList()).freeze();
            var baseTags = TagLoader.loadTagsForExistingRegistries(resources, frozenBase);
            baseTags.forEach(Registry.PendingTags::apply);
            var baseLookups = TagLoader.buildUpdatedLookups(frozenBase, baseTags);
            var world = RegistryDataLoader.load(resources, baseLookups, RegistryDataLoader.WORLD_REGISTRIES, Runnable::run).join();
            var worldTags = TagLoader.loadTagsForExistingRegistries(resources, world);
            var worldLookups = TagLoader.buildUpdatedLookups(world, worldTags);
            var lookups = new LinkedHashMap<Object, HolderLookup.RegistryLookup<?>>();
            baseLookups.forEach(lookup -> lookups.put(lookup.key(), lookup));
            worldLookups.forEach(lookup -> lookups.put(lookup.key(), lookup));
            var reloadable = RegistryDataLoader.load(resources, List.copyOf(lookups.values()),
                    RegistryDataLoader.RELOADABLE_REGISTRIES.stream()
                            .filter(data -> java.util.Set.of(Registries.LOOT_TABLE, Registries.ITEM_MODIFIER,
                                    Registries.PREDICATE, Registries.TRADE_SET, Registries.VILLAGER_TRADE)
                                    .contains(data.key())).toList(), Runnable::run).join();
            // Both the base copies and world registries belong only to this fallback.
            worldTags.forEach(Registry.PendingTags::apply);
            reloadable.listRegistries().forEach(lookup -> lookups.put(lookup.key(), lookup));
            var local = HolderLookup.Provider.create(lookups.values().stream());
            base.listRegistries().forEach(lookup -> lookups.put(lookup.key(), lookup));
            return new Data(local, HolderLookup.Provider.create(lookups.values().stream()), resources);
        } catch (RuntimeException | Error e) {
            resources.close();
            throw e;
        }
    }

    private static <T> Registry<T> copyRegistry(Registry<T> original) {
        var copy = new MappedRegistry<T>(original.key(), original.registryLifecycle());
        original.listElements().forEach(holder -> {
            var local = copy.register(holder.key(), holder.value(),
                    original.registrationInfo(holder.key()).orElse(RegistrationInfo.BUILT_IN));
            if (holder.areComponentsBound()) local.bindComponents(holder.components());
        });
        return copy.freeze();
    }
}
